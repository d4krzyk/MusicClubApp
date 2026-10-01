import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Spinner from 'react-bootstrap/Spinner';
import { describeError } from '../api/client';
import * as klany from '../api/klany';
import ClanBadge from '../components/ClanBadge';
import DoladujWiecej from '../components/DoladujWiecej';
import EmptyState from '../components/EmptyState';
import { IconClan, IconFriends, IconPin, IconSearch } from '../components/Icons';
import Aktywnosc from '../components/klan/Aktywnosc';
import { formatDate } from '../utils/dates';

const SORTOWANIA = ['MATCH', 'MEMBERS', 'ACTIVE', 'NEWEST', 'OLDEST', 'NAME'];
const ROZMIAR_STRONY = 12;
const ZWLOKA_SZUKANIA = 350;

/**
 * Przegladarka klanow: szukanie, filtr gatunku i miasta, sortowanie (dopasowanie do mojego gustu,
 * liczba osob, aktywnosc, data zalozenia, nazwa). Karta klanu pokazuje tyle, ile potrzeba do
 * rozeznania sie "co to za klan" - bez czatu, postow i bez wskazywania osob.
 * Filtry siedza w adresie strony, wiec "wstecz" z klanu wraca do tej samej listy.
 */
export default function KlanyPage() {
  const { t, i18n } = useTranslation();
  const [parametry, setParametry] = useSearchParams();
  const fraza = parametry.get('q') ?? '';
  const gatunek = parametry.get('genre') ?? '';
  const miasto = parametry.get('city') ?? '';
  const mozna = parametry.get('joinable') === '1';
  const sort = SORTOWANIA.includes(parametry.get('sort')) ? parametry.get('sort') : 'MATCH';

  const [wpisaneFraza, setWpisaneFraza] = useState(fraza);
  const [wpisaneMiasto, setWpisaneMiasto] = useState(miasto);
  const [lista, setLista] = useState([]);
  const [strona, setStrona] = useState(null);
  const [gatunki, setGatunki] = useState([]);
  const [ladowanie, setLadowanie] = useState(true);
  const [doladowanie, setDoladowanie] = useState(false);
  const [blad, setBlad] = useState(null);
  // Odpowiedz na starsze zapytanie moze przyjsc po nowszym - ignorujemy ja
  const ostatnie = useRef(0);

  const wczytaj = useCallback(async (numerStrony) => {
    const numer = ++ostatnie.current;
    if (numerStrony === 0) {
      setLadowanie(true);
    } else {
      setDoladowanie(true);
    }
    setBlad(null);
    try {
      const dane = await klany.przegladarka({
        q: fraza, genre: gatunek, city: miasto, joinable: mozna, sort, page: numerStrony, size: ROZMIAR_STRONY,
      });
      if (numer !== ostatnie.current) {
        return;
      }
      setLista((obecne) => (numerStrony === 0 ? dane.content : [...obecne, ...dane.content]));
      setStrona(dane);
      setGatunki(dane.genres);
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
  }, [fraza, gatunek, miasto, mozna, sort]);

  useEffect(() => { wczytaj(0); }, [wczytaj]);

  // "wstecz" w przegladarce zmienia adres - pola tekstowe maja pokazac to, co jest w adresie
  useEffect(() => { setWpisaneFraza((o) => (o.trim() === fraza ? o : fraza)); }, [fraza]);
  useEffect(() => { setWpisaneMiasto((o) => (o.trim() === miasto ? o : miasto)); }, [miasto]);

  function zmien(zmiany) {
    const nowe = new URLSearchParams(parametry);
    for (const [klucz, wartosc] of Object.entries(zmiany)) {
      if (wartosc) {
        nowe.set(klucz, wartosc);
      } else {
        nowe.delete(klucz);
      }
    }
    // filtrowanie nie ma zapelniac historii "wstecz"
    setParametry(nowe, { replace: true });
  }

  // Tekst z pol trafia do adresu po chwili bez pisania
  useEffect(() => {
    if (wpisaneFraza.trim() === fraza && wpisaneMiasto.trim() === miasto) {
      return undefined;
    }
    const zegar = setTimeout(() => zmien({ q: wpisaneFraza.trim(), city: wpisaneMiasto.trim() }), ZWLOKA_SZUKANIA);
    return () => clearTimeout(zegar);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wpisaneFraza, wpisaneMiasto]);

  const filtrowane = Boolean(fraza || gatunek || miasto || mozna);

  function wyczysc() {
    setWpisaneFraza('');
    setWpisaneMiasto('');
    setParametry(new URLSearchParams(), { replace: true });
  }

  return (
    <div className="klany-przegladarka mx-auto">
      <div className="d-flex align-items-center flex-wrap gap-2 mb-1">
        <h1 className="h4 mb-0 me-auto">{t('clans.directory.title')}</h1>
        <Link to="/klan" className="btn btn-outline-secondary btn-sm">{t('clans.directory.myClan')}</Link>
      </div>
      <p className="text-body-secondary small">{t('clans.directory.intro')}</p>

      <div className="klany-filtry mb-3">
        <div className="wydarzenia-szukaj klany-szukaj">
          <IconSearch size={14} className="wydarzenia-szukaj-ikona" />
          <Form.Control type="search" value={wpisaneFraza} maxLength={100} enterKeyHint="search"
            placeholder={t('clans.directory.searchPlaceholder')} aria-label={t('clans.directory.searchLabel')}
            onChange={(e) => setWpisaneFraza(e.target.value)} />
        </div>

        <Form.Select value={gatunek} aria-label={t('clans.directory.genreLabel')}
          onChange={(e) => zmien({ genre: e.target.value })}>
          <option value="">{t('clans.directory.allGenres')}</option>
          {/* wybrany gatunek zostaje na liscie, nawet gdy po zawezeniu filtrow nie ma go w opcjach */}
          {gatunek && !gatunki.some((g) => g.name === gatunek) && <option value={gatunek}>{gatunek}</option>}
          {gatunki.map((g) => <option key={g.name} value={g.name}>{g.name} ({g.clans})</option>)}
        </Form.Select>

        <Form.Control value={wpisaneMiasto} maxLength={60} placeholder={t('clans.directory.cityPlaceholder')}
          aria-label={t('clans.directory.cityLabel')} onChange={(e) => setWpisaneMiasto(e.target.value)} />

        <Form.Select value={sort} className="klany-sortuj" aria-label={t('clans.directory.sortLabel')}
          onChange={(e) => zmien({ sort: e.target.value === 'MATCH' ? '' : e.target.value })}>
          {SORTOWANIA.map((s) => <option key={s} value={s}>{t(`clans.directory.sort.${s}`)}</option>)}
        </Form.Select>

        <Form.Check type="switch" id="klany-do-dolaczenia" className="klany-filtr-prosby"
          label={t('clans.directory.joinable')} checked={mozna}
          onChange={(e) => zmien({ joinable: e.target.checked ? '1' : '' })} />
      </div>

      {blad && (
        <Alert variant="danger" className="d-flex align-items-center justify-content-between gap-2">
          <span>{blad}</span>
          <Button size="sm" variant="outline-danger" onClick={() => wczytaj(0)}>{t('common.retry')}</Button>
        </Alert>
      )}

      {ladowanie && (
        <div className="text-center py-5 text-body-secondary">
          <Spinner animation="border" size="sm" className="me-2" />{t('common.loading')}
        </div>
      )}

      {!ladowanie && !blad && lista.length === 0 && (
        <EmptyState
          icon={filtrowane ? IconSearch : IconClan}
          title={filtrowane ? t('clans.directory.emptyFilteredTitle') : t('clans.directory.emptyTitle')}
          text={filtrowane ? t('clans.directory.emptyFilteredText') : t('clans.directory.emptyText')}
          action={filtrowane
            ? <Button size="sm" variant="outline-primary" onClick={wyczysc}>{t('clans.directory.clear')}</Button>
            : <Link to="/klan" className="btn btn-primary btn-sm">{t('clans.directory.createOwn')}</Link>}
        />
      )}

      {!ladowanie && lista.length > 0 && (
        <>
          <ul className="klany-lista list-unstyled">
            {lista.map((k) => <li key={k.id}><KartaKlanu klan={k} jezyk={i18n.language} /></li>)}
          </ul>
          <DoladujWiecej
            etykieta={t('clans.directory.loadMore')}
            pokazano={lista.length}
            wszystkich={strona?.total ?? lista.length}
            ostatnia={strona?.last ?? true}
            ladowanie={doladowanie}
            onClick={() => wczytaj((strona?.page ?? 0) + 1)}
          />
        </>
      )}
    </div>
  );
}

/** Jedna karta klanu na liscie. Cala jest linkiem do strony klanu (rozciagniety link na nazwie). */
function KartaKlanu({ klan, jezyk }) {
  const { t } = useTranslation();
  const wlasneGatunki = new Set(klan.genres);
  // Gatunki z gustu czlonkow, ktorych klan nie wpisal sam - zeby sie nie powtarzaly
  const zGustu = klan.topGenres.filter((g) => !wlasneGatunki.has(g));

  let nabor;
  if (klan.invited) {
    nabor = <span className="klan-pigulka is-zaproszenie">{t('clans.directory.invited')}</span>;
  } else if (klan.myRequestStatus === 'PENDING') {
    nabor = <span className="klan-pigulka is-oczekuje">{t('clans.directory.requested')}</span>;
  } else if (klan.full) {
    nabor = <span className="klan-pigulka">{t('clans.directory.full')}</span>;
  } else {
    nabor = <span className={`klan-pigulka${klan.joinPolicy === 'REQUESTS' ? ' is-otwarty' : ''}`}>
      {t(`clans.card.policyShort.${klan.joinPolicy}`)}
    </span>;
  }

  return (
    <article className="klan-karta" style={{ '--klan': klan.colorHex }}>
      <div className="klan-karta-gora">
        <div className="klan-karta-ikona">
          {klan.iconUrl ? <img src={klan.iconUrl} alt="" /> : <IconClan size={22} />}
        </div>
        <div className="klan-karta-tytul">
          <h2 className="h6 mb-0 klan-karta-nazwa">
            <Link to={`/klany/${klan.id}`} className="klan-karta-link">{klan.name}</Link>
          </h2>
          <ClanBadge clan={klan} link={false} />
        </div>
      </div>

      {klan.motto && <p className="klan-haslo klan-karta-haslo">„{klan.motto}"</p>}
      {!klan.motto && klan.description && <p className="klan-karta-opis">{klan.description}</p>}

      <ul className="klan-fakty list-unstyled">
        <li><IconFriends size={14} /> {klan.memberCount}/{klan.maxMembers}</li>
        {klan.city && <li><IconPin size={14} /> <span className="klan-fakt-tekst">{klan.city}</span></li>}
        {klan.activityLevel && <li><Aktywnosc poziom={klan.activityLevel} /></li>}
        <li className="klan-fakt-data">{t('clans.directory.since', { date: formatDate(klan.createdAt, jezyk) })}</li>
      </ul>

      {(klan.genres.length > 0 || zGustu.length > 0) && (
        <div className="klan-gust-gatunki">
          {klan.genres.map((g) => (
            <span key={g} className="klan-gatunek klan-gatunek-wybrany" title={t('clans.directory.declaredGenre')}>{g}</span>
          ))}
          {zGustu.map((g) => (
            <span key={g} className="klan-gatunek" title={t('clans.directory.tasteGenre')}>{g}</span>
          ))}
        </div>
      )}

      {klan.match > 0 && (
        <p className="klan-dopasowanie">
          {klan.sharedGenres.length > 0
            ? t('clans.directory.matchGenres', { genres: klan.sharedGenres.join(', ') })
            : t('clans.directory.matchArtists')}
        </p>
      )}

      <div className="klan-karta-stopka">{nabor}</div>
    </article>
  );
}
