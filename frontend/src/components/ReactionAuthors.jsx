import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Modal from 'react-bootstrap/Modal';
import Spinner from 'react-bootstrap/Spinner';
import client from '../api/client';
import Avatar from './Avatar';
import { timeAgo } from '../utils/dates';

/** Ta sama kolejnosc co pod postem - od najbardziej pozytywnej. */
const ORDER = ['FIRE', 'MID', 'MEH'];

/**
 * Okienko "kto zareagowal na ten post".
 *
 * <p><b>Liste pobieramy dopiero po otwarciu.</b> Gdyby kazdy post na tablicy
 * ciagnal ja od razu, dwadziescia postow oznaczaloby dwadziescia dodatkowych
 * zapytan - po to, zeby pokazac cos, w co prawie nikt nie kliknie.</p>
 *
 * <p><b>Widzi to kazdy, nie tylko autor posta.</b> Reakcja jest w serwisie
 * spolecznosciowym gestem publicznym; ukrywanie jej przed pozostalymi
 * czytelnikami byloby zaskakujace, a i tak dalo by sie ja policzyc
 * z licznika obok emotki.</p>
 */
export default function ReactionAuthors({ postId, show, onHide }) {
  const { t, i18n } = useTranslation();

  const [people, setPeople] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!show) {
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(false);

    client.get(`/posts/${postId}/reactions`)
      .then(({ data }) => {
        // Odpowiedz moze wrocic po zamknieciu okienka - wtedy ja porzucamy
        if (!cancelled) setPeople(data);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [show, postId]);

  /*
   * Grupujemy po rodzaju reakcji. Jedna dluga lista wymagalaby czytania
   * emotki przy kazdym wierszu z osobna, zeby zobaczyc, ilu ludzi dalo
   * co - a to jest tu glowne pytanie.
   */
  const groups = ORDER
    .map((code) => [code, people.filter((p) => p.type === code)])
    .filter(([, list]) => list.length > 0);

  return (
    <Modal show={show} onHide={onHide} centered scrollable>
      <Modal.Header closeButton>
        <Modal.Title as="h2" className="h6 mb-0">
          {t('reactions.whoTitle')}
        </Modal.Title>
      </Modal.Header>

      <Modal.Body className="py-2">
        {loading && (
          <div className="text-center text-body-secondary small py-3">
            <Spinner animation="border" size="sm" className="me-2" />
            {t('common.loading')}
          </div>
        )}

        {error && <p className="text-danger small mb-0">{t('reactions.error')}</p>}

        {!loading && !error && people.length === 0 && (
          <p className="text-body-secondary small mb-0">{t('reactions.whoEmpty')}</p>
        )}

        {!loading && groups.map(([code, list]) => (
          <div key={code} className="mb-3">
            <div className="reaction-group-title">
              <span aria-hidden="true">{t(`reactions.emoji.${code}`)}</span>
              <span>{t(`reactions.${code}`)}</span>
              <span className="text-body-secondary">{list.length}</span>
            </div>

            {list.map((person) => (
              <Link
                key={person.username}
                to={`/profil/${encodeURIComponent(person.username)}`}
                className="reaction-person"
                onClick={onHide}
              >
                <Avatar
                  avatarUrl={person.avatarUrl}
                  username={person.username}
                  size={32}
                />
                <span className="fw-semibold text-truncate">{person.username}</span>
                <span className="text-body-secondary small ms-auto">
                  {timeAgo(person.createdAt, i18n.language)}
                </span>
              </Link>
            ))}
          </div>
        ))}
      </Modal.Body>
    </Modal>
  );
}
