import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import client from '../api/client';
import { useChat } from '../chat/ChatContext';
import Avatar from './Avatar';
import MusicCard from './MusicCard';
import MusicPicker from './MusicPicker';
import { IconNote, IconSend } from './Icons';
import { timeAgo } from '../utils/dates';
import { linkError } from '../utils/musicLinks';
import { toSeconds } from '../utils/time';

/** Co ile pytamy o nowosci przy otwartej rozmowie. */
const SYNC_MS = 3_000;

/** Jak czesto najwyzej wysylamy sygnal "pisze". */
const TYPING_PING_MS = 2_000;

/** Ile wiadomosci na jedna strone historii. */
const PAGE_SIZE = 25;

/** Ile pikseli od dolu uznajemy jeszcze za "czyta koniec rozmowy". */
const STICK_TO_BOTTOM_PX = 80;

/**
 * Jedna rozmowa: historia, pisanie i wysylanie.
 *
 * <p><b>Odpytywanie zamiast polaczenia na zywo.</b> Prawdziwy czat "na zywo"
 * wymaga WebSocketa, a to znaczy druga sciezka uwierzytelniania obok sesji,
 * wlasny stan polaczen na serwerze i obsluga zrywania sieci tutaj. Przy
 * rozmowie dwoch osob roznica miedzy "natychmiast" a "w ciagu trzech sekund"
 * jest niezauwazalna. Odpytywanie chodzi TYLKO przy otwartej rozmowie
 * i pyta o same nowosci - patrz {@code ConversationSyncResponse}.</p>
 */
