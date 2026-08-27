import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Spinner from 'react-bootstrap/Spinner';
import Collapse from 'react-bootstrap/Collapse';
import client, { describeError } from '../api/client';
import Field from '../components/Field';
import Post from '../components/Post';
import ImagePicker from '../components/ImagePicker';
import MusicPicker from '../components/MusicPicker';
import { IconCross, IconPlus } from '../components/Icons';
import { toSeconds } from '../utils/time';
import { linkError } from '../utils/musicLinks';

/** Ile postow pobieramy za jednym razem. */
const NA_STRONE = 10;

/** Limit zdjec w jednym poscie - taki sam jak po stronie backendu. */
const MAX_IMAGES = 10;

/**
 * Tablica: przycisk dodawania posta i lista wpisow.
 *
 * <p>Formularz jest domyslnie SCHOWANY za przyciskiem "Nowy post". Wczesniej
 * zajmowal pol ekranu nad tablica, przez co do pierwszego wpisu trzeba bylo
 * przewijac - a przez wieksza czesc czasu uzytkownik chce czytac, nie pisac.</p>
 *
 * <p>Posty doladowujemy przyciskiem "pokaz starsze" zamiast klasycznego
 * stronicowania z numerami - na tablicy naturalniej jest doklejac kolejne
 * wpisy pod spodem. Backend i tak stronicuje normalnie (wymagania nr 3 i 5).</p>
 */
export default function FeedPage() {
  const { t } = useTranslation();

  const [posts, setPosts] = useState([]);
  const [page, setPage] = useState(0);
  const [lastPage, setLastPage] = useState(true);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState(null);
  const [message, setMessage] = useState(null);
  const [formOpen, setFormOpen] = useState(false);

  const fetch = useCallback(async (pageNumber, joined) => {
    setLoading(true);
    setListError(null);
    try {
      const response = await client.get('/posts', {
        params: { page: pageNumber, size: NA_STRONE, direction: 'desc' },
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
    }
  }, [t]);

  useEffect(() => {
    fetch(0, false);
  }, [fetch]);

  function afterAdd(created) {
    // Nowy post ma byc na gorze - to najszybszy sposob, bez ponownego pobierania
    setPosts((previous) => [created, ...previous]);
    setMessage(t('posts.published'));
    setFormOpen(false);
  }

  function afterEdit(updated) {
    setPosts((previous) =>
      previous.map((p) => (p.id === updated.id ? updated : p)));
    setMessage(t('posts.updated'));
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

  return (
    <Row className="justify-content-center">
      <Col lg={8} className="feed-page">
        <div className="d-flex align-items-center justify-content-between mb-3">
          <h1 className="h4 mb-0">{t('posts.title')}</h1>

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

        {posts.map((post) => (
          <Post key={post.id} post={post} onDelete={remove} onUpdate={afterEdit} />
        ))}

        {!loading && posts.length === 0 && !listError && (
          <p className="text-body-secondary text-center py-4">{t('posts.empty')}</p>
        )}

        {loading && (
          <div className="text-center py-3 text-body-secondary">
            <Spinner animation="border" size="sm" className="me-2" />
            {t('common.loading')}
          </div>
        )}

        {!loading && !lastPage && (
          <div className="text-center">
            <Button variant="outline-secondary" onClick={() => fetch(page + 1, true)}>
              {t('posts.loadMore')}
            </Button>
          </div>
        )}
      </Col>
    </Row>
  );
}

/** Formularz dodawania posta: tekst, zdjecia i utwor ze Spotify. */
function PostForm({ onAdded }) {
  const { t } = useTranslation();

  const [content, setContent] = useState('');
  const [musicUrl, setMusicUrl] = useState('');
  const [musicKind, setMusicKind] = useState('TRACK');
  const [startAt, setStartAt] = useState('');
  const [files, setFiles] = useState([]);

  const [fieldErrors, setFieldErrors] = useState({});
  const [generalError, setGeneralError] = useState(null);
  const [sending, setSending] = useState(false);

  function clear() {
    setContent('');
    setMusicUrl('');
    setMusicKind('TRACK');
    setStartAt('');
    setFiles([]);
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

          <Button type="submit" disabled={sending || Boolean(blokada)}>
            {sending ? t('posts.publishing') : t('posts.publish')}
          </Button>
        </Form>
      </Card.Body>
    </Card>
  );
}
