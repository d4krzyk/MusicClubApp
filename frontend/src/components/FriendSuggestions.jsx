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
import { IconPersonCheck, IconPersonPlus } from './Icons';

/** Ile osob pobieramy do paska propozycji. */
const ILE_PROPOZYCJI = 24;

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
      /*
       * Karta zmienia sie od razu, bez czekania na ponowne pobranie listy.
       * Przy pelnym odswiezeniu kolejnosc kart moglaby sie przesunac
       * dokladnie w chwili klikniecia - i palec wyladowalby na kims innym.
       */
      setSent((p) => ({ ...p, [username]: data.friendsNow ? 'friends' : 'sent' }));
      onChange?.();
    } catch (error) {
      const details = describeError(error);
      setError(details.message);
    }
  }

  if (loading) {
    /*
     * Szkielet, a nie kolko z napisem. Kolko zajmowalo jedna linijke,
     * a zaraz po nim wskakiwal rzad kafelkow wysokich na kilkanascie
     * razy tyle - cala strona podskakiwala w tym momencie.
     */
    return <PeopleSkeleton count={5} variant="suggestion" />;
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

              {/*
                Powody sa PRZYCISKIEM, a nie napisem. Liczba mowi, ze cos nas
                laczy, ale nie mowi CO - a to dopiero jest powod, zeby do kogos
                napisac. Klikniecie otwiera okienko z konkretami. Osoby bez
                zadnego dopasowania nie maja czego pokazac, wiec u nich zostaje
                zwykly napis.
              */}
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
        Okienko powstaje DOPIERO po kliknieciu (a nie jest ukryte przy kazdej
        karcie), wiec zapytanie porownujace listy ulubionych leci raz - dla
        tej jednej osoby, o ktora ktos naprawde zapytal.
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
