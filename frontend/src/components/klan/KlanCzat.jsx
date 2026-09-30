import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import Avatar from '../Avatar';
import { IconBell, IconCross, IconReply, IconSend, IconSmile, IconTrash } from '../Icons';
import useOdswiezanie from '../../hooks/useOdswiezanie';
import { timeAgo } from '../../utils/dates';
import { EMOJI, ODSWIEZ_LICZNIK } from '../../utils/klan';

/** Co ile sekund pytamy o nowe wiadomosci, gdy karta jest na wierzchu. */
const ODSTEP_MS = 4000;

/**
 * Czat klanu. Ten sam wyglad dymkow co w rozmowach ze znajomymi, ale jedna wspolna rozmowa
 * dla calego klanu; nowe wiadomosci przychodza przez odpytywanie (nie ma polaczenia na stale).
 * Mozna odpowiadac na konkretna wiadomosc i reagowac emoji (jedna reakcja na osobe).
 * Wiadomosci widziane na ekranie sa oznaczane jako przeczytane - stad licznik w menu.
 * Administrator aplikacji, ktory nie jest czlonkiem, czyta, ale nie pisze i niczego nie oznacza.
 */
export default function KlanCzat({ klan, onZmiana }) {
  const { t, i18n } = useTranslation();
  const [wiadomosci, setWiadomosci] = useState([]);
  const [ladowanie, setLadowanie] = useState(true);
  const [starsze, setStarsze] = useState(false);
  const [ladujStarsze, setLadujStarsze] = useState(false);
  const [tekst, setTekst] = useState('');
  const [odpowiedzNa, setOdpowiedzNa] = useState(null);
  const [wybor, setWybor] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);
  const [blad, setBlad] = useState(null);
  const [wyciszony, setWyciszony] = useState(klan.chatMuted);
  const okno = useRef(null);
  const naDole = useRef(true);
  const mozePisac = klan.myRole != null;

  /* Kreska "nowe wiadomosci" - stoi tam, gdzie skonczyla sie poprzednia wizyta, nawet gdy juz przeczytane */
  const granica = useRef(klan.chatReadId);
  const pokazNowe = useRef(klan.unreadChat > 0);
  const ostatnioOznaczone = useRef(klan.chatReadId);

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

  /** Czlonek widzi czat na ekranie: zapisujemy "przeczytane do" i mowimy o tym menu. */
  const oznaczPrzeczytane = useCallback((lista) => {
    if (!mozePisac || lista.length === 0 || document.visibilityState !== 'visible') {
      return;
    }
    const ostatnia = lista[lista.length - 1].id;
    if (ostatnia <= ostatnioOznaczone.current) {
      return;
    }
    ostatnioOznaczone.current = ostatnia;
    klany.oznaczPrzeczytane(klan.id, ostatnia)
      .then(() => {
        onZmiana((k) => ({ ...k, unreadChat: 0, chatReadId: Math.max(k.chatReadId ?? 0, ostatnia) }));
        window.dispatchEvent(new Event(ODSWIEZ_LICZNIK));
      })
      .catch(() => { ostatnioOznaczone.current = Math.min(ostatnioOznaczone.current, ostatnia - 1); });
  }, [klan.id, mozePisac, onZmiana]);

  useEffect(() => { oznaczPrzeczytane(wiadomosci); }, [wiadomosci, oznaczPrzeczytane]);

  const odswiez = useCallback(async () => {
    const ostatnie = wiadomosci.length > 0 ? wiadomosci[wiadomosci.length - 1].id : 0;
    const pierwsza = wiadomosci.length > 0 ? wiadomosci[0].id : 0;
    try {
      const [nowe, reakcje] = await Promise.all([
        klany.czat(klan.id, { po: ostatnie }),
        klany.reakcjeOd(klan.id, pierwsza),
      ]);
      dopiszNowe(nowe);
      // Odpowiedz opisuje reakcje calego zakresu: wiadomosc, ktorej w niej nie ma, nie ma reakcji
      const wg = new Map(reakcje.map((r) => [r.messageId, r.reactions]));
      setWiadomosci((stare) => stare.map((m) => (
        m.id >= pierwsza && m.id <= ostatnie ? { ...m, reactions: wg.get(m.id) ?? [] } : m
      )));
    } catch {
      // Chwilowy brak sieci - przy nastepnym odpytaniu dojdzie to, co przegapilismy
    }
    oznaczPrzeczytane(wiadomosci);
  }, [klan.id, wiadomosci, dopiszNowe, oznaczPrzeczytane]);
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
      const nowa = await klany.napisz(klan.id, tekst, odpowiedzNa?.id ?? null);
      setTekst('');
      setOdpowiedzNa(null);
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
      setOdpowiedzNa((o) => (o?.id === id ? null : o));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }

  /** Klikniecie w emoji: to samo, co moje - cofa reakcje; inne - ustawia (albo podmienia) ja. */
  async function reaguj(wiadomosc, typ) {
    setWybor(null);
    const moja = (wiadomosc.reactions ?? []).find((r) => r.mine);
    try {
      const nowe = moja?.type === typ
        ? await klany.cofnijReakcje(klan.id, wiadomosc.id)
        : await klany.reaguj(klan.id, wiadomosc.id, typ);
      setWiadomosci((stare) => stare.map((m) => (m.id === wiadomosc.id ? { ...m, reactions: nowe } : m)));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }

  async function przelaczWyciszenie() {
    const nowy = !wyciszony;
    try {
      await klany.wycisz(klan.id, nowy);
      setWyciszony(nowy);
      onZmiana((k) => ({ ...k, chatMuted: nowy }));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }

  function przejdzDo(id) {
    const cel = document.getElementById(`klan-wiad-${id}`);
    if (!cel) {
      return;
    }
    naDole.current = false;
    cel.scrollIntoView({ block: 'center', behavior: 'smooth' });
    cel.classList.add('is-podswietlona');
    setTimeout(() => cel.classList.remove('is-podswietlona'), 1600);
  }

  /** Awatar tylko przy pierwszej wiadomosci z serii jednej osoby - jak w rozmowach ze znajomymi. */
  const zaczynaSerie = (i) => i === 0 || wiadomosci[i - 1].senderUsername !== wiadomosci[i].senderUsername;
  const pierwszaNowa = pokazNowe.current
    ? wiadomosci.find((m) => !m.mine && m.id > (granica.current ?? 0))?.id
    : null;

  return (
    <section className="klan-czat" aria-label={t('clans.chat.title')}>
      {mozePisac && (
        <div className="klan-czat-pasek">
          <Button
            variant={wyciszony ? 'secondary' : 'outline-secondary'}
            size="sm"
            aria-pressed={wyciszony}
            onClick={przelaczWyciszenie}
            title={wyciszony ? t('clans.chat.mutedNote') : undefined}
          >
            <IconBell size={13} className="me-1" />
            {wyciszony ? t('clans.chat.unmute') : t('clans.chat.mute')}
          </Button>
        </div>
      )}

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
            <div key={m.id}>
              {m.id === pierwszaNowa && (
                <div className="klan-nowe" role="separator"><span>{t('clans.chat.newMessages')}</span></div>
              )}
              <div id={`klan-wiad-${m.id}`} className={`bubble-row klan-wiadomosc${m.mine ? ' is-mine' : ''}`}>
                {!m.mine && (
                  <span className="bubble-avatar">
                    {zaczynaSerie(i) && <Avatar avatarUrl={m.senderAvatarUrl} username={m.senderUsername} size={26} />}
                  </span>
                )}
                <div className="bubble">
                  {!m.mine && zaczynaSerie(i) && <span className="klan-czat-autor">{m.senderUsername}</span>}

                  {m.replyToId != null && (
                    m.replyTo ? (
                      <button type="button" className="klan-cytat" onClick={() => przejdzDo(m.replyTo.id)}
                        title={t('clans.chat.goToMessage')}>
                        <span className="klan-cytat-autor">{m.replyTo.senderUsername}</span>
                        <span className="klan-cytat-tekst">{m.replyTo.excerpt}</span>
                      </button>
                    ) : (
                      <span className="klan-cytat is-brak">{t('clans.chat.replyUnavailable')}</span>
                    )
                  )}

                  <p className="bubble-text">{m.content}</p>

                  {(m.reactions ?? []).length > 0 && (
                    <div className="klan-reakcje">
                      {m.reactions.map((r) => (
                        <button
                          key={r.type}
                          type="button"
                          className={`klan-reakcja${r.mine ? ' is-moja' : ''}`}
                          aria-pressed={r.mine}
                          disabled={!mozePisac}
                          onClick={() => reaguj(m, r.type)}
                          title={t(`clans.emoji.${r.type}`)}
                        >
                          <span aria-hidden="true">{EMOJI[r.type]}</span>
                          <span className="visually-hidden">{t(`clans.emoji.${r.type}`)}</span>
                          <span className="klan-reakcja-liczba">{r.count}</span>
                        </button>
                      ))}
                    </div>
                  )}

                  <span className="bubble-time">
                    {timeAgo(m.createdAt, i18n.language)}
                    {mozePisac && (
                      <>
                        <button type="button" className="klan-akcja" onClick={() => { setOdpowiedzNa(m); }}
                          aria-label={t('clans.chat.reply')} title={t('clans.chat.reply')}>
                          <IconReply size={12} />
                        </button>
                        <button type="button" className="klan-akcja" aria-expanded={wybor === m.id}
                          onClick={() => setWybor((w) => (w === m.id ? null : m.id))}
                          aria-label={t('clans.chat.react')} title={t('clans.chat.react')}>
                          <IconSmile size={12} />
                        </button>
                      </>
                    )}
                    {m.canDelete && (
                      <button
                        type="button"
                        className="klan-akcja"
                        onClick={() => usun(m.id)}
                        aria-label={t('common.delete')}
                        title={t('common.delete')}
                      >
                        <IconTrash size={11} />
                      </button>
                    )}
                  </span>

                  {wybor === m.id && (
                    <div className="klan-wybor" role="group" aria-label={t('clans.chat.react')}>
                      {Object.keys(EMOJI).map((typ) => (
                        <button key={typ} type="button" className="klan-wybor-emoji" onClick={() => reaguj(m, typ)}
                          aria-label={t(`clans.emoji.${typ}`)} title={t(`clans.emoji.${typ}`)}>
                          {EMOJI[typ]}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>

        {mozePisac ? (
          <form className="chat-composer" onSubmit={wyslij}>
            {blad && <div className="chat-error">{blad}</div>}
            {odpowiedzNa && (
              <div className="klan-odpowiadasz">
                <span className="klan-odpowiadasz-tekst">
                  <strong>{t('clans.chat.replyingTo', { username: odpowiedzNa.senderUsername })}</strong>
                  <span>{odpowiedzNa.content}</span>
                </span>
                <button type="button" className="klan-akcja" onClick={() => setOdpowiedzNa(null)}
                  aria-label={t('clans.chat.cancelReply')} title={t('clans.chat.cancelReply')}>
                  <IconCross size={12} />
                </button>
              </div>
            )}
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
