import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import { describeError } from '../../api/client';
import * as ekipy from '../../api/ekipy';
import Avatar from '../Avatar';
import KartaSpotkania from '../spotkanie/KartaSpotkania';
import SpotkanieForm from '../spotkanie/SpotkanieForm';
import TekstWiadomosci from '../TekstWiadomosci';
import { IconPin, IconSend, IconTrash } from '../Icons';
import useOdswiezanie from '../../hooks/useOdswiezanie';
import { timeAgo } from '../../utils/dates';

const ODSTEP_MS = 4000;

/**
 * Czat ekipy: kilka osob jadacych na jeden koncert. Tekst i spotkania (miejsce zbiorki z pinezka i przypomnieniem),
 * usuwanie wlasnych (zakladajacy usuwa tez cudze), nowe przez odpytywanie co 4 s, usuniete i odpowiedzi na spotkania
 * przez /changes. Dwa dni po koncercie - tylko do czytania.
 */
export default function EkipaCzat({ ekipaId, otwarty, onPrzeczytane }) {
  const { t, i18n } = useTranslation();
  const [wiadomosci, setWiadomosci] = useState([]);
  const [ladowanie, setLadowanie] = useState(true);
  const [starsze, setStarsze] = useState(false);
  const [tekst, setTekst] = useState('');
  const [wysylanie, setWysylanie] = useState(false);
  const [blad, setBlad] = useState(null);
  const [spotkanie, setSpotkanie] = useState(false);
  const okno = useRef(null);
  const naDole = useRef(true);
  const czasZmian = useRef(null);
  const oznaczone = useRef(0);

  const dopisz = useCallback((nowe) => {
    if (nowe.length === 0) return;
    setWiadomosci((stare) => {
      const znane = new Set(stare.map((m) => m.id));
      const dodane = nowe.filter((m) => !znane.has(m.id));
      return dodane.length ? [...stare, ...dodane] : stare;
    });
  }, []);

  const podmienSpotkanie = useCallback((s) => {
    setWiadomosci((stare) => (stare.some((m) => m.meeting?.id === s.id)
      ? stare.map((m) => (m.meeting?.id === s.id ? { ...m, meeting: s } : m)) : stare));
  }, []);

  useEffect(() => {
    let anulowane = false;
    czasZmian.current = null;
    ekipy.zmiany(ekipaId).then((z) => { if (!anulowane && !czasZmian.current) czasZmian.current = z.serverTime; }).catch(() => {});
    ekipy.czat(ekipaId)
      .then((lista) => { if (!anulowane) { setWiadomosci(lista); setStarsze(lista.length >= 40); } })
      .catch((p) => !anulowane && setBlad(describeError(p).message))
      .finally(() => !anulowane && setLadowanie(false));
    return () => { anulowane = true; };
  }, [ekipaId]);

  // Widziane na ekranie = przeczytane (licznik "Twoje koncerty" i push dopiero przy nastepnej nowej)
  useEffect(() => {
    if (wiadomosci.length === 0 || document.visibilityState !== 'visible') return;
    const ostatnia = wiadomosci[wiadomosci.length - 1].id;
    if (ostatnia <= oznaczone.current) return;
    oznaczone.current = ostatnia;
    ekipy.oznaczPrzeczytane(ekipaId, ostatnia).then(() => onPrzeczytane?.()).catch(() => { oznaczone.current = 0; });
  }, [wiadomosci, ekipaId, onPrzeczytane]);

  const odswiez = useCallback(async () => {
    const ostatnia = wiadomosci.length ? wiadomosci[wiadomosci.length - 1].id : 0;
    try {
      const [nowe, z] = await Promise.all([ekipy.czat(ekipaId, { po: ostatnia }), ekipy.zmiany(ekipaId, czasZmian.current)]);
      dopisz(nowe);
      if (czasZmian.current) {
        if (z.deletedIds.length) {
          const ids = new Set(z.deletedIds);
          setWiadomosci((stare) => stare.map((m) => (ids.has(m.id) ? jakoUsunieta(m) : m)));
        }
        z.meetings.forEach(podmienSpotkanie);
      }
      czasZmian.current = z.serverTime;
    } catch {
      // przy nastepnym odpytaniu dojdzie
    }
  }, [ekipaId, wiadomosci, dopisz, podmienSpotkanie]);
  useOdswiezanie(odswiez, ODSTEP_MS, !ladowanie);

  useEffect(() => {
    const el = okno.current;
    if (el && naDole.current) el.scrollTop = el.scrollHeight;
  }, [wiadomosci]);

  async function wczytajStarsze() {
    try {
      const lista = await ekipy.czat(ekipaId, { przed: wiadomosci[0].id });
      naDole.current = false;
      setWiadomosci((stare) => [...lista, ...stare]);
      setStarsze(lista.length >= 40);
    } catch (p) {
      setBlad(describeError(p).message);
    }
  }

  async function wyslij(e) {
    e.preventDefault();
    if (!tekst.trim() || wysylanie) return;
    setWysylanie(true);
    setBlad(null);
    try {
      const nowa = await ekipy.napisz(ekipaId, tekst.trim());
      setTekst('');
      naDole.current = true;
      dopisz([nowa]);
    } catch (p) {
      setBlad(describeError(p).message);
    } finally {
      setWysylanie(false);
    }
  }

  async function wyslijSpotkanie(dane) {
    try {
      const nowa = await ekipy.wyslijSpotkanie(ekipaId, dane);
      setSpotkanie(false);
      naDole.current = true;
      dopisz([nowa]);
    } catch (p) {
      throw new Error(describeError(p).message);
    }
  }

  async function usun(id) {
    if (!window.confirm(t('chat.deleteMessageConfirm'))) return;
    try {
      await ekipy.usunWiadomosc(ekipaId, id);
      setWiadomosci((stare) => stare.map((m) => (m.id === id ? jakoUsunieta(m) : m)));
    } catch (p) {
      setBlad(describeError(p).message);
    }
  }

  const zaczynaSerie = (i) => i === 0 || wiadomosci[i - 1].senderUsername !== wiadomosci[i].senderUsername;

  return (
    <section className="ekipa-czat" aria-label={t('crews.chat.title')}>
      <div className="chat-thread">
        <div className="chat-messages" ref={okno}
          onScroll={() => { const el = okno.current; naDole.current = el.scrollHeight - el.scrollTop - el.clientHeight < 60; }}>
          {ladowanie && <div className="chat-loading">{[0, 1, 2].map((i) => <span key={i} className={`bubble-skeleton${i % 2 ? ' is-mine' : ''}`} />)}</div>}
          {!ladowanie && wiadomosci.length === 0 && <p className="chat-empty">{t('crews.chat.empty')}</p>}
          {starsze && !ladowanie && (
            <div className="text-center mb-2">
              <Button variant="outline-secondary" size="sm" onClick={wczytajStarsze}>{t('chat.olderMessages')}</Button>
            </div>
          )}
          {wiadomosci.map((m, i) => (
            <div key={m.id} className={`bubble-row${m.mine ? ' is-mine' : ''}`}>
              {!m.mine && (
                <span className="bubble-avatar">
                  {zaczynaSerie(i) && <Avatar avatarUrl={m.senderAvatarUrl} username={m.senderUsername} size={26} />}
                </span>
              )}
              <div className={`bubble${m.deleted ? ' is-usunieta' : ''}`}>
                {!m.mine && zaczynaSerie(i) && <span className="klan-czat-autor">{m.senderUsername}</span>}
                {m.deleted ? <p className="bubble-usunieta">{t('chat.messageDeleted')}</p> : (
                  <>
                    <TekstWiadomosci tekst={m.content} />
                    {m.meeting && <KartaSpotkania spotkanie={m.meeting} onZmiana={podmienSpotkanie} />}
                  </>
                )}
                <span className="bubble-time">
                  {timeAgo(m.createdAt, i18n.language)}
                  {m.canDelete && (
                    <button type="button" className="klan-akcja czat-usun" onClick={() => usun(m.id)}
                      aria-label={t('chat.deleteMessage')} title={t('chat.deleteMessage')}>
                      <IconTrash size={11} />
                    </button>
                  )}
                </span>
              </div>
            </div>
          ))}
        </div>

        {otwarty && spotkanie && (
          <div className="chat-spotkanie-panel">
            <SpotkanieForm idPrefix="ekipa-spotkanie" onWyslij={wyslijSpotkanie} onZamknij={() => setSpotkanie(false)} />
          </div>
        )}
        {otwarty ? (
          <form className="chat-composer" onSubmit={wyslij}>
            {blad && <div className="chat-error">{blad}</div>}
            <div className="chat-composer-row">
              <button type="button" className={`chat-music-toggle chat-spotkanie-toggle${spotkanie ? ' is-open' : ''}`}
                onClick={() => setSpotkanie((b) => !b)} aria-pressed={spotkanie}
                aria-label={t('crews.chat.meetingPoint')} title={t('crews.chat.meetingPoint')}>
                <IconPin size={16} />
              </button>
              <textarea className="chat-input form-control" rows={1} value={tekst} maxLength={1000}
                onChange={(e) => setTekst(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) wyslij(e); }}
                placeholder={t('crews.chat.placeholder')} aria-label={t('crews.chat.placeholder')} />
              <button type="submit" className="chat-send" disabled={wysylanie || !tekst.trim()}
                aria-label={t('chat.send')} title={t('chat.send')}>
                <IconSend size={16} />
              </button>
            </div>
          </form>
        ) : (
          <div className="chat-composer chat-closed">
            <p className="mb-0 small text-body-secondary">{t('crews.chat.closed')}</p>
          </div>
        )}
      </div>
    </section>
  );
}

function jakoUsunieta(m) {
  return { ...m, deleted: true, content: '', meeting: null, canDelete: false };
}
