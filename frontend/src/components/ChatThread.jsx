import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import { describeError } from '../api/client';
import {
  historia, nowsze, oznaczPrzeczytane, pisze, usunWiadomosc, wyslij, wyslijSpotkanie,
} from '../api/czat';
import { useChat } from '../chat/ChatContext';
import useGify from '../hooks/useGify';
import Avatar from './Avatar';
import GifObrazek from './gif/GifObrazek';
import GifPicker from './gif/GifPicker';
import MusicCard from './MusicCard';
import MusicPicker from './MusicPicker';
import KartaSpotkania from './spotkanie/KartaSpotkania';
import SpotkanieForm from './spotkanie/SpotkanieForm';
import TekstWiadomosci from './TekstWiadomosci';
import {
  IconCross, IconGif, IconNote, IconPin, IconSend, IconTrash,
} from './Icons';
import { timeAgo } from '../utils/dates';
import { linkError } from '../utils/musicLinks';
import { toSeconds } from '../utils/time';
import useOdswiezanie from '../hooks/useOdswiezanie';

/** Co ile pytamy o nowosci przy otwartej rozmowie. */
const SYNC_MS = 3_000;

/** Jak czesto najwyzej wysylamy sygnal "pisze". */
const TYPING_PING_MS = 2_000;

/** Ile wiadomosci na jedna strone historii. */
const PAGE_SIZE = 25;

/** Ile pikseli od dolu uznajemy jeszcze za "czyta koniec rozmowy". */
const STICK_TO_BOTTOM_PX = 80;

