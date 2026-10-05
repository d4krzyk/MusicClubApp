import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import { describeError } from '../api/client';
import * as znajomi from '../api/znajomi';
import { useAuth } from '../auth/AuthContext';
import Avatar from '../components/Avatar';
import PeopleSkeleton from '../components/PeopleSkeleton';
import FriendsStrip from '../components/FriendsStrip';
import FriendSuggestions from '../components/FriendSuggestions';
import { IconCards, IconCross, IconList, IconPersonCheck, IconPersonPlus } from '../components/Icons';
import TrybPoznawaj from '../components/poznawaj/TrybPoznawaj';
import { formatDate } from '../utils/dates';
import useWskaznik from '../hooks/useWskaznik';

/** Zapamietany tryb zakladki - kto przeglada karty, wraca do kart. */
const KLUCZ_TRYBU = 'znajomi.tryb';

function zapamietanyTryb() {
  try {
    return localStorage.getItem(KLUCZ_TRYBU) === 'poznawaj' ? 'poznawaj' : 'lista';
  } catch {
    return 'lista';
  }
}

/**
 * Ekran "Znajomi" w dwoch trybach: "Lista" (zaproszenia, wyszukiwarka, propozycje i wlasna lista) i "Poznawaj"
 * (karty osob z okolicy jedna po drugiej - wzajemne "tak" = znajomi). Tryb siedzi w adresie (?tryb=poznawaj),
 * wiec da sie do niego prowadzic odnosnikiem.
 */
export default function FriendsPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const tryb = params.get('tryb') ?? zapamietanyTryb();
  const tryby = useRef(null);
  useWskaznik(tryby, tryb);

  function przelacz(nowy) {
    try {
      localStorage.setItem(KLUCZ_TRYBU, nowy);
    } catch {
      // bez pamieci - tryb i tak siedzi w adresie
    }
    setParams(nowy === 'poznawaj' ? { tryb: 'poznawaj' } : {}, { replace: true });
  }

  return (
    <Row className="justify-content-center">
      <Col lg={tryb === 'poznawaj' ? 10 : 8} xl={tryb === 'poznawaj' ? 9 : 8} className="tiles-in">
        <div className="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3">
          <h1 className="h4 mb-0">{t('friends.title')}</h1>
          <div ref={tryby} className="znajomi-tryby" role="tablist" aria-label={t('discover.modes')}>
            <button type="button" role="tab" id="tryb-lista" aria-selected={tryb === 'lista'}
              aria-controls="tryb-panel" className={tryb === 'lista' ? 'is-aktywny' : ''} onClick={() => przelacz('lista')}>
              <IconList size={14} /> {t('discover.tabList')}
            </button>
            <button type="button" role="tab" id="tryb-poznawaj" aria-selected={tryb === 'poznawaj'}
              aria-controls="tryb-panel" className={tryb === 'poznawaj' ? 'is-aktywny' : ''} onClick={() => przelacz('poznawaj')}>
              <IconCards size={14} /> {t('discover.tabDiscover')}
            </button>
          </div>
        </div>
        <div id="tryb-panel" role="tabpanel" aria-labelledby={tryb === 'poznawaj' ? 'tryb-poznawaj' : 'tryb-lista'}>
          {tryb === 'poznawaj' ? <TrybPoznawaj /> : <ListaZnajomych />}
        </div>
      </Col>
    </Row>
  );
}

/** Tryb "Lista": zaproszenia oczekujace, wyszukiwarka, propozycje i wlasna lista. */
function ListaZnajomych() {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();

  const [invitations, setInvitations] = useState({ incoming: [], outgoing: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [refreshFriends, setRefreshFriends] = useState(0);

  const [searched, setSearched] = useState('');
  const [sending, setSending] = useState(false);

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      setInvitations(await znajomi.zaproszenia());
    } catch (error) {
      const details = describeError(error);
      setError(details.message);
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
      setError(details.message);
    }
  }

  async function invite(e) {
    e.preventDefault();
    if (!searched.trim()) {
      return;
    }
    setSending(true);
    try {
      const data = await znajomi.zapros(searched.trim());
      await fetch();
      setRefreshFriends((n) => n + 1);
      setSearched('');
      setMessage(data.friendsNow ? t('friends.nowFriends') : t('friends.invited'));
      setError(null);
    } catch (error) {
      const details = describeError(error);
      setError(details.message);
      setMessage(null);
    } finally {
      setSending(false);
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
      <>
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
                {/*
                  text-nowrap: bez tego ikona i napis lamia sie na dwa wiersze i przycisk robi sie
                  dwa razy wyzszy niz pole obok
                */}
                <Button
                  type="submit"
                  className="text-nowrap"
                  disabled={sending || !searched.trim()}
                >
                  <IconPersonPlus /> {t('friends.invite')}
                </Button>
              </div>
              <Form.Text>{t('friends.inviteHint')}</Form.Text>
            </Form>
          </Card.Body>
        </Card>

        {/*
          Szkielet zamiast kolka: zaproszenia to lista osob, wiec pokazujemy ksztalt listy osob.
        */}
        {loading && <PeopleSkeleton count={3} variant="friend" />}

        {/* Propozycje stoja WYZEJ niz zaproszenia. */}
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
                      () => znajomi.przyjmij(z.id),
                      t('friends.nowFriends'))}
                  >
                    <IconPersonCheck /> {t('friends.accept')}
                  </Button>
                  <Button
                    size="sm"
                    variant="outline-secondary"
                    onClick={() => run(
                      () => znajomi.odrzuc(z.id),
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
                    () => znajomi.odrzuc(z.id),
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
        {user && <FriendsStrip username={user.username} refresh={refreshFriends} self />}
      </>
  );
}