export default function ChatThread({ username, avatarUrl, onPresence, onRead }) {
  const { t, i18n } = useTranslation();
  const { setUnread } = useChat();

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

  const [partnerTyping, setPartnerTyping] = useState(false);

  const scroller = useRef(null);
  const input = useRef(null);
  const lastTypingPing = useRef(0);

  /*
   * Funkcje od rodzica trzymamy w UCHWYTACH, a nie bierzemy wprost do listy
   * zaleznosci efektow.
   *
   * Powod jest konkretny i kosztowal jeden prawdziwy blad. Rodzic tworzy je
   * na nowo przy kazdym rysowaniu, wiec dla Reacta za kazdym razem sa to
   * INNE funkcje. Efekt wczytujacy historie mial je w zaleznosciach - i przez
   * to uruchamial sie w kolko, za kazdym razem czyszczac liste wiadomosci.
   * Objaw: wyslana wiadomosc znikala w tej samej chwili, w ktorej sie
   * pojawiala. Uchwyt zawsze wskazuje najswiezsza wersje, ale sam sie nie
   * zmienia - wiec efekt zalezy juz tylko od tego, od czego naprawde zalezy.
   */
  const callbacks = useRef({ onPresence, onRead });
  useEffect(() => {
    callbacks.current = { onPresence, onRead };
  }, [onPresence, onRead]);
  /*
   * Ostatni znany identyfikator trzymamy TAKZE w ref, a nie tylko w stanie.
   * Odpytywanie zyje w interwale zalozonym raz - gdyby czytalo stan, widzialoby
   * na zawsze wartosc z chwili zalozenia (domkniecie nad stara zmienna).
   */
  const lastId = useRef(null);

  /* ---------------------------------------------------------------- */
  /*  Historia                                                         */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setMessages([]);
    lastId.current = null;

    client.get(`/messages/with/${encodeURIComponent(username)}`, {
      params: { page: 0, size: PAGE_SIZE },
    })
      .then(({ data }) => {
        if (cancelled) return;

        /*
         * Serwer oddaje OD NAJNOWSZEJ (czat otwiera sie na koncu rozmowy),
         * a na ekranie kolejnosc jest odwrotna. Odwracamy tutaj, w jednym
         * miejscu - dalej caly komponent pracuje juz na kolejnosci ekranu.
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
    client.post(`/messages/with/${encodeURIComponent(username)}/read`)
      .then(() => callbacks.current.onRead?.(username))
      .catch(() => {});

    return () => { cancelled = true; };
  }, [username, t]);

  async function loadOlder() {
    setLoadingOlder(true);
    try {
      const { data } = await client.get(`/messages/with/${encodeURIComponent(username)}`, {
        params: { page: Math.floor(messages.length / PAGE_SIZE), size: PAGE_SIZE },
      });

      const older = [...data.content].reverse();
      /*
       * Dokladamy NA POCZATEK i odsiewamy powtorki po identyfikatorze.
       * Powtorka jest tu realna: jesli w czasie czytania przyszla nowa
       * wiadomosc, strony przesuwaja sie o jeden i ostatnia pozycja
       * poprzedniej strony wraca w nastepnej.
       */
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

  const sync = useCallback(async () => {
    try {
      const { data } = await client.get(
        `/messages/with/${encodeURIComponent(username)}/sync`,
        { params: lastId.current ? { after: lastId.current } : {} });

      setPartnerTyping(data.partnerTyping);
      callbacks.current.onPresence?.(username, data.presence);
      setUnread(data.unread);

      /*
       * Ptaszek "przeczytane". Przeczytanie NIE tworzy nowej wiadomosci -
       * zmienia jedna kolumne w starej, ktora dawno zostala wyslana. Same
       * "nowsze wiadomosci" nigdy by wiec o tym nie powiedzialy i ptaszek
       * nie pojawialby sie bez odswiezenia calej strony.
       *
       * Serwer podaje NAJWYZSZY przeczytany numer, a nie liste - wiadomosci
       * czyta sie po kolei, wiec wszystko do tego numeru jest przeczytane.
       */
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
      /*
       * Cisza. Odpytywanie chodzi co trzy sekundy - jedna nieudana proba
       * (uspiony laptop, chwilowa utrata sieci) nie moze zasypac panelu
       * komunikatami o bledzie. Nastepna proba za chwile.
       */
    }
  }, [username, setUnread]);

  useEffect(() => {
    sync();
    const timer = setInterval(sync, SYNC_MS);
    return () => clearInterval(timer);
  }, [sync]);

  /* ---------------------------------------------------------------- */
  /*  Przewijanie                                                      */
  /* ---------------------------------------------------------------- */

  /*
   * Zjezdzamy na dol po nowych wiadomosciach - ale TYLKO wtedy, gdy
   * uzytkownik juz jest na dole. Bez tego warunku odczytywanie starszej
   * czesci rozmowy bylo by przerywane skokiem w dol za kazdym razem,
   * gdy druga strona cos napisze.
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

    /*
     * Sygnal "pisze" wysylamy najwyzej raz na dwie sekundy. Bez tego kazde
     * uderzenie w klawisz bylo by osobnym zapytaniem do serwera - przy
     * normalnym tempie pisania kilkanascie na sekunde.
     */
    const now = Date.now();
    if (value && now - lastTypingPing.current > TYPING_PING_MS) {
      lastTypingPing.current = now;
      client.post(`/messages/with/${encodeURIComponent(username)}/typing`).catch(() => {});
    }
  }

  const musicProblem = musicOpen ? linkError(musicUrl, musicKind) : null;
  const nothingToSend = !text.trim() && !musicUrl.trim();

  async function send(event) {
    event.preventDefault();
    if (sending || nothingToSend || musicProblem) {
      return;
    }

    setSending(true);
    setError(null);
    setFieldErrors({});

    try {
      const { data } = await client.post(
        `/messages/with/${encodeURIComponent(username)}`,
        {
          content: text.trim() || null,
          musicUrl: musicUrl.trim() || null,
          musicKind: musicUrl.trim() ? musicKind : null,
          musicStartSeconds: musicKind === 'TRACK' ? toSeconds(startAt) : null,
        });

      /*
       * Dopisujemy odpowiedz serwera, a nie to, co wpisal uzytkownik.
       * Tylko serwer zna identyfikator, dokladny czas i rozpoznane dane
       * nagrania - a bez identyfikatora odpytywanie przyslaloby te sama
       * wiadomosc jeszcze raz, jako "nowa".
       */
      setMessages((current) => [...current, data]);
      lastId.current = data.id;

      setText('');
      setMusicUrl('');
      setStartAt('');
      setMusicOpen(false);
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

  /**
   * Enter wysyla, Shift+Enter przechodzi do nowej linii.
   *
   * <p>To jest zachowanie, ktorego ludzie oczekuja po kazdym komunikatorze.
   * Zwykle pole tekstowe wstawialoby tu nowa linie, a wiadomosc czekalaby
   * na klikniecie w przycisk.</p>
   */
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
            {/*
              Awatar tylko przy PIERWSZEJ wiadomosci z serii tej samej osoby.
              Powtarzanie go przy kazdym zdaniu robi z rozmowy kolumne
              zdjec, w ktorej trudno znalezc tresc.
            */}
            {!message.mine && (
              <span className="bubble-avatar">
                {startsRun(messages, index) && (
                  <Avatar avatarUrl={avatarUrl} username={username} size={26} />
                )}
              </span>
            )}

            <div className="bubble">
              {message.content && <p className="bubble-text">{message.content}</p>}
              {message.musicEmbedUrl && <MusicCard message={message} />}

              <span className="bubble-time">
                {timeAgo(message.createdAt, i18n.language)}
                {/* Ptaszek widzi tylko nadawca - odbiorcy nic by nie mowil */}
                {message.mine && message.read && ' · ✓✓'}
              </span>
            </div>
          </div>
        ))}

        {partnerTyping && (
          <div className="bubble-row">
            {/*
              Awatar przy dymku "pisze" jest ZAWSZE, a nie tylko przy pierwszej
              wiadomosci z serii. Ten dymek nie należy do żadnej serii - stoi
              pod koniec rozmowy jako osobne zdarzenie i bez zdjęcia wyglądał
              jak trzy kropki, które wzięły się znikąd.
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

        <div className="chat-composer-row">
          <button
            type="button"
            className={`chat-music-toggle${musicOpen ? ' is-open' : ''}`}
            onClick={() => setMusicOpen((was) => !was)}
            aria-pressed={musicOpen}
            aria-label={t('chat.attachMusic')}
            title={t('chat.attachMusic')}
          >
            <IconNote size={16} />
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
    </div>
  );
}

/**
 * Czy ta wiadomosc zaczyna nowa serie od tej samej osoby.
 *
 * <p>Po tym poznajemy, gdzie postawic awatar. Pierwsza wiadomosc w rozmowie
 * zawsze zaczyna serie.</p>
 */
function startsRun(messages, index) {
  return index === 0 || messages[index - 1].mine !== messages[index].mine;
}
