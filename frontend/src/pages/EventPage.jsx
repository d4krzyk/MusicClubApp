import { Fragment, useCallback, useEffect, useState } from 'react';
import Collapse from 'react-bootstrap/Collapse';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../api/client';
import * as posty from '../api/posty';
import {
  jedno, uczestnicy, zapisz, zrezygnuj,
} from '../api/wydarzenia';
import Avatar from '../components/Avatar';
import EmptyState from '../components/EmptyState';
import EventReasons from '../components/EventReasons';
import MapaPunktu, { adresTrasy } from '../components/mapa/MapaPunktu';
import OdOrganizatora from '../components/wydarzenie/OdOrganizatora';
import Wykonawcy from '../components/wydarzenie/Wykonawcy';
import EkipyWydarzenia from '../components/ekipa/EkipyWydarzenia';
import Post from '../components/Post';
import PostForm from '../components/PostForm';
import PostSkeleton from '../components/PostSkeleton';
import useLiveReactions from '../hooks/useLiveReactions';
import {
  IconArrowLeft, IconCalendar, IconCheckCircle, IconClock, IconCross, IconExternal, IconFriends, IconPin,
  IconPlus, IconStar, IconTicket,
} from '../components/Icons';
import {
  adresMapy, godzina, nazwaMiasta, pelnaData, plakietka,
} from '../utils/wydarzenia';

/** Opis dluzszy niz tyle znakow jest zwiniety - na telefonie to kilka ekranow tekstu. */
const DLUGI_OPIS = 600;

/** Ile postow pod wydarzeniem naraz - jak na tablicy. */
const POSTOW_NA_STRONE = 10;

/** Strona jednego wydarzenia: kiedy, gdzie, kto gra, opis i bilety. */
export default function EventPage() {
  const { t, i18n } = useTranslation();
  const { id } = useParams();
  const navigate = useNavigate();
  const lokalizacja = useLocation();

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
   * Po zapisie liczniki i moj stan przychodza od razu w odpowiedzi, ale lista
   * uczestnikow i powody (znajomi) - nie. Dociagamy cale wydarzenie po cichu,
   * bez kolka ladowania i bez zwijania opisu.
   */
  const odswiezPoCichu = useCallback(async () => {
    try {
      setWydarzenie(await jedno(id));
    } catch {
      // Zostaje to, co jest - stan zapisu i tak juz sie zmienil
    }
  }, [id]);

  function poZmianieUdzialu(stan) {
    setWydarzenie((w) => ({ ...w, participation: stan }));
    odswiezPoCichu();
  }

  /*
   * Przycisk jest podpisany "Wszystkie wydarzenia", wiec ma prowadzic do listy. Jedyny wyjatek: gdy przyszlismy z samej
   * listy - wtedy cofamy sie o krok, zeby wrocic do niej z tym samym miastem i fraza (siedza w adresie). Skad
   * przyszlismy, mowi karta wydarzenia (state.poprzednia).
   *
   * Wczesniej cofalismy sie zawsze, gdy byla jakakolwiek historia - i z ekipy, tablicy albo klanu przycisk
   * "Wszystkie wydarzenia" odsylal z powrotem tam, skad przyszlismy.
   */
  function wroc(e) {
    if (lokalizacja.state?.poprzednia === '/wydarzenia') {
      e.preventDefault();
      navigate(-1);
    }
  }

  return (
    <div className="wydarzenie-strona mx-auto tiles-in">
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
        <>
          <Szczegoly
            w={wydarzenie}
            rozwiniety={rozwiniety}
            onRozwin={() => setRozwiniety((r) => !r)}
            onZmianaUdzialu={poZmianieUdzialu}
            onOdswiez={odswiezPoCichu}
            t={t}
            jezyk={i18n.language}
          />
          <PostyWydarzenia idWydarzenia={wydarzenie.id} />
        </>
      )}
    </div>
  );
}

