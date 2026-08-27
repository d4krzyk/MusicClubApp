import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Spinner from 'react-bootstrap/Spinner';
import client, { describeError } from '../api/client';
import Avatar from './Avatar';
import HorizontalStrip from './HorizontalStrip';
import { IconPersonCheck, IconPersonPlus } from './Icons';

/**
 * Proponowani znajomi - <b>cala spolecznosc, od najlepiej dopasowanych</b>.
 *
 * <p><b>Dlaczego wszyscy, a nie tylko dopasowani.</b> Bo aplikacja dla
 * kilkunastu osob, ktora po odsianiu "za malo podobnych" pokazuje pusta
 * strone, jest bezuzyteczna dokladnie wtedy, kiedy najbardziej potrzeba
 * w niej ludzi - na starcie. Pasek przewija sie od lewej: najpierw osoby,
 * z ktorymi cos nas laczy, dalej po prostu pozostali uzytkownicy.</p>
 *
 * <p><b>Na karcie widac POWOD dopasowania, a nie wynik punktowy.</b>
 * "2 wspolnych artystow" mowi wszystko; "14 punktow" nie mowi nic
 * i nie da sie tego sensownie wytlumaczyc.</p>
 *
 * <p>Znajomi zostaja na liscie, tylko z innym oznaczeniem. Gdyby znikali,
 * osoba z najlepszym dopasowaniem przepadalaby w chwili dodania jej do
 * znajomych - czyli dokladnie ta, ktora najlepiej tlumaczy, po co ta lista
 * w ogole jest.</p>
 */
export default function FriendSuggestions({ refresh, onChange }) {
  const { t } = useTranslation();

  const [people, setPeople] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [sent, setSent] = useState({});

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const response = await client.get('/friends/suggestions', { params: { limit: 24 } });
      setPeople(response.data);
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    fetch();
  }, [fetch, refresh]);

  async function invite(username) {
    setError(null);
    try {
      const { data } = await client.post('/friends/requests', { username: username });
      /*
       * Karta zmienia sie od razu, bez czekania na ponowne pobranie listy.
       * Przy pelnym odswiezeniu kolejnosc kart moglaby sie przesunac
       * dokladnie w chwili klikniecia - i palec wyladowalby na kims innym.
       */
      setSent((p) => ({ ...p, [username]: data.friendsNow ? 'friends' : 'sent' }));
      onChange?.();
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    }
  }

  if (loading) {
    return (
      <div className="text-body-secondary small py-2">
        <Spinner animation="border" size="sm" className="me-2" />
        {t('common.loading')}
      </div>
    );
  }

  if (people.length === 0) {
    return <p className="text-body-secondary small">{t('friends.noSuggestions')}</p>;
  }

  const anyoneMatched = people.some((o) => o.matched);

  return (
    <div>
      {error && <Alert variant="danger" className="py-2">{error}</Alert>}

      {/*
        Podpowiedz pokazujemy TYLKO wtedy, gdy nikt nie jest dopasowany.
        Przy dobrych dopasowaniach byloby to czepianie sie o nic, a tak -
        tlumaczy, dlaczego lista wyglada przypadkowo, i mowi, co z tym zrobic.
      */}
      {!anyoneMatched && (
        <Alert variant="info" className="py-2 small">
          {t('friends.betterMatchesHint')}{' '}
          <Link to="/profil">{t('friends.betterMatchesLink')}</Link>
        </Alert>
      )}

      <HorizontalStrip itemWidth={160}>
        {people.map((o) => {
          const state = sent[o.username]
            ?? (o.alreadyFriend ? 'friends' : null);

          return (
            <div key={o.username} className="suggestion-card">
              <Link
                to={`/profil/${o.username}`}
                className="text-decoration-none text-body d-block text-center"
              >
                <Avatar avatarUrl={o.avatarUrl} username={o.username} size={56} />
                <div className="fw-semibold text-truncate mt-1">{o.username}</div>
              </Link>

              <div className="match-reasons">
                {o.sharedArtists > 0 && (
                  <div>{t('friends.sharedArtists', { count: o.sharedArtists })}</div>
                )}
                {o.mutualFriends > 0 && (
                  <div>{t('friends.mutual', { count: o.mutualFriends })}</div>
                )}
                {o.sharedGenres > 0 && (
                  <div>{t('friends.sharedGenres', { count: o.sharedGenres })}</div>
                )}
                {!o.matched && <div>{t('friends.noCommonGround')}</div>}
              </div>

              {state === 'friends' && (
                <span className="text-success small d-block text-center">
                  <IconPersonCheck /> {t('friends.alreadyFriends')}
                </span>
              )}
              {state === 'sent' && (
                <span className="text-body-secondary small d-block text-center">
                  {t('friends.invited')}
                </span>
              )}
              {state === null && (
                <Button
                  size="sm"
                  variant="outline-primary"
                  className="w-100"
                  onClick={() => invite(o.username)}
                >
                  <IconPersonPlus /> {t('friends.invite')}
                </Button>
              )}
            </div>
          );
        })}
      </HorizontalStrip>
    </div>
  );
}
