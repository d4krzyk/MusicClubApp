import { useTranslation } from 'react-i18next';
import Form from 'react-bootstrap/Form';
import ButtonGroup from 'react-bootstrap/ButtonGroup';
import Button from 'react-bootstrap/Button';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Field from './Field';
import { linkError } from '../utils/musicLinks';

/** Wybor nagrania do posta: rodzaj, link i (tylko dla utworu) moment startu. */

/** Kolejnosc na przelaczniku - od najczestszego przypadku. */
const KINDS = ['TRACK', 'ALBUM', 'ARTIST', 'PLAYLIST'];

export default function MusicPicker({
  kind, onKind,
  link, onLink,
  startSeconds, onStartSeconds,
  serverErrors = {},
  idPrefix = '',
}) {
  const { t } = useTranslation();

  const errorKey = linkError(link, kind);
  // Blad z serwera ma pierwszenstwo - jest ostateczny
  const linkFieldError = serverErrors.musicUrl ?? (errorKey ? t(errorKey) : undefined);

  const startSecondsAllowed = kind === 'TRACK';

  function changeKind(created) {
    onKind(created);
    // Przy zmianie na album/artyste moment startu traci sens - czyscimy go,
    // zeby nie poszedl na serwer jako "ukryta" wartosc
    if (created !== 'TRACK') {
      onStartSeconds('');
    }
  }

  return (
    <div className="mb-3">
      <Form.Label className="d-block">{t('posts.music')}</Form.Label>

      <ButtonGroup size="sm" className="mb-2" aria-label={t('posts.musicKind')}>
        {KINDS.map((code) => (
          <Button
            key={code}
            type="button"
            variant={kind === code ? 'primary' : 'outline-secondary'}
            onClick={() => changeKind(code)}
            aria-pressed={kind === code}
          >
            {t(`posts.musicKinds.${code}`)}
          </Button>
        ))}
      </ButtonGroup>

      <Row>
        <Col md={startSecondsAllowed ? 8 : 12}>
          <Field
            id={`${idPrefix}musicUrl`}
            label={t('posts.musicUrl')}
            value={link}
            onChange={onLink}
            error={linkFieldError}
            suggestion={t(`posts.musicHints.${kind}`)}
            placeholder={t('posts.musicPlaceholder')}
            wymagane={false}
          />
        </Col>

        {/* Moment startu WYLACZNIE przy utworze */}
        {startSecondsAllowed && (
          <Col md={4}>
            <Field
              id={`${idPrefix}musicStartSeconds`}
              label={t('posts.startAt')}
              value={startSeconds}
              onChange={onStartSeconds}
              error={serverErrors.musicStartSeconds}
              suggestion={t('posts.startAtHint')}
              placeholder="1:23"
              wymagane={false}
            />
          </Col>
        )}
      </Row>
    </div>
  );
}
