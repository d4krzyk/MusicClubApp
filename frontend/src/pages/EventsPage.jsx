import { Fragment, useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../api/client';
import * as wydarzenia from '../api/wydarzenia';
import { useAuth } from '../auth/AuthContext';
import DoladujWiecej from '../components/DoladujWiecej';
import EmptyState from '../components/EmptyState';
import EventCard from '../components/EventCard';
import { IconCalendar, IconSearch } from '../components/Icons';
import { formatDateTime } from '../utils/dates';
import { naglowekDnia, nazwaMiasta } from '../utils/wydarzenia';

const ROZMIAR_STRONY = 20;

/** Po tylu milisekundach od ostatniej litery szukamy - nie przy kazdym klawiszu. */
const ZWLOKA_SZUKANIA = 350;

/** Pod tym kluczem przegladarka pamieta wybrane miasto. */
const PAMIEC_MIASTA = 'wydarzenia.miasto';

function zapamietaneMiasto() {
  try {
    return localStorage.getItem(PAMIEC_MIASTA) ?? '';
  } catch {
    // Tryb prywatny albo zablokowane dane strony - po prostu bez pamieci
    return '';
  }
}

function zapamietajMiasto(klucz) {
  try {
    if (klucz) {
      localStorage.setItem(PAMIEC_MIASTA, klucz);
    } else {
      localStorage.removeItem(PAMIEC_MIASTA);
    }
  } catch {
    // jw.
  }
}

/** Zakladka Wydarzenia: nadchodzace koncerty, z filtrem miasta i wyszukiwarka. */
export default function EventsPage() {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();

  /*
   * Miasto i fraza siedza w adresie strony (?miasto=krakow&q=jazz). Dzieki
   * temu "wstecz" ze strony wydarzenia wraca do tej samej, przefiltrowanej
   * listy, a taki adres da sie komus wyslac.
   */
  const [parametry, setParametry] = useSearchParams();
  const miasto = parametry.get('miasto') ?? zapamietaneMiasto();
  const fraza = parametry.get('q') ?? '';

  /* To, co wpisuje uzytkownik - trafia do adresu dopiero po chwili ciszy. */
  const [wpisane, setWpisane] = useState(fraza);

  const [info, setInfo] = useState(null);
  const [lista, setLista] = useState([]);
  const [strona, setStrona] = useState(null);
  const [ladowanie, setLadowanie] = useState(true);
  const [doladowanie, setDoladowanie] = useState(false);
  const [blad, setBlad] = useState(null);

  /*
   * Numer ostatniego zapytania. Przy szybkim przelaczaniu miast odpowiedz
   * na starsze zapytanie potrafi przyjsc PO nowszym - i nadpisalaby liste
   * wydarzeniami z miasta, z ktorego juz wyszlismy.
   */
  const ostatnie = useRef(0);

  useEffect(() => {
    wydarzenia.info()
      .then(setInfo)
      .catch(() => setInfo({ configured: true, cities: [] }));   // filtr to dodatek
  }, []);

  const wczytaj = useCallback(async (numerStrony) => {
    const numer = ++ostatnie.current;
    if (numerStrony === 0) {
      setLadowanie(true);
    } else {
      setDoladowanie(true);
    }
    setBlad(null);

    try {
      const dane = await wydarzenia.lista({
        miasto, fraza, strona: numerStrony, rozmiar: ROZMIAR_STRONY,
      });
      if (numer !== ostatnie.current) {
        return;
      }
      setLista((obecne) => (numerStrony === 0 ? dane.content : [...obecne, ...dane.content]));
      setStrona(dane);
    } catch (problem) {
      if (numer === ostatnie.current) {
        setBlad(describeError(problem).message);
      }
    } finally {
      if (numer === ostatnie.current) {
        setLadowanie(false);
        setDoladowanie(false);
      }
    }
  }, [miasto, fraza]);

  useEffect(() => {
    wczytaj(0);
  }, [wczytaj]);

  /*
   * Fraza zmieniona z zewnatrz (np. "wstecz" w przegladarce) wraca do pola.
   * Porownujemy z przycietym tekstem - inaczej spacja wpisana miedzy
   * dwoma slowami znikalaby w polowie pisania.
   */
  useEffect(() => {
    setWpisane((obecne) => (obecne.trim() === fraza ? obecne : fraza));
  }, [fraza]);

  /* Fraza wpisana w pole trafia do adresu po chwili bez pisania. */
  useEffect(() => {
    if (wpisane.trim() === fraza) {
      return undefined;
    }
    const zegar = setTimeout(() => {
      zmienParametry({ q: wpisane.trim() });
    }, ZWLOKA_SZUKANIA);
    return () => clearTimeout(zegar);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wpisane]);

  function zmienParametry(zmiany) {
    const nowe = new URLSearchParams(parametry);
    const wartosci = { miasto, q: fraza, ...zmiany };
    for (const [klucz, wartosc] of Object.entries(wartosci)) {
      if (wartosc) {
        nowe.set(klucz, wartosc);
      } else {
        nowe.delete(klucz);
      }
    }
    // replace: filtrowanie nie powinno zapelniac historii "wstecz"
    setParametry(nowe, { replace: true });
  }

  function wybierzMiasto(klucz) {
    zapamietajMiasto(klucz);
    /*
     * Pusty wybor tez zapisujemy w adresie - jako "miasto=" - bo inaczej
     * po odswiezeniu strona wzielaby miasto z pamieci przegladarki.
     */
    const nowe = new URLSearchParams(parametry);
    nowe.set('miasto', klucz);
    setParametry(nowe, { replace: true });
  }

  const miasta = info?.cities ?? [];
  const wlaczone = info?.configured !== false;

  /* Wybrane miasto, ktorego nie ma juz na liscie (np. po imporcie) - i tak je pokazujemy. */
  const miastoPozaLista = miasto && !miasta.some((m) => m.key === miasto);

  return (
    <div className="wydarzenia-strona">
      <div className="d-flex align-items-baseline justify-content-between flex-wrap gap-2 mb-3">
        <h1 className="h4 mb-0 page-title">{t('events.title')}</h1>
        <span className="text-body-secondary small">{t('events.subtitle')}</span>
      </div>

      {user?.admin && info?.lastImport && (
        <div className={`small mb-3 ${info.lastImport.success ? 'text-body-secondary' : 'text-danger'}`}>
          {info.lastImport.success
            ? t('events.adminImport', {
              date: formatDateTime(info.lastImport.finishedAt, i18n.language),
              count: info.lastImport.events,
            })
            : t('events.adminImportFailed', { error: info.lastImport.error })}
        </div>
      )}

      <div className="wydarzenia-filtry mb-3">
        <div className="wydarzenia-szukaj">
          <IconSearch size={14} className="wydarzenia-szukaj-ikona" />
          <Form.Control
            type="search"
            value={wpisane}
            onChange={(e) => setWpisane(e.target.value)}
            placeholder={t('events.searchPlaceholder')}
            aria-label={t('events.searchLabel')}
            maxLength={100}
            enterKeyHint="search"
          />
        </div>

        <Form.Select
          value={miasto}
          onChange={(e) => wybierzMiasto(e.target.value)}
          aria-label={t('events.cityLabel')}
          className="wydarzenia-miasto"
        >
          <option value="">{t('events.allCities')}</option>
          {miastoPozaLista && (
            <option value={miasto}>{nazwaMiasta(miasto, miasto, i18n.language)}</option>
          )}
          {miasta.map((m) => (
            <option key={m.key} value={m.key}>
              {nazwaMiasta(m.key, m.name, i18n.language)} ({m.events})
            </option>
          ))}
        </Form.Select>
      </div>

      {blad && (
        <Alert variant="danger" className="d-flex align-items-center justify-content-between gap-2">
          <span>{blad}</span>
          <Button size="sm" variant="outline-danger" onClick={() => wczytaj(0)}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}

      {ladowanie && (
        <div className="text-center py-5 text-body-secondary">
          <Spinner animation="border" size="sm" className="me-2" />
          {t('common.loading')}
        </div>
      )}

      {!ladowanie && !blad && lista.length === 0 && (
        !wlaczone ? (
          <EmptyState
            icon={IconCalendar}
            title={t('events.notConfiguredTitle')}
            text={user?.admin ? t('events.notConfiguredAdmin') : t('events.notConfiguredText')}
          />
        ) : miasto || fraza ? (
          <EmptyState
            icon={IconSearch}
            title={t('events.emptyTitle')}
            text={t('events.emptyText')}
          />
        ) : (
          <EmptyState
            icon={IconCalendar}
            title={t('events.waitingTitle')}
            text={t('events.waitingText')}
          />
        )
      )}

      {!ladowanie && lista.length > 0 && (
        <div className="wydarzenia-lista">
          {lista.map((w, i) => (
            <Fragment key={w.id}>
              {/* Naglowek za kazdym razem, gdy zaczyna sie nowy dzien */}
              {(i === 0 || lista[i - 1].date !== w.date) && (
                <h2 className="wydarzenia-dzien">{naglowekDnia(w.date, i18n.language, t)}</h2>
              )}
              <EventCard wydarzenie={w} />
            </Fragment>
          ))}

          <DoladujWiecej
            etykieta={t('events.loadMore')}
            pokazano={lista.length}
            wszystkich={strona?.totalElements ?? lista.length}
            ostatnia={strona?.last ?? true}
            ladowanie={doladowanie}
            onClick={() => wczytaj((strona?.number ?? 0) + 1)}
          />
        </div>
      )}

      {/* Skad sa te dane - to katalog Ticketmastera, a nie nasz */}
      <p className="text-center text-body-secondary small mt-4 mb-0">{t('events.source')}</p>
    </div>
  );
}
