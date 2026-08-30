import { Fragment, useCallback, useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Collapse from 'react-bootstrap/Collapse';
import { describeError } from '../api/client';
import * as posty from '../api/posty';
import Field from '../components/Field';
import Post from '../components/Post';
import PostSkeleton from '../components/PostSkeleton';
import EmptyState from '../components/EmptyState';
import ImagePicker from '../components/ImagePicker';
import MusicPicker from '../components/MusicPicker';
import VisibilityPicker from '../components/VisibilityPicker';
import useLiveReactions from '../hooks/useLiveReactions';
import { IconCross, IconPlus, IconFriends, IconGlobe, IconInbox } from '../components/Icons';
import { toSeconds } from '../utils/time';
import { linkError } from '../utils/musicLinks';

/** Ile postow pobieramy za jednym razem. */
const PAGE_SIZE = 10;

/** Limit zdjec w jednym poscie - taki sam jak po stronie backendu. */
const MAX_IMAGES = 10;

/** Tablica: przycisk dodawania posta i lista wpisow. */
export default function FeedPage() {
  const { t } = useTranslation();
  const location = useLocation();

  const [posts, setPosts] = useState([]);
  const [page, setPage] = useState(0);
  const [lastPage, setLastPage] = useState(true);
  const [loading, setLoading] = useState(true);
  const [firstLoad, setFirstLoad] = useState(true);
  const [listError, setListError] = useState(null);
  const [message, setMessage] = useState(null);
  const [formOpen, setFormOpen] = useState(false);
  const [scope, setScope] = useState('ALL');

  const fetch = useCallback(async (pageNumber, joined, wantedScope) => {
    setLoading(true);
    setListError(null);
    try {
      const data = await posty.tablica({
        strona: pageNumber, rozmiar: PAGE_SIZE, zakres: wantedScope,
      });

      setPosts((previous) => (joined ? [...previous, ...data.content] : data.content));
      setLastPage(data.last);
      setPage(data.number);
    } catch (error) {
      const details = describeError(error);
      setListError(details.message);
    } finally {
      setLoading(false);
      setFirstLoad(false);
    }
  }, [t]);

  useEffect(() => {
    fetch(0, false, scope);
  }, [fetch, scope]);

  /* Klikniecie w logo albo w ikone tablicy - takze wtedy, gdy juz tu jestesmy. */
  useEffect(() => {
    if (location.state?.refreshAt) {
      fetch(0, false, scope);
    }
  }, [location.state?.refreshAt, fetch, scope]);

  /* Odswiezanie licznikow reakcji po powrocie do karty. */
  const applyCounts = useCallback((counts) => {
    setPosts((previous) => previous.map((post) =>
      (counts[post.id] ? { ...post, reactions: counts[post.id] } : post)));
  }, []);

  useLiveReactions(posts, applyCounts);

  function changeScope(next) {
    if (next === scope) {
      return;
    }
    // Nowy zakres = inna lista; bez wyczyszczenia mignelyby stare wpisy
    setPosts([]);
    setFirstLoad(true);
    setScope(next);
  }

  function afterAdd(created) {
    // Nowy post ma byc na gorze - to najszybszy sposob, bez ponownego pobierania
    setPosts((previous) => [created, ...previous]);
    setMessage(t('posts.published'));
    setFormOpen(false);
  }

  function afterEdit(updated) {
    replacePost(updated);
    setMessage(t('posts.updated'));
  }

  /*
   * Reakcja tez zwraca odswiezonego posta (ze swiezymi licznikami), ale NIE jest zmiana tresci -
   * wiec zadnego komunikatu.
   */
  function afterReaction(updated) {
    replacePost(updated);
  }

  function replacePost(updated) {
    setPosts((previous) =>
      previous.map((p) => (p.id === updated.id ? updated : p)));
  }

  async function remove(id) {
    if (!window.confirm(t('common.confirmDelete'))) {
      return;
    }
    try {
      await posty.usun(id);
      setPosts((previous) => previous.filter((p) => p.id !== id));
      setMessage(t('posts.deleted'));
    } catch (error) {
      const details = describeError(error);
      setListError(details.message);
    }
  }

  /* Gdzie konczy sie krag, a zaczyna reszta swiata. */
  const strangersStartAt = posts.findIndex((post) => !post.fromFriend);
  const showDivider = scope === 'ALL' && strangersStartAt > 0;

  return (
    <Row className="justify-content-center">
      <Col lg={8} className="feed-page">
        <div className="d-flex align-items-center justify-content-between mb-3 gap-2 flex-wrap">
          <h1 className="h4 mb-0 page-title">{t('posts.title')}</h1>

          <Button
            variant={formOpen ? 'outline-secondary' : 'primary'}
            onClick={() => setFormOpen((open) => !open)}
            aria-expanded={formOpen}
            aria-controls="formularz-postu"
          >
            {formOpen ? (
              <>
                <IconCross /> {t('common.cancel')}
              </>
            ) : (
              <>
                <IconPlus /> {t('posts.newPost')}
              </>
            )}
          </Button>
        </div>

        <div className="segmented mb-3" role="group" aria-label={t('posts.scope.label')}>
          <button
            type="button"
            className={`segmented-option${scope === 'ALL' ? ' is-active' : ''}`}
            aria-pressed={scope === 'ALL'}
            onClick={() => changeScope('ALL')}
          >
            <IconGlobe size={14} />
            <span>{t('posts.scope.ALL')}</span>
          </button>
          <button
            type="button"
            className={`segmented-option${scope === 'FRIENDS' ? ' is-active' : ''}`}
            aria-pressed={scope === 'FRIENDS'}
            onClick={() => changeScope('FRIENDS')}
          >
            <IconFriends size={14} />
            <span>{t('posts.scope.FRIENDS')}</span>
          </button>
        </div>

        <Collapse in={formOpen}>
          <div id="formularz-postu">
            <PostForm onAdded={afterAdd} />
          </div>
        </Collapse>

        {message && (
          <Alert variant="success" dismissible onClose={() => setMessage(null)}>
            {message}
          </Alert>
        )}
        {listError && <Alert variant="danger">{listError}</Alert>}

        {firstLoad && loading && <PostSkeleton count={3} />}

        {posts.map((post, i) => (
          /* Fragment, a nie <div> - dodatkowy element popsulby odstepy miedzy kartami */
          <Fragment key={post.id}>
            {showDivider && i === strangersStartAt && (
              <div className="feed-divider">
                <span>{t('posts.strangersBelow')}</span>
              </div>
            )}

            <Post
              post={post}
              index={i % PAGE_SIZE}
              onDelete={remove}
              onUpdate={afterEdit}
              onReaction={afterReaction}
            />
          </Fragment>
        ))}

        {!loading && posts.length === 0 && !listError && (
          scope === 'FRIENDS' ? (
            <EmptyState
              icon={IconFriends}
              title={t('posts.emptyFriends.title')}
              text={t('posts.emptyFriends.text')}
              action={
                <>
                  <Button variant="primary" onClick={() => changeScope('ALL')}>
                    {t('posts.emptyFriends.showAll')}
                  </Button>
                  <Link to="/znajomi" className="btn btn-outline-secondary">
                    {t('posts.emptyFriends.findFriends')}
                  </Link>
                </>
              }
            />
          ) : (
            <EmptyState
              icon={IconInbox}
              title={t('posts.emptyAll.title')}
              text={t('posts.emptyAll.text')}
              action={
                <Button variant="primary" onClick={() => setFormOpen(true)}>
                  <IconPlus /> {t('posts.newPost')}
                </Button>
              }
            />
          )
        )}

        {loading && !firstLoad && (
          <div className="text-center py-3 text-body-secondary small">
            <span className="loading-dots me-2" aria-hidden="true">
              <span /><span /><span />
            </span>
            {t('common.loading')}
          </div>
        )}

        {!loading && !lastPage && (
          <div className="text-center">
            <Button variant="outline-secondary" onClick={() => fetch(page + 1, true, scope)}>
              {t('posts.loadMore')}
            </Button>
          </div>
        )}
      </Col>
    </Row>
  );
}

/** Formularz dodawania posta: tekst, zdjecia, utwor i wybor widocznosci. */
function PostForm({ onAdded }) {
  const { t } = useTranslation();

  const [content, setContent] = useState('');
  const [musicUrl, setMusicUrl] = useState('');
  const [musicKind, setMusicKind] = useState('TRACK');
  const [startAt, setStartAt] = useState('');
  const [files, setFiles] = useState([]);
  const [visibility, setVisibility] = useState('PUBLIC');

  const [fieldErrors, setFieldErrors] = useState({});
  const [generalError, setGeneralError] = useState(null);
  const [sending, setSending] = useState(false);

  function clear() {
    setContent('');
    setMusicUrl('');
    setMusicKind('TRACK');
    setStartAt('');
    setFiles([]);
    /* Widocznosci NIE resetujemy. */
  }

  /* Zly link BLOKUJE wysylke. */
  const blokada = linkError(musicUrl, musicKind);

  async function submit(e) {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);
    setSending(true);

    try {
      const dodany = await posty.dodaj({
        content,
        musicUrl: musicUrl || null,
        // Bez linku rodzaj nie ma do czego sie odnosic - serwer to odrzuci
        musicKind: musicUrl ? musicKind : null,
        musicStartSeconds: musicKind === 'TRACK' ? toSeconds(startAt) : null,
        visibility,
      }, files);

      clear();
      onAdded(dodany);
    } catch (error) {
      const details = describeError(error);
      setFieldErrors(details.fieldErrors);
      setGeneralError(details.message);
    } finally {
      setSending(false);
    }
  }

  return (
    <Card className="mb-4">
      <Card.Body>
        {generalError && <Alert variant="danger">{generalError}</Alert>}

        <Form onSubmit={submit} noValidate>
          <Field
            id="content"
            label={t('posts.content')}
            value={content}
            onChange={setContent}
            error={fieldErrors.content}
            placeholder={t('posts.contentPlaceholder')}
            asTextarea
            rows={3}
          />

          <ImagePicker
            files={files}
            onChange={setFiles}
            maks={MAX_IMAGES}
            error={fieldErrors.images}
          />

          <MusicPicker
            kind={musicKind}
            onKind={setMusicKind}
            link={musicUrl}
            onLink={setMusicUrl}
            startSeconds={startAt}
            onStartSeconds={setStartAt}
            serverErrors={fieldErrors}
          />

          <VisibilityPicker value={visibility} onChange={setVisibility} />

          <Button type="submit" disabled={sending || Boolean(blokada)}>
            {sending ? t('posts.publishing') : t('posts.publish')}
          </Button>
        </Form>
      </Card.Body>
    </Card>
  );
}
