import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { coWasLaczy } from '../api/profil';
import Avatar from './Avatar';
import HorizontalStrip from './HorizontalStrip';

/** Co Was laczy - wspolni artysci, utwory, gatunki i znajomi. */
export default function CommonGround({ username, compact = false }) {
  const { t } = useTranslation();

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      setData(await coWasLaczy(username));
    } catch {
      // To dodatek do profilu, a nie profil - gdy padnie, reszta ma dzialac
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [username]);

  useEffect(() => {
    fetch();
  }, [fetch]);

  if (loading) {
    return (
      <div className="common-ground-skeleton" aria-hidden="true">
        <span className="skeleton skeleton-line" style={{ width: '45%' }} />
        <span className="skeleton skeleton-line" style={{ width: '70%' }} />
      </div>
    );
  }

  // Wlasny profil - nie ma czego z czym porownywac
  if (!data || data.self) {
    return null;
  }

  const { artists, tracks, genres, friends } = data;
  const nothing = artists.length === 0 && tracks.length === 0
    && genres.length === 0 && friends.length === 0;

  if (nothing) {
    /*
     * Brak czesci wspolnej to normalny wynik, a nie usterka - i wlasnie dlatego mowimy o nim
     * wprost.
     */
    return compact ? (
      <p className="text-body-secondary small mb-0">{t('common_ground.nothing')}</p>
    ) : (
      <section className="mb-4 common-ground">
        <h2 className="h5 mb-2">{t('common_ground.title')}</h2>
        <p className="text-body-secondary small mb-0">
          {t('common_ground.nothing')} {t('common_ground.nothingHint')}
        </p>
      </section>
    );
  }

  const body = (
    <>
      {genres.length > 0 && (
        <div className="mb-3">
          <h3 className="h6 text-body-secondary mb-2">{t('common_ground.genres')}</h3>
          {/*
            Gatunki jako plakietki, a nie lista - jest ich zwykle kilka, sa krotkie i nie maja
            wlasnej kolejnosci waznosci.
          */}
          <div className="genre-pills">
            {genres.map((genre) => (
              <span key={genre} className="genre-pill">{genre}</span>
            ))}
          </div>
        </div>
      )}

      {artists.length > 0 && (
        <Covers
          title={t('common_ground.artists')}
          items={artists.map((a) => ({
            key: a.externalId, image: a.imageUrl, caption: a.name,
          }))}
        />
      )}

      {tracks.length > 0 && (
        <Covers
          title={t('common_ground.tracks')}
          items={tracks.map((u) => ({
            key: u.externalId, image: u.imageUrl, caption: u.title, sub: u.artistName,
          }))}
        />
      )}

      {friends.length > 0 && (
        <div className="mb-1">
          <h3 className="h6 text-body-secondary mb-2">{t('common_ground.friends')}</h3>
          <div className="common-friends">
            {friends.map((person) => (
              <Link
                key={person.username}
                to={`/profil/${encodeURIComponent(person.username)}`}
                className="friend-card text-decoration-none text-body"
                title={t('profile.visit', { username: person.username })}
              >
                <Avatar avatarUrl={person.avatarUrl} username={person.username} size={48} />
                <span className="small text-truncate w-100 text-center mt-1">
                  {person.username}
                </span>
              </Link>
            ))}
          </div>
        </div>
      )}
    </>
  );

  if (compact) {
    return body;
  }

  return (
    <section className="mb-4 common-ground">
      <h2 className="h5 mb-2">{t('common_ground.title')}</h2>
      <p className="text-body-secondary small">{t('common_ground.why')}</p>
      {body}
    </section>
  );
}

/** Rzad okladek - ten sam uklad co przy ulubionych, zeby nie uczyc drugiego. */
function Covers({ title, items }) {
  return (
    <div className="mb-3">
      <h3 className="h6 text-body-secondary mb-2">{title}</h3>

      <HorizontalStrip itemWidth={116}>
        {items.map((item) => (
          <div key={item.key} className="favorite-card">
            {item.image ? (
              <img src={item.image} alt="" className="favorite-cover" loading="lazy" />
            ) : (
              <div className="favorite-cover cover-placeholder" aria-hidden="true">
                {(item.caption ?? '?').trim().charAt(0).toUpperCase()}
              </div>
            )}

            <div className="favorite-caption">
              <div className="text-truncate small">{item.caption}</div>
              {item.sub && (
                <div className="text-truncate text-body-secondary" style={{ fontSize: '.75rem' }}>
                  {item.sub}
                </div>
              )}
            </div>
          </div>
        ))}
      </HorizontalStrip>
    </div>
  );
}
