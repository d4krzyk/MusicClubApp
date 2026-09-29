import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import { describeError } from '../api/client';
import * as posty from '../api/posty';
import Field from './Field';
import ImagePicker from './ImagePicker';
import MusicPicker from './MusicPicker';
import VisibilityPicker from './VisibilityPicker';
import { toSeconds } from '../utils/time';
import { linkError } from '../utils/musicLinks';

/** Limit zdjec w jednym poscie - taki sam jak po stronie backendu. */
const MAX_IMAGES = 10;

/**
 * Formularz dodawania posta: tekst, zdjecia, utwor i wybor widocznosci.
 * Z eventId post trafia pod to wydarzenie - poza tym niczym sie nie rozni.
 */
export default function PostForm({
  onAdded, eventId = null, idPola = 'content', etykieta, podpowiedz,
}) {
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
        eventId,
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
            id={idPola}
            label={etykieta ?? t('posts.content')}
            value={content}
            onChange={setContent}
            error={fieldErrors.content}
            placeholder={podpowiedz ?? t('posts.contentPlaceholder')}
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
