import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Spinner from 'react-bootstrap/Spinner';
import client from '../api/client';
import Avatar from './Avatar';
import EmptyState from './EmptyState';
import { IconFriends, IconPersonPlus } from './Icons';

/** Ilu znajomych mieści sie na jednym "ekranie" paska. */
const PAGE_SIZE = 6;

/**
 * Poziomy pasek znajomych pod profilem.
 *
 * <p><b>Dlaczego nie karuzela Bootstrapa?</b> Karuzela wymaga wszystkich
 * slajdow w dokumencie from razu, a my chcemy doczytywac kolejne osoby dopiero
 * po kliknieciu strzalki. Poza tym na telefonie pasek przewija sie palcem
 * sam z siebie, a karuzela wymusza klikanie.</p>
 *
 * <p><b>Strzalki = kolejna STRONA z serwera</b>, a nie przesuniecie tego,
 * co juz mamy. Dzieki temu profil z dwustoma znajomymi nie sciaga dwustu
 * kafelkow na wejsciu - wykorzystujemy stronicowanie, ktore backend i tak ma
 * (wymagania nr 3 i 5).</p>
 *
 * <p>Kolejnosc ustala serwer: from osob najbardziej powiazanych z ogladajacym.
 * Dzis liczy sie to po wspolnych znajomych; gdy dojda artysci ze Spotify,
 * zmieni sie samo zapytanie w bazie, a ten komponent zostanie bez zmian.</p>
 */
export default function FriendsStrip({ username, refresh, self = false }) {
  const { t } = useTranslation();

  const [friends, setFriends] = useState([]);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  const fetch = useCallback(async (pageNumber) => {
    setLoading(true);
    try {
      const response = await client.get(
        `/profiles/${encodeURIComponent(username)}/friends`,
        { params: { page: pageNumber, size: PAGE_SIZE } });

      setFriends(response.data.content);
      setPage(response.data.number);
      setPages(response.data.totalPages);
      setTotal(response.data.totalElements);
    } catch {
      // Pasek znajomych to dodatek - gdy padnie, profil ma dzialac dalej
      setFriends([]);
    } finally {
      setLoading(false);
    }
  }, [username]);

  useEffect(() => {
    fetch(0);
  }, [fetch, refresh]);

  if (loading && friends.length === 0) {
    return (
      <div className="text-body-secondary small py-2">
        <Spinner animation="border" size="sm" className="me-2" />
        {t('common.loading')}
      </div>
    );
  }

  if (total === 0) {
    /*
     * Samo "Brak znajomych." konczy rozmowe w miejscu, w ktorym uzytkownik
     * ma najwiecej pytan. Na WLASNYM profilu mowimy wiec, co z tym zrobic,
     * i dajemy jedno klikniecie; na cudzym wystarcza zdanie - tam nie ma
     * czego zaproponowac.
     */
    return self ? (
      <EmptyState
        icon={IconFriends}
        title={t('friends.noneSelf')}
        text={t('friends.noneSelfHint')}
        action={
          <Link to="/znajomi" className="btn btn-primary">
            <IconPersonPlus /> {t('friends.findPeople')}
          </Link>
        }
      />
    ) : (
      <p className="text-body-secondary small">{t('friends.none')}</p>
    );
  }

  // Pierwszy i ostatni element na tej stronie - do napisu "1-6 z 23"
  const from = page * PAGE_SIZE + 1;
  const until = from + friends.length - 1;

  return (
    <div>
      <div className="friends-strip">
        {friends.map((friend) => (
          <Link
            key={friend.username}
            to={`/profil/${friend.username}`}
            className="friend-card text-decoration-none text-body"
            title={t('profile.visit', { username: friend.username })}
          >
            <Avatar
              avatarUrl={friend.avatarUrl}
              username={friend.username}
              size={64}
            />
            <div className="small fw-semibold text-truncate w-100 text-center mt-1">
              {friend.username}
            </div>
            {friend.mutualFriends > 0 && (
              <div className="text-body-secondary text-center" style={{ fontSize: '0.75rem' }}>
                {t('friends.mutual', { count: friend.mutualFriends })}
              </div>
            )}
          </Link>
        ))}
      </div>

      {/* Sterowanie POD paskiem - tak samo jak przy galerii zdjec w postach */}
      {pages > 1 && (
        <div className="d-flex align-items-center justify-content-center gap-3 mt-2">
          <Button
            variant="outline-secondary"
            size="sm"
            disabled={page === 0 || loading}
            onClick={() => fetch(page - 1)}
            aria-label={t('users.previous')}
          >
            ‹
          </Button>

          <span className="text-body-secondary small">
            {from}–{until} {t('common.of')} {total}
          </span>

          <Button
            variant="outline-secondary"
            size="sm"
            disabled={page >= pages - 1 || loading}
            onClick={() => fetch(page + 1)}
            aria-label={t('users.next')}
          >
            ›
          </Button>
        </div>
      )}
    </div>
  );
}
