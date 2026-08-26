import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Spinner from 'react-bootstrap/Spinner';
import client, { describeError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import Avatar from '../components/Avatar';
import FriendsStrip from '../components/FriendsStrip';
import FriendSuggestions from '../components/FriendSuggestions';
import { IconCross, IconPersonCheck, IconPersonPlus } from '../components/Icons';
import { formatDate } from '../utils/dates';

/**
 * Ekran "Znajomi": zaproszenia oczekujace, wyszukiwarka i wlasna lista.
 *
 * <p>Obie listy zaproszen (do mnie i ode mnie) przychodza JEDNYM zapytaniem.
 * Przy dwoch osobnych endpointach po kazdej akcji trzeba by odswiezac dwie
 * rzeczy i jedna z nich potrafilaby przez chwile pokazywac stan sprzed
 * klikniecia.</p>
 */
export default function FriendsPage() {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();

  const [invitations, setInvitations] = useState({ incoming: [], outgoing: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [refreshFriends, setRefreshFriends] = useState(0);

  const [searched, setSearched] = useState('');
  const [wysylanie, setWysylanie] = useState(false);

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const response = await client.get('/friends/requests');
      setInvitations(response.data);
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    fetch();
  }, [fetch]);

  async function run(akcja, successMessage) {
    setError(null);
    setMessage(null);
    try {
      await akcja();
      await fetch();
      setRefreshFriends((n) => n + 1);
      setMessage(successMessage);
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    }
  }

  async function invite(e) {
    e.preventDefault();
    if (!searched.trim()) {
      return;
    }
    setWysylanie(true);
    try {
      const { data } = await client.post('/friends/requests', { username: searched.trim() });
      await fetch();
      setRefreshFriends((n) => n + 1);
      setSearched('');
      /*
       * Serwer mowi, czy znajomosc powstala OD RAZU - dzieje sie tak, gdy
       * ta osoba wczesniej zaprosila nas. Wtedy komunikat "zaproszenie
       * wyslane" bylby mylacy.
       */
      setMessage(data.friendsNow ? t('friends.nowFriends') : t('friends.invited'));
      setError(null);
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
      setMessage(null);
    } finally {
      setWysylanie(false);
    }
  }

  function row(z, buttons) {
    return (
      <div key={z.id} className="d-flex align-items-center gap-2 py-2 border-bottom">
        <Link to={`/profil/${z.username}`} className="d-flex align-items-center gap-2 text-decoration-none text-body flex-grow-1">
          <Avatar avatarUrl={z.avatarUrl} username={z.username} size={40} />
          <div>
            <div className="fw-semibold">{z.username}</div>
            <div className="text-body-secondary small">
              {formatDate(z.createdAt, i18n.language)}
            </div>
          </div>
        </Link>
        <div className="d-flex gap-2">{buttons}</div>
      </div>
    );
  }

  return (
    <Row className="justify-content-center">
      <Col lg={8}>
        <h1 className="h4 mb-3">{t('friends.title')}</h1>

        {message && (
          <Alert variant="success" dismissible onClose={() => setMessage(null)}>
            {message}
          </Alert>
        )}
        {error && <Alert variant="danger" dismissible onClose={() => setError(null)}>{error}</Alert>}

        {/* Zapraszanie po loginie */}
        <Card className="mb-4">
          <Card.Body>
            <Form onSubmit={invite}>
              <Form.Label htmlFor="szukany">{t('friends.inviteByName')}</Form.Label>
              <div className="d-flex gap-2">
                <Form.Control
                  id="szukany"
                  value={searched}
                  onChange={(e) => setSearched(e.target.value)}
                  placeholder={t('friends.usernamePlaceholder')}
                />
                <Button type="submit" disabled={wysylanie || !searched.trim()}>
                  <IconPersonPlus /> {t('friends.invite')}
                </Button>
              </div>
              <Form.Text>{t('friends.inviteHint')}</Form.Text>
            </Form>
          </Card.Body>
        </Card>

        {loading && (
          <div className="text-center py-3 text-body-secondary">
            <Spinner animation="border" size="sm" className="me-2" />
            {t('common.loading')}
          </div>
        )}

        {/*
          Propozycje stoja WYZEJ niz zaproszenia. Zaproszenia ogląda sie
          wtedy, gdy juz sa; propozycje sa po to, zeby w ogole bylo co
          ogladac - i to one maja sens na pustym koncie.
        */}
        <h2 className="h5 mb-2">{t('friends.suggestions')}</h2>
        <Card className="mb-4">
          <Card.Body>
            <FriendSuggestions
              refresh={refreshFriends}
              onChange={() => {
                fetch();
                setRefreshFriends((n) => n + 1);
              }}
            />
          </Card.Body>
        </Card>

        {/* Zaproszenia DO MNIE */}
        <h2 className="h5 mb-2">
          {t('friends.incoming')}
          {invitations.incoming.length > 0 && ` (${invitations.incoming.length})`}
        </h2>
        <Card className="mb-4">
          <Card.Body>
            {invitations.incoming.length === 0 ? (
              <p className="text-body-secondary small mb-0">{t('friends.noIncoming')}</p>
            ) : (
              invitations.incoming.map((z) => row(z, (
                <>
                  <Button
                    size="sm"
                    onClick={() => run(
                      () => client.post(`/friends/requests/${z.id}/accept`),
                      t('friends.nowFriends'))}
                  >
                    <IconPersonCheck /> {t('friends.accept')}
                  </Button>
                  <Button
                    size="sm"
                    variant="outline-secondary"
                    onClick={() => run(
                      () => client.delete(`/friends/requests/${z.id}`),
                      t('friends.rejected'))}
                  >
                    <IconCross /> {t('friends.reject')}
                  </Button>
                </>
              )))
            )}
          </Card.Body>
        </Card>

        {/* Zaproszenia ODE MNIE */}
        <h2 className="h5 mb-2">{t('friends.outgoing')}</h2>
        <Card className="mb-4">
          <Card.Body>
            {invitations.outgoing.length === 0 ? (
              <p className="text-body-secondary small mb-0">{t('friends.noOutgoing')}</p>
            ) : (
              invitations.outgoing.map((z) => row(z, (
                <Button
                  size="sm"
                  variant="outline-secondary"
                  onClick={() => run(
                    () => client.delete(`/friends/requests/${z.id}`),
                    t('friends.cancelled'))}
                >
                  <IconCross /> {t('friends.cancelInvite')}
                </Button>
              )))
            )}
          </Card.Body>
        </Card>

        {/* Moja lista znajomych - ten sam komponent co na profilu */}
        <h2 className="h5 mb-2">{t('friends.mine')}</h2>
        {user && <FriendsStrip username={user.username} refresh={refreshFriends} />}
      </Col>
    </Row>
  );
}
