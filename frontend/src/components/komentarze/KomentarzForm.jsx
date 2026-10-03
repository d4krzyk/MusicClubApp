import {
  useCallback, useEffect, useId, useRef, useState,
} from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import { describeError } from '../../api/client';
import * as komentarze from '../../api/komentarze';
import { useAuth } from '../../auth/AuthContext';
import useGify from '../../hooks/useGify';
import Avatar from '../Avatar';
import GifPicker from '../gif/GifPicker';
import { IconCross, IconGif, IconSend } from '../Icons';

/** To samo, czego pilnuje serwer. */
export const MAKS_DLUGOSC = 1000;

/** Po tylu ms od ostatniej litery pytamy o osoby do oznaczenia. */
const ZWLOKA_PODPOWIEDZI = 150;

/** Slowo przed kursorem, ktore zaczyna sie od @ - "ala @ma" daje "ma". */
const SLOWO_PRZED_KURSOREM = /(^|\s)@([A-Za-z0-9_.-]{0,50})$/;

/**
 * Pole komentarza (nowego albo odpowiedzi). Wpisanie "@" otwiera liste osob, ktore mozna oznaczyc pod
 * tym postem - strzalki wybieraja, Enter/Tab wstawia, Esc zamyka.
 * `poczatek` to tekst na starcie (przy odpowiedzi: "@login "), `onDodano` dostaje gotowy komentarz.
 */
