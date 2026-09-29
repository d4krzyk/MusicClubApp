import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../api/client';
import { jedno } from '../api/wydarzenia';
import {
  IconArrowLeft, IconCalendar, IconClock, IconExternal, IconPin, IconTicket,
} from '../components/Icons';
import {
  adresMapy, godzina, nazwaMiasta, pelnaData, plakietka,
} from '../utils/wydarzenia';

/** Opis dluzszy niz tyle znakow jest zwiniety - na telefonie to kilka ekranow tekstu. */
const DLUGI_OPIS = 600;

/** Strona jednego wydarzenia: kiedy, gdzie, kto gra, opis i bilety. */
export default function EventPage() {
  const { t, i18n } = useTranslation();
  const { id } = useParams();
  const navigate = useNavigate();

  const [wydarzenie, setWydarzenie] = useState(null);
  const [ladowanie, setLadowanie] = useState(true);
  const [blad, setBlad] = useState(null);
  const [rozwiniety, setRozwiniety] = useState(false);

  const wczytaj = useCallback(async () => {
    setLadowanie(true);
    setBlad(null);
    setRozwiniety(false);
    try {
      setWydarzenie(await jedno(id));
    } catch (problem) {
      /*
       * Serwer przy 404 mowi "Nie znaleziono: event o identyfikatorze 42" -
       * poprawnie, ale nie po ludzku. Tu wiemy, o co chodzi, wiec mowimy wprost.
       */
      setBlad(problem.response?.status === 404
        ? t('events.notFound')
        : describeError(problem).message);
    } finally {
      setLadowanie(false);
    }
  }, [id, t]);

  useEffect(() => {
    wczytaj();
  }, [wczytaj]);

  /*
   * "Wstecz" zamiast sztywnego odnosnika do listy: wraca do listy z tym
   * samym miastem i fraza. Gdy ktos wszedl tu prosto z linku, historii
   * nie ma - wtedy idziemy na sama liste.
   */
  function wroc(e) {
    if (window.history.state?.idx > 0) {
      e.preventDefault();
      navigate(-1);
    }
  }

  return (
    <div className="wydarzenie-strona mx-auto">
      <Link to="/wydarzenia" onClick={wroc} className="btn btn-outline-secondary btn-sm mb-3">
        <IconArrowLeft /> {t('events.back')}
      </Link>

      {ladowanie && (
        <div className="text-center py-5 text-body-secondary">
          <Spinner animation="border" size="sm" className="me-2" />
          {t('common.loading')}
        </div>
      )}

      {blad && <Alert variant="warning">{blad}</Alert>}

      {!ladowanie && !blad && wydarzenie && (
        <Szczegoly
          w={wydarzenie}
          rozwiniety={rozwiniety}
          onRozwin={() => setRozwiniety((r) => !r)}
          t={t}
          jezyk={i18n.language}
        />
      )}
    </div>
  );
}

