import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Modal from 'react-bootstrap/Modal';
import { describeError } from '../api/client';
import { propozycje, zapros } from '../api/znajomi';
import Avatar from './Avatar';
import CommonGround from './CommonGround';
import HorizontalStrip from './HorizontalStrip';
import PeopleSkeleton from './PeopleSkeleton';
import PodpowiedzMiasta from './PodpowiedzMiasta';
import { IconPersonCheck, IconPersonPlus, IconPin } from './Icons';

/** Ile osob pobieramy do paska propozycji. */
const ILE_PROPOZYCJI = 24;

/** Proponowani znajomi - cala spolecznosc, od najlepiej dopasowanych. */
export default function FriendSuggestions({ refresh, onChange }) {
  const { t } = useTranslation();

  const [people, setPeople] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [sent, setSent] = useState({});

  /* Login osoby, dla ktorej otwarto okienko "co Was laczy" (null = zamkniete) */
  const [showCommon, setShowCommon] = useState(null);

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      setPeople(await propozycje(ILE_PROPOZYCJI));
    } catch (error) {
      const details = describeError(error);
      setError(details.message);
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
      const data = await zapros(username);
      /* Karta zmienia sie od razu, bez czekania na ponowne pobranie listy. */
      setSent((p) => ({ ...p, [username]: data.friendsNow ? 'friends' : 'sent' }));
      onChange?.();
    } catch (error) {
      const details = describeError(error);
      setError(details.message);
    }
  }

  if (loading) {
    /* Szkielet, a nie kolko z napisem. */
    return <PeopleSkeleton count={5} variant="suggestion" />;
  }

  if (people.length === 0) {
    return <p className="text-body-secondary small">{t('friends.noSuggestions')}</p>;
  }

  const anyoneMatched = people.some((o) => o.matched);

  return (
    <div>
      {error && <Alert variant="danger" className="py-2">{error}</Alert>}

      <PodpowiedzMiasta tekst={t('location.friendsNudge')} />

      {/* Podpowiedz pokazujemy TYLKO wtedy, gdy nikt nie jest dopasowany. */}
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

              {/* Skad: tylko to, co ta osoba sama pokazuje; bez dokladnej odleglosci */}
              {(o.proximity || o.city) && (
                <div className={`suggestion-miasto${o.proximity ? ' is-blisko' : ''}`}>
                  <IconPin size={11} />{' '}
                  {o.proximity === 'SAME_CITY' && t('location.sameCity')}
                  {o.proximity === 'NEARBY' && t('location.nearby', { city: o.city })}
                  {!o.proximity && o.city}
                </div>
              )}

              {/* Powody sa PRZYCISKIEM, a nie napisem. */}
              {o.matched ? (
                <button
                  type="button"
                  className="match-reasons match-reasons-button"
                  onClick={() => setShowCommon(o.username)}
                  title={t('common_ground.show')}
                >
                  {o.sharedArtists > 0 && (
                    <span>{t('friends.sharedArtists', { count: o.sharedArtists })}</span>
                  )}
                  {o.mutualFriends > 0 && (
                    <span>{t('friends.mutual', { count: o.mutualFriends })}</span>
                  )}
                  {o.sharedGenres > 0 && (
                    <span>{t('friends.sharedGenres', { count: o.sharedGenres })}</span>
                  )}
                  <span className="match-reasons-more">{t('common_ground.show')}</span>
                </button>
              ) : (
                <div className="match-reasons">{t('friends.noCommonGround')}</div>
              )}

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

      {/*
        Okienko powstaje DOPIERO po kliknieciu (a nie jest ukryte przy kazdej karcie), wiec
        zapytanie porownujace listy ulubionych leci raz - dla tej jednej osoby, o ktora ktos
        naprawde zapytal.
      */}
      <Modal show={Boolean(showCommon)} onHide={() => setShowCommon(null)} centered scrollable>
        <Modal.Header closeButton>
          <Modal.Title as="h2" className="h6 mb-0">
            {t('common_ground.withPerson', { username: showCommon })}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {showCommon && <CommonGround username={showCommon} compact />}
        </Modal.Body>
      </Modal>
    </div>
  );
}
