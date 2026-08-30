import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import { describeError } from '../api/client';
import * as posty from '../api/posty';
import { publiczny } from '../api/profil';
import { useAuth } from '../auth/AuthContext';
import { useChat } from '../chat/ChatContext';
import Avatar from '../components/Avatar';
import Post from '../components/Post';
import PostSkeleton from '../components/PostSkeleton';
import ProfileSkeleton from '../components/ProfileSkeleton';
import EmptyState from '../components/EmptyState';
import CommonGround from '../components/CommonGround';
import PresenceDot from '../components/PresenceDot';
import ReportButton from '../components/ReportButton';
import FriendsStrip from '../components/FriendsStrip';
import TopMusic from '../components/TopMusic';
import Favorites from '../components/Favorites';
import Playlists from '../components/Playlists';
import FriendshipButton from '../components/FriendshipButton';
import { IconChat, IconInbox, IconPlus } from '../components/Icons';
import { formatDate } from '../utils/dates';

/** Ile postow pobieramy za jednym razem. */
const PAGE_SIZE = 10;

/** Profil uzytkownika - wlasny albo cudzy. */
export default function ProfilePage() {
  const { t, i18n } = useTranslation();
  const { username } = useParams();
  const { user } = useAuth();
  const { openChat } = useChat();

  // Bez nazwy w adresie ogladamy siebie
  const whose = username ?? user?.username;

  const [profile, setProfile] = useState(null);
  const [posts, setPosts] = useState([]);
  const [page, setPage] = useState(0);
  const [lastPage, setLastPage] = useState(true);
  const [loading, setLoading] = useState(true);
  const [postsLoading, setPostsLoading] = useState(true);
  const [error, setError] = useState(null);

  /* Licznik wymuszajacy przeladowanie paska znajomych. */
  const [refreshFriends, setRefreshFriends] = useState(0);

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setProfile(await publiczny(whose));
    } catch (error) {
      /* Przy 404 pokazujemy WLASNY komunikat. */
      const details = describeError(error);
      setError(error.response?.status === 404
        ? t('profile.notFound')
        : details.message);
      setProfile(null);
    } finally {
      setLoading(false);
    }
  }, [whose, t]);

  const loadPosts = useCallback(async (pageNumber, joined) => {
    setPostsLoading(true);
    try {
      const data = await posty.tablica({
        strona: pageNumber, rozmiar: PAGE_SIZE, kolejnosc: 'desc', autor: whose,
      });

      setPosts((previous) => (joined ? [...previous, ...data.content] : data.content));
      setLastPage(data.last);
      setPage(data.number);
    } catch (error) {
      const details = describeError(error);
      setError(details.message);
    } finally {
      setPostsLoading(false);
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
      await posty.usun(id);
      setPosts((previous) => previous.filter((p) => p.id !== id));
      // Licznik postow w naglowku musi sie zgadzac z tym, co widac nizej
      setProfile((p) => (p ? { ...p, postCount: Math.max(p.postCount - 1, 0) } : p));
    } catch (error) {
      const details = describeError(error);
      setError(details.message);
    }
  }

  if (loading && !profile) {
    /*
     * Szkielet zamiast kolka - te same ksztalty co gotowa strona, wiec po wczytaniu nic nie
     * podskakuje.
     */
    return (
      <Row className="justify-content-center">
        <Col lg={8}>
          <ProfileSkeleton />
        </Col>
      </Row>
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
      {/* tiles-in sprawia, ze sekcje profilu wchodza PO KOLEI - tak samo jak karty na tablicy. */}
      <Col lg={8} className="tiles-in">
        <Card className="mb-4">
          <Card.Body className="d-flex align-items-center gap-3 flex-wrap">
            <Avatar avatarUrl={profile.avatarUrl} username={profile.username} size={80} />

            <div className="flex-grow-1">
              <h1 className="h4 mb-1">{profile.username}</h1>

              {/* Obecnosc pokazujemy tylko na CUDZYM profilu. */}
              {!profile.self && (
                <div className="mb-1">
                  <PresenceDot presence={profile.presence} withLabel />
                </div>
              )}

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
              Ustawienia konta (login, e-mail, haslo) sa czyms innym niz profil - dlatego przycisk
              prowadzi do osobnej strony i widzi go tylko wlasciciel.
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

              {/* "Napisz" pokazuje sie WYLACZNIE przy znajomym - bo tylko z nim wolno pisac. */}
              {profile.friendshipStatus === 'FRIENDS' && (
                <Button
                  variant="outline-primary"
                  size="sm"
                  onClick={() => openChat(profile.username)}
                >
                  <IconChat className="me-1" /> {t('chat.write')}
                </Button>
              )}

              {/* Zgloszenie - tylko na CUDZYM profilu, z oczywistego powodu. */}
              {!profile.self && (
                <ReportButton
                  username={profile.username}
                  contexts={profile.friendshipStatus === 'FRIENDS'
                    ? ['PROFILE', 'CONVERSATION']
                    : ['PROFILE']}
                />
              )}
            </div>
          </Card.Body>
        </Card>

        {/* "Co Was laczy" stoi NAD ulubionymi i to jest celowe. */}
        <CommonGround username={profile.username} />

        {/*
          Dwa bloki obok siebie, ktore latwo pomylic, a mowia co innego: ULUBIENI to swiadoma
          deklaracja - "lubie tych wykonawcow".
        */}
        <Favorites
          username={profile.username}
          onChange={() => setRefreshFriends((n) => n + 1)}
        />

        {/* GABLOTKA PLAYLIST to trzeci, jeszcze inny rodzaj informacji. */}
        <Playlists username={profile.username} />

        <TopMusic username={profile.username} refresh={refreshFriends} />

        <h2 className="h5 mb-2">{t('friends.title')}</h2>
        <div className="mb-4">
          <FriendsStrip username={profile.username} refresh={refreshFriends} self={profile.self} />
        </div>

        <h2 className="h5 mb-3">{t('profile.posts')}</h2>

        {error && <Alert variant="danger">{error}</Alert>}

        {postsLoading && <PostSkeleton count={2} />}

        <div className="feed-page">
          {posts.map((post, i) => (
            <Post
              key={post.id}
              post={post}
              index={i % PAGE_SIZE}
              onDelete={deletePost}
              onUpdate={afterPostChange}
              onReaction={afterPostChange}
            />
          ))}
        </div>

        {!postsLoading && posts.length === 0 && (
          <EmptyState
            icon={IconInbox}
            title={profile.self ? t('profile.noPostsSelf') : t('profile.noPosts')}
            text={profile.self
              ? t('profile.noPostsSelfHint')
              : t('profile.noPostsHint', { username: profile.username })}
            action={profile.self && (
              <Link to="/" className="btn btn-primary">
                <IconPlus /> {t('posts.newPost')}
              </Link>
            )}
          />
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
