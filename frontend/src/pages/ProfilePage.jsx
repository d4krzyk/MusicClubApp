import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Spinner from 'react-bootstrap/Spinner';
import client, { describeError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import Avatar from '../components/Avatar';
import Post from '../components/Post';
import FriendsStrip from '../components/FriendsStrip';
import TopMusic from '../components/TopMusic';
import Favorites from '../components/Favorites';
import FriendshipButton from '../components/FriendshipButton';
import { formatDate } from '../utils/dates';

/** Ile postow pobieramy za jednym razem. */
const PAGE_SIZE = 10;

/**
 * Profil uzytkownika - wlasny albo cudzy.
 *
 * <p><b>Jedna strona na oba przypadki.</b> Roznica sprowadza sie do kilku
 * przyciskow, wiec osobny komponent na "moj profil" oznaczalby dwa pliki
 * robiace prawie to samo - i dwa miejsca do poprawiania przy kazdej zmianie.
 * O tym, ktory wariant widzimy, decyduje pole {@code self} <b>z serwera</b>.</p>
 *
 * <p>Adres {@code /profil} (bez nazwy) pokazuje profil zalogowanego
 * uzytkownika - wygodny link z menu.</p>
 *
 * <p>Miejsce na liste znajomych i ulubionych artystow ze Spotify jest
 * przygotowane nizej - dojda w kolejnych krokach.</p>
 */
export default function ProfilePage() {
  const { t, i18n } = useTranslation();
  const { username } = useParams();
  const { user } = useAuth();

  // Bez nazwy w adresie ogladamy siebie
  const whose = username ?? user?.username;

  const [profile, setProfile] = useState(null);
  const [posts, setPosts] = useState([]);
  const [page, setPage] = useState(0);
  const [lastPage, setLastPage] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  /*
   * Licznik wymuszajacy przeladowanie paska znajomych. Po przyjeciu albo
   * usunieciu znajomosci lista musi sie odswiezyc - a pasek pobiera dane sam,
   * wiec trzeba mu dac znac. Zwykla zmiana liczby wystarczy jako sygnal.
   */
  const [refreshFriends, setRefreshFriends] = useState(0);

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await client.get(`/profiles/${encodeURIComponent(whose)}`);
      setProfile(response.data);
    } catch (error) {
      /*
       * Przy 404 pokazujemy WLASNY komunikat. Serwer odsyla ogolne
       * "Nie znaleziono: user o identyfikatorze ...", co jest w porzadku
       * w logach i w Swaggerze, ale odwiedzajacemu profil nic nie mowi -
       * "user" to nazwa z kodu, nie slowo z jego swiata.
       */
      const details = describeError(error);
      setError(error.response?.status === 404
        ? t('profile.notFound')
        : details.message ?? (details.messageKey ? t(details.messageKey) : null));
      setProfile(null);
    } finally {
      setLoading(false);
    }
  }, [whose, t]);

  const loadPosts = useCallback(async (pageNumber, joined) => {
    try {
      const response = await client.get('/posts', {
        params: { page: pageNumber, size: PAGE_SIZE, direction: 'desc', author: whose },
      });
      const data = response.data;

      setPosts((previous) => (joined ? [...previous, ...data.content] : data.content));
      setLastPage(data.last);
      setPage(data.number);
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    }
  }, [whose, t]);

  useEffect(() => {
    if (!whose) {
      return;
    }
    // Nowy profil = czyscimy poprzednie posts, inaczej mignelyby cudze wpisy
    setPosts([]);
    loadProfile();
    loadPosts(0, false);
  }, [whose, loadProfile, loadPosts]);

  function afterPostChange(updated) {
    setPosts((previous) =>
      previous.map((p) => (p.id === updated.id ? updated : p)));
  }

  async function deletePost(id) {
    if (!window.confirm(t('common.confirmDelete'))) {
      return;
    }
    try {
      await client.delete(`/posts/${id}`);
      setPosts((previous) => previous.filter((p) => p.id !== id));
      // Licznik postow w naglowku musi sie zgadzac z tym, co widac nizej
      setProfile((p) => (p ? { ...p, postCount: Math.max(p.postCount - 1, 0) } : p));
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    }
  }

  if (loading && !profile) {
    return (
      <div className="text-center py-5 text-body-secondary">
        <Spinner animation="border" size="sm" className="me-2" />
        {t('common.loading')}
      </div>
    );
  }

  if (!profile) {
    return (
      <Row className="justify-content-center">
        <Col lg={8}>
          <Alert variant="danger">{error ?? t('profile.notFound')}</Alert>
          <Link to="/" className="btn btn-outline-secondary">
            {t('menu.feed')}
          </Link>
        </Col>
      </Row>
    );
  }

  return (
    <Row className="justify-content-center">
      <Col lg={8}>
        <Card className="mb-4">
          <Card.Body className="d-flex align-items-center gap-3 flex-wrap">
            <Avatar avatarUrl={profile.avatarUrl} username={profile.username} size={80} />

            <div className="flex-grow-1">
              <h1 className="h4 mb-1">{profile.username}</h1>
              <div className="text-body-secondary small">
                {t('profile.memberSince', {
                  date: formatDate(profile.createdAt, i18n.language),
                })}
              </div>
              <div className="text-body-secondary small">
                {t('profile.postCount', { count: profile.postCount })}
                {' · '}
                {t('friends.count', { count: profile.friendCount })}
              </div>
            </div>

            {/*
              Ustawienia konta (login, e-mail, haslo) sa czyms innym niz profil -
              dlatego przycisk prowadzi do osobnej strony i widzi go tylko
              wlasciciel. O tym, czy to jego profil, mowi serwer.
            */}
            <div className="d-flex flex-column align-items-end gap-2">
              {profile.self && (
                <Link to="/settings" className="btn btn-outline-secondary btn-sm">
                  {t('profile.editAccount')}
                </Link>
              )}

              <FriendshipButton
                profile={profile}
                onChange={async () => {
                  await loadProfile();
                  setRefreshFriends((n) => n + 1);
                }}
              />
            </div>
          </Card.Body>
        </Card>

        {/*
          Dwa bloki obok siebie, ktore latwo pomylic, a mowia co innego:

          ULUBIENI to swiadoma deklaracja - "lubie tych wykonawcow". To na
          nich opiera sie dopasowywanie ludzi.

          NAJCZESCIEJ WRZUCANE jest wyliczone z postow. Mowi, co ktos
          wrzuca na tablice - a to nie to samo: cos mozna wrzucic raz
          dla zartu albo dlatego, ze akurat bylo glosno.
        */}
        <Favorites
          username={profile.username}
          onChange={() => setRefreshFriends((n) => n + 1)}
        />

        <TopMusic username={profile.username} refresh={refreshFriends} />

        <h2 className="h5 mb-2">{t('friends.title')}</h2>
        <div className="mb-4">
          <FriendsStrip username={profile.username} refresh={refreshFriends} />
        </div>

        <h2 className="h5 mb-3">{t('profile.posts')}</h2>

        {error && <Alert variant="danger">{error}</Alert>}

        {posts.map((post) => (
          <Post key={post.id} post={post} onDelete={deletePost} onUpdate={afterPostChange} />
        ))}

        {posts.length === 0 && (
          <p className="text-body-secondary text-center py-4">
            {profile.self ? t('profile.noPostsSelf') : t('profile.noPosts')}
          </p>
        )}

        {!lastPage && (
          <div className="text-center">
            <Button variant="outline-secondary" onClick={() => loadPosts(page + 1, true)}>
              {t('posts.loadMore')}
            </Button>
          </div>
        )}
      </Col>
    </Row>
  );
}