/** Jedna rozmowa: historia, pisanie i wysylanie. */
export default function ChatThread({ username, avatarUrl, friend = true, onPresence, onRead }) {
  const { t, i18n } = useTranslation();
  const { setUnread } = useChat();
  const gify = useGify();

  /** Wiadomosci ROSNACO - tak jak leza na ekranie. */
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [hasOlder, setHasOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);

  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});

  const [musicOpen, setMusicOpen] = useState(false);
  const [musicUrl, setMusicUrl] = useState('');
  const [musicKind, setMusicKind] = useState('TRACK');
  const [startAt, setStartAt] = useState('');

  /* GIF wybrany z przegladarki (caly wynik z tokenem) i czy przegladarka jest otwarta */
  const [gif, setGif] = useState(null);
  const [gifOpen, setGifOpen] = useState(false);
  const [meetingOpen, setMeetingOpen] = useState(false);

  const [partnerTyping, setPartnerTyping] = useState(false);

  /* Czy z ta osoba wolno teraz PISAC. */
  const [canWrite, setCanWrite] = useState(friend);

  const scroller = useRef(null);
  const input = useRef(null);
  const lastTypingPing = useRef(0);

  /*
   * Funkcje od rodzica trzymamy w UCHWYTACH, a nie bierzemy wprost do listy zaleznosci efektow.
   */
  const callbacks = useRef({ onPresence, onRead });
  useEffect(() => {
    callbacks.current = { onPresence, onRead };
  }, [onPresence, onRead]);
  /* Ostatni znany identyfikator trzymamy TAKZE w ref, a nie tylko w stanie. */
  const lastId = useRef(null);
  /* Czas serwera z ostatniego odpytania - od niego serwer liczy usuniete wiadomosci. */
  const czasSerwera = useRef(null);

  /* ---------------------------------------------------------------- */
  /*  Historia                                                         */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setMessages([]);
    lastId.current = null;
    czasSerwera.current = null;

    historia(username, 0, PAGE_SIZE)
      .then((data) => {
        if (cancelled) return;

        /*
         * Serwer oddaje OD NAJNOWSZEJ (czat otwiera sie na koncu rozmowy), a na ekranie kolejnosc
         * jest odwrotna.
         */
        const ordered = [...data.content].reverse();
        setMessages(ordered);
        setHasOlder(data.totalPages > 1);
        lastId.current = ordered.length ? ordered[ordered.length - 1].id : null;
      })
      .catch(() => {
        if (!cancelled) setError(t('chat.loadFailed'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    // Otwarcie rozmowy kasuje kropki przy tej osobie
    oznaczPrzeczytane(username)
      .then(() => callbacks.current.onRead?.(username))
      .catch(() => {});

    return () => { cancelled = true; };
  }, [username, t]);

  async function loadOlder() {
    setLoadingOlder(true);
    try {
      const data = await historia(
        username, Math.floor(messages.length / PAGE_SIZE), PAGE_SIZE);

      const older = [...data.content].reverse();
      /* Dokladamy NA POCZATEK i odsiewamy powtorki po identyfikatorze. */
      setMessages((current) => {
        const known = new Set(current.map((m) => m.id));
        return [...older.filter((m) => !known.has(m.id)), ...current];
      });
      setHasOlder(data.number + 1 < data.totalPages);
    } catch {
      setError(t('chat.loadFailed'));
    } finally {
      setLoadingOlder(false);
    }
  }

  /* ---------------------------------------------------------------- */
  /*  Odpytywanie o nowosci                                            */
  /* ---------------------------------------------------------------- */

  /** Nowy stan spotkania w wiadomosci, ktora je niesie (moja odpowiedz albo zmiana z odpytywania). */
  const podmienSpotkanie = useCallback((s) => {
    setMessages((current) => (current.some((m) => m.meeting?.id === s.id)
      ? current.map((m) => (m.meeting?.id === s.id ? { ...m, meeting: s } : m))
      : current));
  }, []);

  const sync = useCallback(async () => {
    try {
      const data = await nowsze(username, lastId.current, czasSerwera.current);
      czasSerwera.current = data.serverTime ?? czasSerwera.current;

      // Spotkania z nowymi odpowiedziami albo odwolane - nowy stan karty
      if (data.meetings?.length) {
        data.meetings.forEach(podmienSpotkanie);
      }

      // Usuniete u drugiej strony (albo w innej karcie) - zostaje slad
      if (data.deletedIds?.length) {
        const usuniete = new Set(data.deletedIds);
        setMessages((current) => (current.some((m) => usuniete.has(m.id) && !m.deleted)
          ? current.map((m) => (usuniete.has(m.id) ? jakoUsunieta(m) : m))
          : current));
      }

      setPartnerTyping(data.partnerTyping);
      setCanWrite(data.friend);
      callbacks.current.onPresence?.(username, data.presence);
      setUnread(data.unread);

      /* Ptaszek "przeczytane". */
      const readUpTo = data.lastReadOutgoingId;
      if (readUpTo != null) {
        setMessages((current) => (
          current.some((m) => m.mine && !m.read && m.id <= readUpTo)
            ? current.map((m) => (
              m.mine && m.id <= readUpTo ? { ...m, read: true } : m))
            // Bez tego warunku kazde odpytanie tworzyloby nowa tablice,
            // a React przerysowywalby cala rozmowe co trzy sekundy
            : current));
      }

      if (data.messages.length === 0) {
        return;
      }

      setMessages((current) => {
        const known = new Set(current.map((m) => m.id));
        const fresh = data.messages.filter((m) => !known.has(m.id));
        return fresh.length ? [...current, ...fresh] : current;
      });

      lastId.current = data.messages[data.messages.length - 1].id;
    } catch {
      /* Cisza. */
    }
  }, [username, setUnread, podmienSpotkanie]);

  useEffect(() => {
    sync();
  }, [sync]);

  useOdswiezanie(sync, SYNC_MS);

  /* ---------------------------------------------------------------- */
  /*  Przewijanie                                                      */
  /* ---------------------------------------------------------------- */

  /*
   * Zjezdzamy na dol po nowych wiadomosciach - ale TYLKO wtedy, gdy uzytkownik juz jest na dole.
   */
  useEffect(() => {
    const box = scroller.current;
    if (!box) return;

    const atBottom =
      box.scrollHeight - box.scrollTop - box.clientHeight < STICK_TO_BOTTOM_PX;

    if (atBottom || messages.length <= PAGE_SIZE) {
      box.scrollTop = box.scrollHeight;
    }
  }, [messages, partnerTyping]);

  useEffect(() => {
    input.current?.focus();
  }, [username]);

  /* ---------------------------------------------------------------- */
  /*  Pisanie i wysylanie                                              */
  /* ---------------------------------------------------------------- */

  function handleTyping(value) {
    setText(value);

    /* Sygnal "pisze" wysylamy najwyzej raz na dwie sekundy. */
    const now = Date.now();
    if (value && now - lastTypingPing.current > TYPING_PING_MS) {
      lastTypingPing.current = now;
      pisze(username).catch(() => {});
    }
  }

  const musicProblem = musicOpen ? linkError(musicUrl, musicKind) : null;
  const nothingToSend = !text.trim() && !musicUrl.trim() && !gif;

  async function send(event) {
    event.preventDefault();
    if (sending || nothingToSend || musicProblem) {
      return;
    }

    setSending(true);
    setError(null);
    setFieldErrors({});

    try {
      const data = await wyslij(username, {
        content: text.trim() || null,
        musicUrl: musicUrl.trim() || null,
        musicKind: musicUrl.trim() ? musicKind : null,
        musicStartSeconds: musicKind === 'TRACK' ? toSeconds(startAt) : null,
        gif: gif?.token ?? null,
      });

      /* Dopisujemy odpowiedz serwera, a nie to, co wpisal uzytkownik. */
      setMessages((current) => [...current, data]);
      lastId.current = data.id;

      setText('');
      setMusicUrl('');
      setStartAt('');
      setMusicOpen(false);
      setGif(null);
      setGifOpen(false);
      input.current?.focus();
    } catch (problem) {
      const body = problem.response?.data;
      setError(body?.message ?? t('chat.sendFailed'));

      if (Array.isArray(body?.errors)) {
        setFieldErrors(Object.fromEntries(
          body.errors.map((e) => [e.field, e.message])));
      }
    } finally {
      setSending(false);
    }
  }

  async function wyslijSpotkanieDo(dane) {
    try {
      const data = await wyslijSpotkanie(username, dane);
      setMessages((current) => [...current, data]);
      lastId.current = data.id;
      setMeetingOpen(false);
    } catch (problem) {
      throw new Error(describeError(problem).message);
    }
  }

  async function usun(message) {
    if (!window.confirm(t('chat.deleteMessageConfirm'))) {
      return;
    }
    setError(null);
    try {
      const data = await usunWiadomosc(message.id);
      setMessages((current) => current.map((m) => (m.id === message.id ? { ...m, ...data } : m)));
    } catch {
      setError(t('chat.deleteMessageFailed'));
    }
  }

  /** Enter wysyla, Shift+Enter przechodzi do nowej linii. */
  function onKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      send(event);
    }
  }

  return (
    <div className="chat-thread">
      <div className="chat-messages" ref={scroller}>
        {loading && (
          <div className="chat-loading">
            {[0, 1, 2].map((i) => (
              <span key={i} className={`bubble-skeleton${i % 2 ? ' is-mine' : ''}`} />
            ))}
          </div>
        )}

        {!loading && messages.length === 0 && (
          <p className="chat-empty">{t('chat.emptyThread', { username })}</p>
        )}

        {hasOlder && !loading && (
          <div className="text-center mb-2">
            <Button
              variant="outline-secondary"
              size="sm"
              onClick={loadOlder}
              disabled={loadingOlder}
            >
              {loadingOlder ? t('common.loading') : t('chat.olderMessages')}
            </Button>
          </div>
        )}

        {messages.map((message, index) => (
          <div
            key={message.id}
            className={`bubble-row${message.mine ? ' is-mine' : ''}`}
          >
            {/* Awatar tylko przy PIERWSZEJ wiadomosci z serii tej samej osoby. */}
            {!message.mine && (
              <span className="bubble-avatar">
                {startsRun(messages, index) && (
                  <Avatar avatarUrl={avatarUrl} username={username} size={26} />
                )}
              </span>
            )}

            <div className={`bubble${message.deleted ? ' is-usunieta' : ''}`}>
              {message.deleted ? (
                <p className="bubble-usunieta">{t('chat.messageDeleted')}</p>
              ) : (
                <>
                  <TekstWiadomosci tekst={message.content} ukryjSamLink={Boolean(message.musicEmbedUrl)} />
                  <GifObrazek gif={message.gif} />
                  {message.musicEmbedUrl && <MusicCard message={message} />}
                  {message.meeting && <KartaSpotkania spotkanie={message.meeting} onZmiana={podmienSpotkanie} />}
                </>
              )}

              <span className="bubble-time">
                {timeAgo(message.createdAt, i18n.language)}
                {/* Ptaszek widzi tylko nadawca - odbiorcy nic by nie mowil */}
                {message.mine && message.read && !message.deleted && ' · ✓✓'}
                {message.mine && !message.deleted && (
                  <button type="button" className="klan-akcja czat-usun" onClick={() => usun(message)}
                    aria-label={t('chat.deleteMessage')} title={t('chat.deleteMessage')}>
                    <IconTrash size={11} />
                  </button>
                )}
              </span>
            </div>
          </div>
        ))}

        {partnerTyping && (
          <div className="bubble-row">
            {/*
              Awatar przy dymku "pisze" jest ZAWSZE, a nie tylko przy pierwszej wiadomosci z
              serii.
            */}
            <span className="bubble-avatar">
              <Avatar avatarUrl={avatarUrl} username={username} size={26} />
            </span>
            <div className="bubble typing-bubble" aria-label={t('chat.typing', { username })}>
              <span className="typing-dots" aria-hidden="true">
                <i /><i /><i />
              </span>
            </div>
          </div>
        )}
      </div>

      {/* Po zerwaniu znajomosci rozmowa ZOSTAJE do przeczytania, ale pole do pisania znika. */}
      {!canWrite ? (
        <div className="chat-composer chat-closed">
          <p className="mb-0 small text-body-secondary">{t('chat.friendsOnly')}</p>
        </div>
      ) : (
      <>
      {meetingOpen && (
        <div className="chat-spotkanie-panel">
          <SpotkanieForm idPrefix="czat-spotkanie" onWyslij={wyslijSpotkanieDo} onZamknij={() => setMeetingOpen(false)} />
        </div>
      )}
      <form className="chat-composer" onSubmit={send}>
        {error && <div className="chat-error">{error}</div>}

        {musicOpen && (
          <div className="chat-music-picker">
            <MusicPicker
              kind={musicKind}
              onKind={setMusicKind}
              link={musicUrl}
              onLink={setMusicUrl}
              startSeconds={startAt}
              onStartSeconds={setStartAt}
              serverErrors={fieldErrors}
              idPrefix="chat-"
            />
          </div>
        )}

        {gif && (
          <div className="gif-wybrany chat-gif-wybrany">
            <img src={gif.previewUrl} alt={gif.title || t('gifs.selected')} referrerPolicy="no-referrer" />
            <button type="button" onClick={() => setGif(null)} aria-label={t('gifs.remove')} title={t('gifs.remove')}>
              <IconCross size={12} />
            </button>
          </div>
        )}

        {gifOpen && (
          <GifPicker
            idPrefix="chat-gif"
            podpis={gify.podpis}
            onWybierz={(wybrany) => { setGif(wybrany); setGifOpen(false); input.current?.focus(); }}
            onZamknij={() => setGifOpen(false)}
          />
        )}

        <div className="chat-composer-row">
          <button
            type="button"
            className={`chat-music-toggle${musicOpen ? ' is-open' : ''}`}
            onClick={() => { setMusicOpen((was) => !was); setGifOpen(false); setMeetingOpen(false); }}
            aria-pressed={musicOpen}
            aria-label={t('chat.attachMusic')}
            title={t('chat.attachMusic')}
          >
            <IconNote size={16} />
          </button>

          {gify.wlaczone && (
            <button
              type="button"
              className={`chat-music-toggle chat-gif-toggle${gifOpen ? ' is-open' : ''}`}
              onClick={() => { setGifOpen((was) => !was); setMusicOpen(false); setMeetingOpen(false); }}
              aria-pressed={gifOpen}
              aria-label={t('chat.attachGif')}
              title={t('chat.attachGif')}
            >
              <IconGif size={18} />
            </button>
          )}

          <button
            type="button"
            className={`chat-music-toggle chat-spotkanie-toggle${meetingOpen ? ' is-open' : ''}`}
            onClick={() => { setMeetingOpen((was) => !was); setMusicOpen(false); setGifOpen(false); }}
            aria-pressed={meetingOpen}
            aria-label={t('meetings.attach')}
            title={t('meetings.attach')}
          >
            <IconPin size={16} />
          </button>

          <textarea
            ref={input}
            className="chat-input form-control"
            rows={1}
            value={text}
            onChange={(e) => handleTyping(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder={t('chat.placeholder', { username })}
            maxLength={2000}
            aria-label={t('chat.placeholder', { username })}
          />

          <button
            type="submit"
            className="chat-send"
            disabled={sending || nothingToSend || Boolean(musicProblem)}
            aria-label={t('chat.send')}
            title={t('chat.send')}
          >
            <IconSend size={16} />
          </button>
        </div>
      </form>
      </>
      )}
    </div>
  );
}

/** Slad po usunietej wiadomosci - to samo, co oddaje serwer (bez tresci i zalacznikow). */
function jakoUsunieta(m) {
  return {
    ...m, deleted: true, content: null, gif: null, musicEmbedUrl: null, musicTitle: null, musicThumbnailUrl: null,
    meeting: null,
  };
}

/** Czy ta wiadomosc zaczyna nowa serie od tej samej osoby. */
function startsRun(messages, index) {
  return index === 0 || messages[index - 1].mine !== messages[index].mine;
}