function Szczegoly({
  w, rozwiniety, onRozwin, onZmianaUdzialu, onOdswiez, t, jezyk,
}) {
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
        {w.withdrawn && <Alert variant="warning" className="py-2">{t('events.withdrawnInfo')}</Alert>}

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
              {w.distanceKm != null && (
                <span className="d-block small wydarzenie-odleglosc">
                  {w.distanceKm === 0 ? t('location.inYourCity') : t('location.kmAway', { km: w.distanceKm })}
                </span>
              )}
              <a href={adresMapy(w)} target="_blank" rel="noopener noreferrer" className="small">
                {t('events.map')} <IconExternal size={11} />
              </a>
            </span>
          </li>
        </ul>

        <Udzial w={w} onZmiana={onZmianaUdzialu} t={t} />

        {/* Ekipy zaraz pod "Biore udzial" - po to jest ta aplikacja: zeby nie isc na koncert samemu */}
        <EkipyWydarzenia w={w} onUdzial={onOdswiez} />

        {w.latitude != null && w.longitude != null && (
          <section className="mb-4 wydarzenie-mapa" aria-labelledby="wydarzenie-gdzie">
            <h2 id="wydarzenie-gdzie" className="h6 wydarzenie-sekcja">{t('events.whereTitle')}</h2>
            <MapaPunktu lat={w.latitude} lon={w.longitude} podpis={w.venueName} />
            <div className="mapa-akcje">
              <Button as="a" size="sm" variant="outline-primary" href={adresTrasy(w.latitude, w.longitude, w.venueName)}
                target="_blank" rel="noopener noreferrer">
                {t('map.directions')} <IconExternal size={11} />
              </Button>
              <Button as="a" size="sm" variant="outline-secondary" href={adresMapy(w)} target="_blank"
                rel="noopener noreferrer">
                {t('map.openInMaps')} <IconExternal size={11} />
              </Button>
            </div>
          </section>
        )}

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

        {w.reasons.length > 0 && (
          <section className="mb-4">
            <h2 className="h6 wydarzenie-sekcja">{t('events.whyTitle')}</h2>
            <EventReasons powody={w.reasons} zawijaj />
          </section>
        )}

        <Uczestnicy w={w} t={t} />

        {w.performers.length > 0 && (
          <section className="mb-4">
            <h2 className="h6 wydarzenie-sekcja">{t('events.lineup')}</h2>
            <Wykonawcy sklad={w.lineup?.length ? w.lineup : w.performers.map((name) => ({ name }))} />
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

        <OdOrganizatora w={w} />

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

        {/* Skad dane: glowne zrodlo i te, ktore uzupelnily braki (Bandsintown i Songkick wymagaja przypisania) */}
        <p className="text-body-secondary small mb-0 mt-3 wydarzenie-zrodla">
          {t('events.sourcesLabel')}{' '}
          {(w.sources?.length ? w.sources : [{ name: 'Ticketmaster' }]).map((s, i) => (
            <Fragment key={s.name}>
              {i > 0 && ' · '}
              {s.url
                ? <a href={s.url} target="_blank" rel="noopener noreferrer nofollow">{s.name}</a>
                : s.name}
            </Fragment>
          ))}
        </p>
      </div>
    </article>
  );
}

/**
 * "Zainteresowany" / "Biore udzial" / rezygnacja.
 *
 * Klikniecie w aktywny przycisk tez rezygnuje - tak dzialaja przelaczniki
 * w innych aplikacjach. Ale samo to byloby za malo widoczne, wiec pod spodem
 * stoi jeszcze wprost napisane "Rezygnuje z udzialu".
 */
function Udzial({ w, onZmiana, t }) {
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);
  const stan = w.participation;
  const moj = stan.myStatus;
  const zamkniete = w.past || w.withdrawn;

  async function wykonaj(akcja) {
    setZajety(true);
    setBlad(null);
    try {
      onZmiana(await akcja());
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  // Pierwszy zapis bez "ukryj" - serwer wezmie domyslne z ustawien prywatnosci
  const przelacz = (status) => wykonaj(() => (moj === status
    ? zrezygnuj(w.id)
    : zapisz(w.id, status, moj ? stan.hidden : undefined)));

  const liczniki = [
    stan.going > 0 && t('events.goingCount', { count: stan.going }),
    stan.interested > 0 && t('events.interestedCount', { count: stan.interested }),
  ].filter(Boolean);

  return (
    <section className="wydarzenie-udzial mb-4">
      {!zamkniete && (
        <div className="wydarzenie-udzial-przyciski">
          <Button
            variant={moj === 'INTERESTED' ? 'warning' : 'outline-secondary'}
            aria-pressed={moj === 'INTERESTED'}
            disabled={zajety}
            onClick={() => przelacz('INTERESTED')}
          >
            <IconStar className="me-2" />
            {t('events.interestedBtn')}
          </Button>
          <Button
            variant={moj === 'GOING' ? 'success' : 'outline-primary'}
            aria-pressed={moj === 'GOING'}
            disabled={zajety}
            onClick={() => przelacz('GOING')}
          >
            <IconCheckCircle className="me-2" />
            {moj === 'GOING' ? t('events.goingActive') : t('events.goingBtn')}
          </Button>
        </div>
      )}

      {moj === 'GOING' && !zamkniete && (
        <Form.Check
          type="switch"
          id={`ukryj-${w.id}`}
          className="mt-2 small"
          checked={stan.hidden}
          disabled={zajety}
          onChange={(e) => wykonaj(() => zapisz(w.id, 'GOING', e.target.checked))}
          label={(
            <>
              {t('events.hideMe')}
              <span className="d-block text-body-secondary">{t('events.hideMeHint')}</span>
            </>
          )}
        />
      )}

      {moj && (
        <Button
          variant="link"
          size="sm"
          className="px-0 text-danger"
          disabled={zajety}
          onClick={() => wykonaj(() => zrezygnuj(w.id))}
        >
          {moj === 'GOING' ? t('events.cancelGoing') : t('events.cancelInterested')}
        </Button>
      )}

      {liczniki.length > 0 && (
        <div className="small text-body-secondary mt-1">{liczniki.join(' · ')}</div>
      )}

      {blad && <Alert variant="danger" className="py-2 mt-2 mb-0">{blad}</Alert>}
    </section>
  );
}

/** Kto idzie. Siebie widze zawsze, ukrytych innych - tylko jako liczbe. */
function Uczestnicy({ w, t }) {
  const [wszyscy, setWszyscy] = useState(null);
  const [ladowanie, setLadowanie] = useState(false);
  const stan = w.participation;

  /* Po zmianie zapisu serwer przysyla nowa liste - rozwinieta stara jest juz nieaktualna. */
  useEffect(() => {
    setWszyscy(null);
  }, [w.attendees]);

  const jaUkryty = stan.myStatus === 'GOING' && stan.hidden;
  const widocznych = stan.going - stan.hiddenGoing + (jaUkryty ? 1 : 0);
  const ukrytychInnych = stan.hiddenGoing - (jaUkryty ? 1 : 0);
  const lista = wszyscy ?? w.attendees;

  async function pokazWszystkich() {
    setLadowanie(true);
    try {
      const zebrani = [];
      for (let strona = 0; ; strona++) {
        const dane = await uczestnicy(w.id, strona, 50);
        zebrani.push(...dane.content);
        if (dane.last || dane.content.length === 0) {
          break;
        }
      }
      setWszyscy(zebrani);
    } catch {
      // Zostaje pierwsza dwunastka - to i tak wiecej niz nic
    } finally {
      setLadowanie(false);
    }
  }

  return (
    <section className="mb-4">
      <h2 className="h6 wydarzenie-sekcja">
        {t('events.attendeesTitle')}
        {stan.going > 0 && <span className="ms-1">({stan.going})</span>}
      </h2>

      {stan.going === 0 && (
        <p className="small text-body-secondary mb-0">{t('events.attendeesNone')}</p>
      )}

      {lista.length > 0 && (
        <ul className="wydarzenie-uczestnicy list-unstyled">
          {lista.map((u) => (
            <li key={u.username}>
              <Link to={`/profil/${u.username}`} className="wydarzenie-uczestnik">
                <Avatar avatarUrl={u.avatarUrl} username={u.username} size={36} />
                <span className="wydarzenie-uczestnik-nazwa">{u.username}</span>
                {u.me && <span className="badge text-bg-secondary">{t('events.you')}</span>}
                {u.friend && <span className="badge wydarzenie-terminy">{t('events.friendBadge')}</span>}
                {u.me && u.hidden && (
                  <span className="wydarzenie-uczestnik-uwaga">{t('events.hiddenMe')}</span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}

      {ukrytychInnych > 0 && (
        <p className="small text-body-secondary mb-1">{t('events.attendeesHidden', { count: ukrytychInnych })}</p>
      )}

      {!wszyscy && lista.length < widocznych && (
        <Button variant="outline-secondary" size="sm" onClick={pokazWszystkich} disabled={ladowanie}>
          {ladowanie && <Spinner animation="border" size="sm" className="me-2" />}
          {t('events.attendeesAll', { count: widocznych })}
        </Button>
      )}
    </section>
  );
}

/**
 * Posty pod wydarzeniem - "szukam ekipy", "kto jedzie z Lodzi?". To zwykle
 * posty: te same reakcje, widocznosc i zglaszanie, sa tez na tablicy.
 */
function PostyWydarzenia({ idWydarzenia }) {
  const { t } = useTranslation();
  const [lista, setLista] = useState([]);
  const [strona, setStrona] = useState(0);
  const [ostatnia, setOstatnia] = useState(true);
  const [wszystkich, setWszystkich] = useState(0);
  const [ladowanie, setLadowanie] = useState(true);
  const [pierwsze, setPierwsze] = useState(true);
  const [blad, setBlad] = useState(null);
  const [komunikat, setKomunikat] = useState(null);
  const [formularz, setFormularz] = useState(false);

  const pobierz = useCallback(async (numer, dolacz) => {
    setLadowanie(true);
    setBlad(null);
    try {
      const dane = await posty.tablica({ strona: numer, rozmiar: POSTOW_NA_STRONE, wydarzenie: idWydarzenia });
      setLista((poprzednie) => (dolacz ? [...poprzednie, ...dane.content] : dane.content));
      setOstatnia(dane.last);
      setStrona(dane.number);
      setWszystkich(dane.totalElements);
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setLadowanie(false);
      setPierwsze(false);
    }
  }, [idWydarzenia]);

  useEffect(() => {
    // Inny termin tej samej serii to inne wydarzenie - i inne posty
    setLista([]);
    setPierwsze(true);
    setFormularz(false);
    setKomunikat(null);
    pobierz(0, false);
  }, [pobierz]);

  const liczniki = useCallback((stan) => {
    setLista((poprzednie) => poprzednie.map((p) => (stan[p.id] ? { ...p, reactions: stan[p.id] } : p)));
  }, []);
  useLiveReactions(lista, liczniki);

  const podmien = (nowy) => setLista((poprzednie) => poprzednie.map((p) => (p.id === nowy.id ? nowy : p)));

  function poDodaniu(nowy) {
    setLista((poprzednie) => [nowy, ...poprzednie]);
    setWszystkich((n) => n + 1);
    setKomunikat(t('posts.published'));
    setFormularz(false);
  }

  async function usun(id) {
    if (!window.confirm(t('common.confirmDelete'))) {
      return;
    }
    try {
      await posty.usun(id);
      setLista((poprzednie) => poprzednie.filter((p) => p.id !== id));
      setWszystkich((n) => Math.max(0, n - 1));
      setKomunikat(t('posts.deleted'));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }

  return (
    <section className="wydarzenie-posty mt-4" aria-labelledby="posty-wydarzenia">
      <div className="d-flex align-items-center justify-content-between gap-2 flex-wrap mb-3">
        <h2 id="posty-wydarzenia" className="h5 mb-0">
          {t('events.posts.title')}
          {wszystkich > 0 && <span className="text-body-secondary fw-normal ms-2">{wszystkich}</span>}
        </h2>
        <Button
          variant={formularz ? 'outline-secondary' : 'primary'}
          onClick={() => setFormularz((f) => !f)}
          aria-expanded={formularz}
          aria-controls="formularz-posta-wydarzenia"
        >
          {formularz ? <><IconCross /> {t('common.cancel')}</> : <><IconPlus /> {t('events.posts.write')}</>}
        </Button>
      </div>

      <Collapse in={formularz}>
        <div id="formularz-posta-wydarzenia">
          <PostForm
            onAdded={poDodaniu}
            eventId={idWydarzenia}
            idPola="tresc-pod-wydarzeniem"
            etykieta={t('events.posts.label')}
            podpowiedz={t('events.posts.placeholder')}
          />
        </div>
      </Collapse>

      {komunikat && (
        <Alert variant="success" dismissible onClose={() => setKomunikat(null)}>{komunikat}</Alert>
      )}
      {blad && <Alert variant="danger">{blad}</Alert>}

      {pierwsze && ladowanie && <PostSkeleton count={2} />}

      {lista.map((p, i) => (
        <Post
          key={p.id}
          post={p}
          index={i % POSTOW_NA_STRONE}
          onDelete={usun}
          onUpdate={(nowy) => { podmien(nowy); setKomunikat(t('posts.updated')); }}
          onReaction={podmien}
          bezWydarzenia
        />
      ))}

      {!ladowanie && !blad && lista.length === 0 && !formularz && (
        <EmptyState
          icon={IconFriends}
          title={t('events.posts.emptyTitle')}
          text={t('events.posts.emptyText')}
          action={(
            <Button variant="primary" onClick={() => setFormularz(true)}>
              <IconPlus /> {t('events.posts.write')}
            </Button>
          )}
        />
      )}

      {!ladowanie && !ostatnia && (
        <div className="text-center">
          <Button variant="outline-secondary" onClick={() => pobierz(strona + 1, true)}>
            {t('posts.loadMore')}
          </Button>
        </div>
      )}
    </section>
  );
}
