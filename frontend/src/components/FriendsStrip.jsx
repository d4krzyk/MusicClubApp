import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import { znajomi } from '../api/profil';
import Avatar from './Avatar';
import EmptyState from './EmptyState';
import PeopleSkeleton from './PeopleSkeleton';
import PresenceDot from './PresenceDot';
import { IconFriends, IconPersonPlus } from './Icons';

/** Ilu znajomych mieści sie na jednym "ekranie" paska. */
const PAGE_SIZE = 6;

/** Poziomy pasek znajomych pod profilem. */
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
      const strona = await znajomi(username, pageNumber, PAGE_SIZE);

      setFriends(strona.content);
      setPage(strona.number);
      setPages(strona.totalPages);
      setTotal(strona.totalElements);
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
    // Szkielet o wysokosci gotowego paska - inaczej strona podskakuje
    // w chwili, gdy kolko z napisem ustepuje miejsca kafelkom
    return <PeopleSkeleton count={PAGE_SIZE} variant="friend" />;
  }

  if (total === 0) {
    /* Samo "Brak znajomych." konczy rozmowe w miejscu, w ktorym uzytkownik ma najwiecej pytan. */
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
            {/* Kropka obecnosci siedzi NA awatarze, a nie obok podpisu. */}
            <span className="friend-card-avatar">
              <Avatar
                avatarUrl={friend.avatarUrl}
                username={friend.username}
                size={64}
              />
              <PresenceDot presence={friend.presence} />
            </span>
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
