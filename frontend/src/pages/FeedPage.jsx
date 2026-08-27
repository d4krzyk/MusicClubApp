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
import client, { describeError } from '../api/client';
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

/**
 * Tablica: przycisk dodawania posta i lista wpisow.
 *
 * <p><b>Kolejnosc ustala serwer</b>: najpierw posty znajomych (i wlasne),
 * pod nimi publiczne posty pozostalych osob. Nie da sie tego zrobic po
 * stronie przegladarki - przesiewanie po pobraniu psuloby stronicowanie,
 * bo kazda strona zawieralaby wtedy inny zestaw wpisow.</p>
 *
 * <p>Przelacznik nad tablica pozwala zawezic ja do samych znajomych.
 * <b>Domyslnie jest szeroka</b>, bo konto zalozone przed chwila nie ma
 * jeszcze ani jednego znajomego - a aplikacja, ktora wita takiego
 * uzytkownika pusta strona, jest bezuzyteczna dokladnie wtedy, kiedy
 * najbardziej potrzebuje go przekonac.</p>
 *
 * <p>Posty doladowujemy przyciskiem "pokaz starsze" zamiast klasycznego
 * stronicowania z numerami - na tablicy naturalniej jest doklejac kolejne
 * wpisy pod spodem. Backend i tak stronicuje normalnie (wymagania nr 3 i 5).</p>
 */
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
      const response = await client.get('/posts', {
        params: { page: pageNumber, size: PAGE_SIZE, scope: wantedScope },
      });
      const data = response.data;

      setPosts((previous) => (joined ? [...previous, ...data.content] : data.content));
      setLastPage(data.last);
      setPage(data.number);
    } catch (error) {
      const details = describeError(error);
      setListError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    } finally {
      setLoading(false);
      setFirstLoad(false);
    }
  }, [t]);

  useEffect(() => {
    fetch(0, false, scope);
  }, [fetch, scope]);

  /*
   * Klikniecie w logo albo w ikone tablicy - takze wtedy, gdy juz tu
   * jestesmy. Layout zostawia w stanie trasy znacznik czasu; jego zmiana
   * jest jedynym sygnalem, bo adres pozostaje ten sam.
   *
   * Pobieramy PIERWSZA strone od nowa, a nie doklejamy: kto wraca na gore,
   * ten chce zobaczyc, co doszlo, a nie te same wpisy w dwoch kopiach.
   */
  useEffect(() => {
    if (location.state?.refreshAt) {
      fetch(0, false, scope);
    }
  }, [location.state?.refreshAt, fetch, scope]);

  /*
   * Odswiezanie licznikow reakcji po powrocie do karty. Wywolanie MUSI byc
   * stabilne (useCallback bez zaleznosci), inaczej zegar w srodku
   * przestawialby sie przy kazdym renderze - czyli po kazdym kliknieciu.
   */
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
   * Reakcja tez zwraca odswiezonego posta (ze swiezymi licznikami), ale
   * NIE jest zmiana tresci - wiec zadnego komunikatu. Wczesniej obie rzeczy
   * szly tym samym wywolaniem i klikniecie emotki pod cudzym postem
   * oglaszalo "Post zostal zaktualizowany".
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
      await client.delete(`/posts/${id}`);
      setPosts((previous) => previous.filter((p) => p.id !== id));
      setMessage(t('posts.deleted'));
    } catch (error) {
      const details = describeError(error);
      setListError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    }
  }

  /*
   * Gdzie konczy sie krag, a zaczyna reszta swiata. Liczymy to z pola
   * fromFriend wyliczonego przez SERWER - przegladarka nie zna listy naszych
   * znajomych i nie ma z czego tego odtworzyc.
   *
   * Kreske rysujemy tylko wtedy, gdy NAD nia cos jest: u kogos bez znajomych
   * napis "dalej: osoby, ktorych jeszcze nie znasz" na samej gorze tablicy
   * brzmialby jak wyrzut.
   */
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
    /*
     * Widocznosci NIE resetujemy. Kto raz wybral "tylko dla znajomych",
     * najpewniej chce tak pisac dalej - a ciche przestawienie z powrotem
     * na publiczny przy drugim poscie byloby ujawnieniem tresci wbrew
     * decyzji, ktora ta osoba przed chwila podjela.
     */
  }

  /*
   * Zly link BLOKUJE wysylke. Wczesniej taki adres byl po cichu polykany:
   * post powstawal bez odtwarzacza i bez slowa wyjasnienia, co dla
   * uzytkownika wygladalo jak zepsuta aplikacja.
   */
  const blokada = linkError(musicUrl, musicKind);

  async function submit(e) {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);
    setSending(true);

    try {
      /*
       * Zdjecia to pliki binarne, wiec zamiast JSON-a wysylamy FormData.
       * Czesc tekstowa idzie jako osobny fragment o nazwie "post" i MUSI miec
       * typ application/json - inaczej Spring nie umie jej zamienic na obiekt
       * i odrzuca zapytanie bledem 415.
       */
      const formData = new FormData();
      formData.append(
        'post',
        new Blob(
          [JSON.stringify({
            content,
            musicUrl: musicUrl || null,
            // Bez linku rodzaj nie ma do czego sie odnosic - serwer to odrzuci
            musicKind: musicUrl ? musicKind : null,
            musicStartSeconds: musicKind === 'TRACK' ? toSeconds(startAt) : null,
            visibility,
          })],
          { type: 'application/json' }
        )
      );
      files.forEach((file) => formData.append('images', file));

      const response = await client.post('/posts', formData);

      clear();
      onAdded(response.data);
    } catch (error) {
      const details = describeError(error);
      setFieldErrors(details.fieldErrors);
      setGeneralError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
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
