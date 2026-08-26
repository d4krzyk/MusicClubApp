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
import client, { opiszBlad } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import Avatar from '../components/Avatar';
import PasekZnajomych from '../components/PasekZnajomych';
import { IkonaKrzyzyk, IkonaOsobaCheck, IkonaOsobaPlus } from '../components/Ikony';
import { sformatujDate } from '../utils/daty';

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

  const [zaproszenia, setZaproszenia] = useState({ incoming: [], outgoing: [] });
  const [ladowanie, setLadowanie] = useState(true);
  const [blad, setBlad] = useState(null);
  const [komunikat, setKomunikat] = useState(null);
  const [odswiezZnajomych, setOdswiezZnajomych] = useState(0);

  const [szukany, setSzukany] = useState('');
  const [wysylanie, setWysylanie] = useState(false);

  const pobierz = useCallback(async () => {
    setLadowanie(true);
    try {
      const odpowiedz = await client.get('/friends/requests');
      setZaproszenia(odpowiedz.data);
    } catch (error) {
      const opis = opiszBlad(error);
      setBlad(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    } finally {
      setLadowanie(false);
    }
  }, [t]);

  useEffect(() => {
    pobierz();
  }, [pobierz]);

  async function wykonaj(akcja, komunikatSukcesu) {
    setBlad(null);
    setKomunikat(null);
    try {
      await akcja();
      await pobierz();
      setOdswiezZnajomych((n) => n + 1);
      setKomunikat(komunikatSukcesu);
    } catch (error) {
      const opis = opiszBlad(error);
      setBlad(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    }
  }

  async function zapros(e) {
    e.preventDefault();
    if (!szukany.trim()) {
      return;
    }
    setWysylanie(true);
    try {
      const { data } = await client.post('/friends/requests', { username: szukany.trim() });
      await pobierz();
      setOdswiezZnajomych((n) => n + 1);
      setSzukany('');
      /*
       * Serwer mowi, czy znajomosc powstala OD RAZU - dzieje sie tak, gdy
       * ta osoba wczesniej zaprosila nas. Wtedy komunikat "zaproszenie
       * wyslane" bylby mylacy.
       */
      setKomunikat(data.friendsNow ? t('friends.nowFriends') : t('friends.invited'));
      setBlad(null);
    } catch (error) {
      const opis = opiszBlad(error);
      setBlad(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
      setKomunikat(null);
    } finally {
      setWysylanie(false);
    }
  }

  function wiersz(z, przyciski) {
    return (
      <div key={z.id} className="d-flex align-items-center gap-2 py-2 border-bottom">
        <Link to={`/profil/${z.username}`} className="d-flex align-items-center gap-2 text-decoration-none text-body flex-grow-1">
          <Avatar avatarUrl={z.avatarUrl} username={z.username} rozmiar={40} />
          <div>
            <div className="fw-semibold">{z.username}</div>
            <div className="text-body-secondary small">
              {sformatujDate(z.createdAt, i18n.language)}
            </div>
          </div>
        </Link>
        <div className="d-flex gap-2">{przyciski}</div>
      </div>
    );
  }

  return (
    <Row className="justify-content-center">
      <Col lg={8}>
        <h1 className="h4 mb-3">{t('friends.title')}</h1>

        {komunikat && (
          <Alert variant="success" dismissible onClose={() => setKomunikat(null)}>
            {komunikat}
          </Alert>
        )}
        {blad && <Alert variant="danger" dismissible onClose={() => setBlad(null)}>{blad}</Alert>}

        {/* Zapraszanie po loginie */}
        <Card className="mb-4">
          <Card.Body>
            <Form onSubmit={zapros}>
              <Form.Label htmlFor="szukany">{t('friends.inviteByName')}</Form.Label>
              <div className="d-flex gap-2">
                <Form.Control
                  id="szukany"
                  value={szukany}
                  onChange={(e) => setSzukany(e.target.value)}
                  placeholder={t('friends.usernamePlaceholder')}
                />
                <Button type="submit" disabled={wysylanie || !szukany.trim()}>
                  <IkonaOsobaPlus /> {t('friends.invite')}
                </Button>
              </div>
              <Form.Text>{t('friends.inviteHint')}</Form.Text>
            </Form>
          </Card.Body>
        </Card>

        {ladowanie && (
          <div className="text-center py-3 text-body-secondary">
            <Spinner animation="border" size="sm" className="me-2" />
            {t('common.loading')}
          </div>
        )}

        {/* Zaproszenia DO MNIE */}
        <h2 className="h5 mb-2">
          {t('friends.incoming')}
          {zaproszenia.incoming.length > 0 && ` (${zaproszenia.incoming.length})`}
        </h2>
        <Card className="mb-4">
          <Card.Body>
            {zaproszenia.incoming.length === 0 ? (
              <p className="text-body-secondary small mb-0">{t('friends.noIncoming')}</p>
            ) : (
              zaproszenia.incoming.map((z) => wiersz(z, (
                <>
                  <Button
                    size="sm"
                    onClick={() => wykonaj(
                      () => client.post(`/friends/requests/${z.id}/accept`),
                      t('friends.nowFriends'))}
                  >
                    <IkonaOsobaCheck /> {t('friends.accept')}
                  </Button>
                  <Button
                    size="sm"
                    variant="outline-secondary"
                    onClick={() => wykonaj(
                      () => client.delete(`/friends/requests/${z.id}`),
                      t('friends.rejected'))}
                  >
                    <IkonaKrzyzyk /> {t('friends.reject')}
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
            {zaproszenia.outgoing.length === 0 ? (
              <p className="text-body-secondary small mb-0">{t('friends.noOutgoing')}</p>
            ) : (
              zaproszenia.outgoing.map((z) => wiersz(z, (
                <Button
                  size="sm"
                  variant="outline-secondary"
                  onClick={() => wykonaj(
                    () => client.delete(`/friends/requests/${z.id}`),
                    t('friends.cancelled'))}
                >
                  <IkonaKrzyzyk /> {t('friends.cancelInvite')}
                </Button>
              )))
            )}
          </Card.Body>
        </Card>

        {/* Moja lista znajomych - ten sam komponent co na profilu */}
        <h2 className="h5 mb-2">{t('friends.mine')}</h2>
        {user && <PasekZnajomych username={user.username} odswiez={odswiezZnajomych} />}
      </Col>
    </Row>
  );
}
