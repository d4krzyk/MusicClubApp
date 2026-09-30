import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import Avatar from '../Avatar';
import { IconSend, IconTrash } from '../Icons';
import useOdswiezanie from '../../hooks/useOdswiezanie';
import { timeAgo } from '../../utils/dates';

/** Co ile sekund pytamy o nowe wiadomosci, gdy karta jest na wierzchu. */
const ODSTEP_MS = 4000;

/**
 * Czat klanu. Ten sam wyglad dymkow co w rozmowach ze znajomymi, ale jedna wspolna rozmowa
 * dla calego klanu; nowe wiadomosci przychodza przez odpytywanie (nie ma polaczenia na stale).
 * Administrator aplikacji, ktory nie jest czlonkiem, czyta, ale nie pisze.
 */
export default function KlanCzat({ klan }) {
  const { t, i18n } = useTranslation();
  const [wiadomosci, setWiadomosci] = useState([]);
  const [ladowanie, setLadowanie] = useState(true);
  const [starsze, setStarsze] = useState(false);
  const [ladujStarsze, setLadujStarsze] = useState(false);
  const [tekst, setTekst] = useState('');
  const [wysylanie, setWysylanie] = useState(false);
  const [blad, setBlad] = useState(null);
  const okno = useRef(null);
  const naDole = useRef(true);
  const mozePisac = klan.myRole != null;

  const dopiszNowe = useCallback((nowe) => {
    if (nowe.length === 0) {
      return;
    }
    setWiadomosci((stare) => {
      const znane = new Set(stare.map((m) => m.id));
      const dodane = nowe.filter((m) => !znane.has(m.id));
      return dodane.length === 0 ? stare : [...stare, ...dodane];
    });
  }, []);

  useEffect(() => {
    let anulowane = false;
    setLadowanie(true);
    klany.czat(klan.id)
      .then((lista) => {
        if (!anulowane) {
          setWiadomosci(lista);
          setStarsze(lista.length >= 40);
        }
      })
      .catch((p) => !anulowane && setBlad(describeError(p).message))
      .finally(() => !anulowane && setLadowanie(false));
    return () => { anulowane = true; };
  }, [klan.id]);

  const odswiez = useCallback(async () => {
    const ostatnie = wiadomosci.length > 0 ? wiadomosci[wiadomosci.length - 1].id : 0;
    try {
      dopiszNowe(await klany.czat(klan.id, { po: ostatnie }));
    } catch {
      // Chwilowy brak sieci - przy nastepnym odpytaniu dojdzie to, co przegapilismy
    }
  }, [klan.id, wiadomosci, dopiszNowe]);
  useOdswiezanie(odswiez, ODSTEP_MS, !ladowanie);

  /* Przewijamy na dol tylko wtedy, gdy ktos juz tam byl - inaczej czytanie starszych bylo by szarpane */
  useEffect(() => {
    const el = okno.current;
    if (el && naDole.current) {
      el.scrollTop = el.scrollHeight;
    }
  }, [wiadomosci]);

  function przyPrzewijaniu() {
    const el = okno.current;
    naDole.current = el.scrollHeight - el.scrollTop - el.clientHeight < 60;
  }

  async function wczytajStarsze() {
    setLadujStarsze(true);
    try {
      const lista = await klany.czat(klan.id, { przed: wiadomosci[0].id });
      naDole.current = false;
      setWiadomosci((stare) => [...lista, ...stare]);
      setStarsze(lista.length >= 40);
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setLadujStarsze(false);
    }
  }

  async function wyslij(e) {
    e.preventDefault();
    if (!tekst.trim() || wysylanie) {
      return;
    }
    setWysylanie(true);
    setBlad(null);
    try {
      const nowa = await klany.napisz(klan.id, tekst);
      setTekst('');
      naDole.current = true;
      dopiszNowe([nowa]);
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setWysylanie(false);
    }
  }

  async function usun(id) {
    if (!window.confirm(t('common.confirmDelete'))) {
      return;
    }
    try {
      await klany.usunWiadomosc(klan.id, id);
      setWiadomosci((stare) => stare.filter((m) => m.id !== id));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }

  /** Awatar tylko przy pierwszej wiadomosci z serii jednej osoby - jak w rozmowach ze znajomymi. */
  const zaczynaSerie = (i) => i === 0 || wiadomosci[i - 1].senderUsername !== wiadomosci[i].senderUsername;

  return (
    <section className="klan-czat" aria-label={t('clans.chat.title')}>
      <div className="chat-thread">
        <div className="chat-messages" ref={okno} onScroll={przyPrzewijaniu}>
          {ladowanie && (
            <div className="chat-loading">
              {[0, 1, 2].map((i) => <span key={i} className={`bubble-skeleton${i % 2 ? ' is-mine' : ''}`} />)}
            </div>
          )}

          {!ladowanie && wiadomosci.length === 0 && <p className="chat-empty">{t('clans.chat.empty')}</p>}

          {starsze && !ladowanie && (
            <div className="text-center mb-2">
              <Button variant="outline-secondary" size="sm" onClick={wczytajStarsze} disabled={ladujStarsze}>
                {ladujStarsze ? t('common.loading') : t('chat.olderMessages')}
              </Button>
            </div>
          )}

          {wiadomosci.map((m, i) => (
            <div key={m.id} className={`bubble-row${m.mine ? ' is-mine' : ''}`}>
              {!m.mine && (
                <span className="bubble-avatar">
                  {zaczynaSerie(i) && <Avatar avatarUrl={m.senderAvatarUrl} username={m.senderUsername} size={26} />}
                </span>
              )}
              <div className="bubble">
                {!m.mine && zaczynaSerie(i) && <span className="klan-czat-autor">{m.senderUsername}</span>}
                <p className="bubble-text">{m.content}</p>
                <span className="bubble-time">
                  {timeAgo(m.createdAt, i18n.language)}
                  {m.canDelete && (
                    <button
                      type="button"
                      className="btn btn-link btn-sm p-0 ms-2 align-baseline text-reset"
                      onClick={() => usun(m.id)}
                      aria-label={t('common.delete')}
                      title={t('common.delete')}
                    >
                      <IconTrash size={11} />
                    </button>
                  )}
                </span>
              </div>
            </div>
          ))}
        </div>

        {mozePisac ? (
          <form className="chat-composer" onSubmit={wyslij}>
            {blad && <div className="chat-error">{blad}</div>}
            <div className="chat-composer-row">
              <textarea
                className="chat-input form-control"
                rows={1}
                value={tekst}
                onChange={(e) => setTekst(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { wyslij(e); } }}
                placeholder={t('clans.chat.placeholder')}
                aria-label={t('clans.chat.placeholder')}
                maxLength={1000}
              />
              <button
                type="submit"
                className="chat-send"
                disabled={wysylanie || !tekst.trim()}
                aria-label={t('chat.send')}
                title={t('chat.send')}
              >
                <IconSend size={16} />
              </button>
            </div>
          </form>
        ) : (
          <div className="chat-composer chat-closed">
            <p className="mb-0 small text-body-secondary">{t('clans.chat.readOnly')}</p>
          </div>
        )}
      </div>
    </section>
  );
}