export default function KomentarzForm({
  idPosta, idRodzica = null, poczatek = '', onDodano, onAnuluj = null, autoFocus = false, etykieta,
}) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const gify = useGify();
  const id = useId();
  const pole = useRef(null);
  const [tekst, setTekst] = useState(poczatek);
  const [wysylanie, setWysylanie] = useState(false);
  const [blad, setBlad] = useState(null);
  const [podpowiedzi, setPodpowiedzi] = useState([]);
  const [aktywna, setAktywna] = useState(0);
  const [fragment, setFragment] = useState(null);
  /* GIF wybrany z przegladarki (caly wynik z tokenem) i czy przegladarka jest otwarta */
  const [gif, setGif] = useState(null);
  const [gifOtwarty, setGifOtwarty] = useState(false);

  useEffect(() => {
    if (autoFocus && pole.current) {
      pole.current.focus();
      const koniec = pole.current.value.length;
      pole.current.setSelectionRange(koniec, koniec);
    }
  }, [autoFocus]);

  /* Pole rosnie razem z tekstem, do czterech wierszy. */
  useEffect(() => {
    const el = pole.current;
    if (el) {
      el.style.height = 'auto';
      el.style.height = `${Math.min(el.scrollHeight, 120)}px`;
    }
  }, [tekst]);

  /* Podpowiedzi: pytamy dopiero po chwili ciszy i ignorujemy spozniona odpowiedz. */
  useEffect(() => {
    if (fragment === null) {
      setPodpowiedzi([]);
      return undefined;
    }
    let aktualne = true;
    const zegar = setTimeout(() => {
      komentarze.doOznaczenia(idPosta, fragment)
        .then((lista) => {
          if (aktualne) {
            setPodpowiedzi(lista);
            setAktywna(0);
          }
        })
        .catch(() => aktualne && setPodpowiedzi([]));
    }, ZWLOKA_PODPOWIEDZI);
    return () => {
      aktualne = false;
      clearTimeout(zegar);
    };
  }, [fragment, idPosta]);

  const sprawdzKursor = useCallback((wartosc, kursor) => {
    const trafienie = SLOWO_PRZED_KURSOREM.exec(wartosc.slice(0, kursor));
    setFragment(trafienie ? trafienie[2] : null);
  }, []);

  function zmiana(e) {
    setTekst(e.target.value);
    setBlad(null);
    sprawdzKursor(e.target.value, e.target.selectionStart);
  }

  function wstaw(login) {
    const el = pole.current;
    const kursor = el.selectionStart;
    const przed = tekst.slice(0, kursor).replace(SLOWO_PRZED_KURSOREM, (cale, spacja) => `${spacja}@${login} `);
    const po = tekst.slice(kursor);
    setTekst(przed + po);
    setFragment(null);
    setPodpowiedzi([]);
    // kursor ma stanac za wstawionym oznaczeniem
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(przed.length, przed.length);
    });
  }

  function klawisz(e) {
    if (podpowiedzi.length > 0 && fragment !== null) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setAktywna((a) => (a + 1) % podpowiedzi.length);
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        setAktywna((a) => (a - 1 + podpowiedzi.length) % podpowiedzi.length);
        return;
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault();
        wstaw(podpowiedzi[aktywna].username);
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        setFragment(null);
        return;
      }
    }
    // Ctrl/Cmd+Enter wysyla - sam Enter to nowy wiersz, jak w innych komentarzach
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      wyslij(e);
    }
  }

  async function wyslij(e) {
    e.preventDefault();
    const tresc = tekst.trim();
    if ((!tresc && !gif) || wysylanie) {
      return;
    }
    setWysylanie(true);
    setBlad(null);
    try {
      const dodany = await komentarze.dodaj(idPosta, { tresc, idRodzica, gif: gif?.token ?? null });
      setTekst('');
      setFragment(null);
      setGif(null);
      setGifOtwarty(false);
      onDodano?.(dodany);
    } catch (problem) {
      const szczegoly = describeError(problem);
      setBlad(szczegoly.fieldErrors.content ?? szczegoly.fieldErrors.gif ?? szczegoly.message);
    } finally {
      setWysylanie(false);
    }
  }

  const zaDlugi = tekst.length > MAKS_DLUGOSC;
  const otwarte = fragment !== null && podpowiedzi.length > 0;

  return (
    <Form onSubmit={wyslij} className="komentarz-form" noValidate>
      <div className="d-flex gap-2 align-items-start">
        <Avatar avatarUrl={user?.avatarUrl} username={user?.username ?? '?'} size={28} />
        <div className="flex-grow-1 komentarz-pole">
          <Form.Control
            ref={pole}
            as="textarea"
            rows={1}
            id={id}
            value={tekst}
            onChange={zmiana}
            onKeyDown={klawisz}
            onClick={(e) => sprawdzKursor(tekst, e.target.selectionStart)}
            onBlur={() => setTimeout(() => setFragment(null), 120)}
            placeholder={etykieta ?? (idRodzica ? t('comments.replyPlaceholder') : t('comments.placeholder'))}
            aria-label={etykieta ?? t('comments.placeholder')}
            aria-autocomplete="list"
            aria-expanded={otwarte}
            aria-controls={otwarte ? `${id}-lista` : undefined}
            maxLength={MAKS_DLUGOSC + 200}
            className="komentarz-textarea"
          />

          {otwarte && (
            <ul id={`${id}-lista`} className="komentarz-podpowiedzi list-unstyled" role="listbox"
              aria-label={t('comments.mentionList')}>
              {podpowiedzi.map((o, i) => (
                <li key={o.username} role="option" aria-selected={i === aktywna}
                  className={i === aktywna ? 'is-aktywna' : ''}>
                  {/* mouseDown, a nie click: click przychodzi po blur pola i lista zdazylaby zniknac */}
                  <button type="button" onMouseDown={(e) => { e.preventDefault(); wstaw(o.username); }}>
                    <Avatar avatarUrl={o.avatarUrl} username={o.username} size={22} />
                    <span className="text-truncate">{o.username}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        {gify.wlaczone && (
          <Button type="button" size="sm" variant={gifOtwarty ? 'primary' : 'outline-secondary'}
            className="komentarz-gif-przycisk" onClick={() => setGifOtwarty((o) => !o)}
            aria-pressed={gifOtwarty} aria-label={t('gifs.add')} title={t('gifs.add')}>
            <IconGif size={20} />
          </Button>
        )}
        <Button type="submit" size="sm" disabled={wysylanie || (!tekst.trim() && !gif) || zaDlugi}
          aria-label={t('comments.send')} title={t('comments.send')}>
          <IconSend size={14} />
        </Button>
      </div>

      {gif && (
        <div className="gif-wybrany komentarz-gif-wybrany">
          <img src={gif.previewUrl} alt={gif.title || t('gifs.selected')} referrerPolicy="no-referrer" />
          <button type="button" onClick={() => setGif(null)} aria-label={t('gifs.remove')} title={t('gifs.remove')}>
            <IconCross size={12} />
          </button>
        </div>
      )}

      {gifOtwarty && (
        <GifPicker
          idPrefix={`${id}-gif`}
          podpis={gify.podpis}
          onWybierz={(wybrany) => { setGif(wybrany); setGifOtwarty(false); }}
          onZamknij={() => setGifOtwarty(false)}
        />
      )}

      <div className="d-flex justify-content-between align-items-center komentarz-pod">
        <span className="small text-body-secondary">{t('comments.hint')}</span>
        {tekst.length > MAKS_DLUGOSC * 0.8 && (
          <span className={`small ${zaDlugi ? 'text-danger' : 'text-body-secondary'}`}>
            {tekst.length}/{MAKS_DLUGOSC}
          </span>
        )}
        {onAnuluj && (
          <Button type="button" variant="link" size="sm" className="p-0" onClick={onAnuluj}>
            {t('common.cancel')}
          </Button>
        )}
      </div>
      {blad && <div className="small text-danger mt-1" role="alert">{blad}</div>}
    </Form>
  );
}
