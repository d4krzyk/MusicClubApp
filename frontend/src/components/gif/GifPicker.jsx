import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import { describeError } from '../../api/client';
import { szukaj } from '../../api/gify';
import { IconCross } from '../Icons';

/** Po tylu ms od ostatniej litery pytamy serwer. */
const ZWLOKA_MS = 400;

/** Najwyzej tyle znakow we frazie - tyle przyjmuje serwer. */
const MAKS_FRAZA = 80;

/**
 * Przegladarka GIF-ow: pole szukania, kafelki z podgladem i "Pokaz wiecej". Bez frazy pokazuje popularne.
 * Pod spodem podpis dostawcy ("Powered by KLIPY") - wymaga go regulamin obu dostawcow.
 * `onWybierz` dostaje caly wynik ({ token, previewUrl, title, ... }), a okno zamyka sie samo.
 */
export default function GifPicker({ podpis, onWybierz, onZamknij, idPrefix = 'gif' }) {
  const { t } = useTranslation();
  const [fraza, setFraza] = useState('');
  const [wyniki, setWyniki] = useState([]);
  const [nastepna, setNastepna] = useState(null);
  const [ladowanie, setLadowanie] = useState(true);
  const [doladowanie, setDoladowanie] = useState(false);
  const [blad, setBlad] = useState(null);
  const [szukano, setSzukano] = useState('');
  const pole = useRef(null);
  /* Spozniona odpowiedz na stare pytanie nie ma nadpisac nowszej. */
  const numer = useRef(0);
  const zegar = useRef(null);

  const wczytaj = useCallback(async (tekst, pozycja) => {
    const moj = ++numer.current;
    if (pozycja) {
      setDoladowanie(true);
    } else {
      setLadowanie(true);
    }
    setBlad(null);
    try {
      const dane = await szukaj(tekst, pozycja);
      if (moj !== numer.current) {
        return;
      }
      setWyniki((obecne) => {
        if (!pozycja) {
          return dane.items;
        }
        const znane = new Set(obecne.map((g) => g.id));
        return [...obecne, ...dane.items.filter((g) => !znane.has(g.id))];
      });
      setNastepna(dane.next);
      setSzukano(tekst);
    } catch (problem) {
      if (moj === numer.current) {
        setBlad(describeError(problem).message);
      }
    } finally {
      if (moj === numer.current) {
        setLadowanie(false);
        setDoladowanie(false);
      }
    }
  }, []);

  /* Pierwsze wejscie: popularne; potem zapytanie po chwili ciszy. */
  useEffect(() => {
    zegar.current = setTimeout(() => wczytaj(fraza.trim(), null), fraza === '' ? 0 : ZWLOKA_MS);
    return () => clearTimeout(zegar.current);
  }, [fraza, wczytaj]);

  useEffect(() => {
    pole.current?.focus();
  }, []);

  function klawisz(e) {
    // Pole szukania siedzi w formularzu komentarza albo czatu - Enter ma szukac, a nie wysylac tamten formularz
    if (e.key === 'Enter') {
      e.preventDefault();
      clearTimeout(zegar.current);
      wczytaj(fraza.trim(), null);
      return;
    }
    if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      onZamknij();
    }
  }

  return (
    <div className="gif-picker" role="dialog" aria-label={t('gifs.title')} onKeyDown={klawisz}>
      <div className="gif-picker-gora">
        <input
          ref={pole}
          id={`${idPrefix}-szukaj`}
          type="search"
          className="form-control form-control-sm"
          value={fraza}
          maxLength={MAKS_FRAZA}
          onChange={(e) => setFraza(e.target.value)}
          placeholder={t('gifs.search')}
          aria-label={t('gifs.search')}
          autoComplete="off"
        />
        <button type="button" className="gif-picker-zamknij" onClick={onZamknij}
          aria-label={t('gifs.close')} title={t('gifs.close')}>
          <IconCross size={14} />
        </button>
      </div>

      <div className="gif-picker-lista" aria-busy={ladowanie}>
        {blad && (
          <div className="gif-picker-komunikat" role="alert">
            <p className="mb-2">{blad}</p>
            <Button size="sm" variant="outline-secondary" onClick={() => wczytaj(fraza.trim(), null)}>
              {t('gifs.retry')}
            </Button>
          </div>
        )}

        {ladowanie && !blad && wyniki.length === 0 && (
          <p className="gif-picker-komunikat">{t('common.loading')}</p>
        )}

        {!ladowanie && !blad && wyniki.length === 0 && (
          <p className="gif-picker-komunikat">
            {szukano ? t('gifs.nothing', { query: szukano }) : t('gifs.empty')}
          </p>
        )}

        {wyniki.length > 0 && (
          <>
            <p className="gif-picker-naglowek">{szukano ? t('gifs.results') : t('gifs.trending')}</p>
            <ul className="gif-picker-siatka list-unstyled" role="list">
              {wyniki.map((g) => (
                <li key={g.id}>
                  <button type="button" className="gif-picker-kafelek" onClick={() => onWybierz(g)}
                    aria-label={g.title || t('gifs.alt')} title={g.title || undefined}>
                    <img src={g.previewUrl} alt="" width={g.width || undefined} height={g.height || undefined}
                      loading="lazy" decoding="async" referrerPolicy="no-referrer" />
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}

        {nastepna && !blad && (
          <div className="text-center mt-2">
            <Button size="sm" variant="outline-secondary" onClick={() => wczytaj(szukano, nastepna)}
              disabled={doladowanie}>
              {doladowanie ? t('common.loading') : t('gifs.more')}
            </Button>
          </div>
        )}
      </div>

      {podpis && <p className="gif-picker-podpis">{t('gifs.powered', { name: podpis })}</p>}
    </div>
  );
}
