import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Spinner from 'react-bootstrap/Spinner';
import client from '../api/client';
import Avatar from './Avatar';

/** Ilu znajomych mieści sie na jednym "ekranie" paska. */
const NA_STRONE = 6;

/**
 * Poziomy pasek znajomych pod profilem.
 *
 * <p><b>Dlaczego nie karuzela Bootstrapa?</b> Karuzela wymaga wszystkich
 * slajdow w dokumencie od razu, a my chcemy doczytywac kolejne osoby dopiero
 * po kliknieciu strzalki. Poza tym na telefonie pasek przewija sie palcem
 * sam z siebie, a karuzela wymusza klikanie.</p>
 *
 * <p><b>Strzalki = kolejna STRONA z serwera</b>, a nie przesuniecie tego,
 * co juz mamy. Dzieki temu profil z dwustoma znajomymi nie sciaga dwustu
 * kafelkow na wejsciu - wykorzystujemy stronicowanie, ktore backend i tak ma
 * (wymagania nr 3 i 5).</p>
 *
 * <p>Kolejnosc ustala serwer: od osob najbardziej powiazanych z ogladajacym.
 * Dzis liczy sie to po wspolnych znajomych; gdy dojda artysci ze Spotify,
 * zmieni sie samo zapytanie w bazie, a ten komponent zostanie bez zmian.</p>
 */
export default function PasekZnajomych({ username, odswiez }) {
  const { t } = useTranslation();

  const [znajomi, setZnajomi] = useState([]);
  const [strona, setStrona] = useState(0);
  const [stron, setStron] = useState(0);
  const [ile, setIle] = useState(0);
  const [ladowanie, setLadowanie] = useState(true);

  const pobierz = useCallback(async (numer) => {
    setLadowanie(true);
    try {
      const odpowiedz = await client.get(
        `/profiles/${encodeURIComponent(username)}/friends`,
        { params: { page: numer, size: NA_STRONE } });

      setZnajomi(odpowiedz.data.content);
      setStrona(odpowiedz.data.number);
      setStron(odpowiedz.data.totalPages);
      setIle(odpowiedz.data.totalElements);
    } catch {
      // Pasek znajomych to dodatek - gdy padnie, profil ma dzialac dalej
      setZnajomi([]);
    } finally {
      setLadowanie(false);
    }
  }, [username]);

  useEffect(() => {
    pobierz(0);
  }, [pobierz, odswiez]);

  if (ladowanie && znajomi.length === 0) {
    return (
      <div className="text-body-secondary small py-2">
        <Spinner animation="border" size="sm" className="me-2" />
        {t('common.loading')}
      </div>
    );
  }

  if (ile === 0) {
    return <p className="text-body-secondary small">{t('friends.none')}</p>;
  }

  // Pierwszy i ostatni element na tej stronie - do napisu "1-6 z 23"
  const od = strona * NA_STRONE + 1;
  const doKtorego = od + znajomi.length - 1;

  return (
    <div>
      <div className="pasek-znajomych">
        {znajomi.map((znajomy) => (
          <Link
            key={znajomy.username}
            to={`/profil/${znajomy.username}`}
            className="kafelek-znajomego text-decoration-none text-body"
            title={t('profile.visit', { username: znajomy.username })}
          >
            <Avatar
              avatarUrl={znajomy.avatarUrl}
              username={znajomy.username}
              rozmiar={64}
            />
            <div className="small fw-semibold text-truncate w-100 text-center mt-1">
              {znajomy.username}
            </div>
            {znajomy.mutualFriends > 0 && (
              <div className="text-body-secondary text-center" style={{ fontSize: '0.75rem' }}>
                {t('friends.mutual', { count: znajomy.mutualFriends })}
              </div>
            )}
          </Link>
        ))}
      </div>

      {/* Sterowanie POD paskiem - tak samo jak przy galerii zdjec w postach */}
      {stron > 1 && (
        <div className="d-flex align-items-center justify-content-center gap-3 mt-2">
          <Button
            variant="outline-secondary"
            size="sm"
            disabled={strona === 0 || ladowanie}
            onClick={() => pobierz(strona - 1)}
            aria-label={t('users.previous')}
          >
            ‹
          </Button>

          <span className="text-body-secondary small">
            {od}–{doKtorego} {t('common.of')} {ile}
          </span>

          <Button
            variant="outline-secondary"
            size="sm"
            disabled={strona >= stron - 1 || ladowanie}
            onClick={() => pobierz(strona + 1)}
            aria-label={t('users.next')}
          >
            ›
          </Button>
        </div>
      )}
    </div>
  );
}