function Szczegoly({ w, rozwiniety, onRozwin, t, jezyk }) {
  const { dzien, miesiac } = plakietka(w.date, jezyk);
  const miasto = nazwaMiasta(w.cityKey, w.city, jezyk);
  const dlugi = (w.description?.length ?? 0) > DLUGI_OPIS;
  const opis = dlugi && !rozwiniety
    ? `${w.description.slice(0, DLUGI_OPIS).trimEnd()}…`
    : w.description;

  return (
    <article className="card overflow-hidden">
      <div className="wydarzenie-okladka">
        {w.imageUrl ? (
          <img src={w.imageUrl} alt="" />
        ) : (
          <span className="wydarzenie-zdjecie-zastepcze" aria-hidden="true">
            <IconCalendar size={48} />
          </span>
        )}
        <span className="wydarzenie-plakietka wydarzenie-plakietka-duza" aria-hidden="true">
          <span className="wydarzenie-plakietka-dzien">{dzien}</span>
          <span className="wydarzenie-plakietka-miesiac">{miesiac}</span>
        </span>
      </div>

      <div className="card-body">
        {w.past && <Alert variant="secondary" className="py-2">{t('events.past')}</Alert>}
        {w.status === 'CANCELLED' && <Alert variant="danger" className="py-2">{t('events.cancelledInfo')}</Alert>}
        {w.status === 'POSTPONED' && <Alert variant="warning" className="py-2">{t('events.postponedInfo')}</Alert>}
        {w.status === 'RESCHEDULED' && <Alert variant="info" className="py-2">{t('events.rescheduledInfo')}</Alert>}

        <h1 className="h4 mb-3 wydarzenie-tytul">{w.name}</h1>

        <ul className="wydarzenie-fakty list-unstyled">
          <li>
            <IconCalendar />
            <span className="wydarzenie-data">{pelnaData(w.date, jezyk)}</span>
          </li>
          <li>
            <IconClock />
            <span>{w.time ? godzina(w.time) : t('events.timeUnknown')}</span>
          </li>
          <li>
            <IconPin />
            <span>
              <span className="fw-semibold">{w.venueName}</span>
              {(w.address || miasto) && (
                <span className="d-block text-body-secondary">
                  {[w.address, miasto].filter(Boolean).join(', ')}
                </span>
              )}
              <a href={adresMapy(w)} target="_blank" rel="noopener noreferrer" className="small">
                {t('events.map')} <IconExternal size={11} />
              </a>
            </span>
          </li>
        </ul>

        {w.ticketUrl && (
          <Button
            as="a"
            href={w.ticketUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="wydarzenie-bilety mb-4"
          >
            <IconTicket className="me-2" />
            {t('events.tickets')}
            <IconExternal size={12} className="ms-2" />
          </Button>
        )}

        {w.performers.length > 0 && (
          <section className="mb-4">
            <h2 className="h6 wydarzenie-sekcja">{t('events.lineup')}</h2>
            <ul className="wydarzenie-sklad-lista list-unstyled">
              {w.performers.map((nazwa, i) => (
                <li key={`${nazwa}-${i}`} className={i === 0 ? 'is-pierwszy' : ''}>{nazwa}</li>
              ))}
            </ul>
          </section>
        )}

        {w.description && (
          <section className="mb-4">
            <h2 className="h6 wydarzenie-sekcja">{t('events.about')}</h2>
            {/* Zwykly tekst, nigdy HTML - to tresc z obcego serwisu */}
            <p className="wydarzenie-opis mb-1">{opis}</p>
            {dlugi && (
              <Button variant="link" size="sm" className="p-0" onClick={onRozwin}>
                {rozwiniety ? t('events.showLess') : t('events.showMore')}
              </Button>
            )}
          </section>
        )}

        {(w.genre || w.subGenre) && (
          <div className="mb-4 d-flex gap-1 flex-wrap">
            {[w.genre, w.subGenre]
              .filter((g, i, all) => g && all.indexOf(g) === i)
              .map((g) => <span key={g} className="badge wydarzenie-gatunek">{g}</span>)}
          </div>
        )}

        {w.otherDates.length > 0 && (
          <section className="mb-2">
            <h2 className="h6 wydarzenie-sekcja">{t('events.otherDates')}</h2>
            <ul className="wydarzenie-terminy-lista list-unstyled">
              {w.otherDates.map((d) => (
                <li key={d.id}>
                  <Link to={`/wydarzenia/${d.id}`} replace>
                    <span>{pelnaData(d.date, jezyk)}</span>
                    <span className="text-body-secondary">
                      {d.time ? godzina(d.time) : t('events.timeUnknown')}
                    </span>
                    {d.status !== 'SCHEDULED' && (
                      <span className={`badge wydarzenie-status wydarzenie-status-${d.status.toLowerCase()}`}>
                        {t(`events.status.${d.status}`)}
                      </span>
                    )}
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        )}

        <p className="text-body-secondary small mb-0 mt-3">{t('events.source')}</p>
      </div>
    </article>
  );
}
