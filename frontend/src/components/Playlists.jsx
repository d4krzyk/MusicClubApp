import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import InputGroup from 'react-bootstrap/InputGroup';
import Modal from 'react-bootstrap/Modal';
import { describeError } from '../api/client';
import * as profil from '../api/profil';
import HorizontalStrip from './HorizontalStrip';
import { IconLink, IconPlaylist } from './Icons';
import { playerHeight } from '../utils/player';
import { linkError } from '../utils/musicLinks';

/**
 * Gablotka playlist na profilu - do pieciu skladanek "posluchaj tego, co ja".
 *
 * <p><b>Czym to sie rozni od bloku "Ulubieni".</b> Tamten opiera sie na
 * katalogu Deezera i sluzy dopasowywaniu ludzi - dlatego kazda pozycja musi
 * byc porownywalna co do identyfikatora. Playlista jest dla czlowieka,
 * nie dla maszyny: nie liczy sie do zadnego dopasowania, bo dwie osoby moga
 * wystawic te sama skladanke, majac na mysli zupelnie co innego.</p>
 *
 * <p><b>Odtwarzacz otwiera sie dopiero po kliknieciu.</b> Piec ramek
 * {@code <iframe>} wczytywanych od razu to piec polaczen do obcych serwisow
 * przy kazdym wejsciu na profil - i profil, ktory wstaje sekunde dluzej.
 * W gablotce widac okladki, a granie zaczyna sie na zadanie.</p>
 *
 * @param username czyj profil ogladamy
 */
export default function Playlists({ username }) {
  const { t } = useTranslation();

  const [data, setData] = useState(null);
  const [url, setUrl] = useState('');
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState(null);
  const [playing, setPlaying] = useState(null);

  const fetch = useCallback(async () => {
    try {
      setData(await profil.playlisty(username));
    } catch {
      // Gablotka to czesc profilu, a nie caly profil - gdy padnie,
      // reszta strony ma dzialac dalej
      setData(null);
    }
  }, [username]);

  useEffect(() => {
    fetch();
  }, [fetch]);

  if (!data) {
    return null;
  }

  const { items, canEdit, max } = data;

  // Pustej gablotki na cudzym profilu nie pokazujemy - to tylko dziura na stronie
  if (!canEdit && items.length === 0) {
    return null;
  }

  /*
   * Zly link BLOKUJE wysylke - tak samo jak przy dodawaniu posta. Rodzaj
   * jest tu z gory ustalony na PLAYLIST, wiec komunikat od razu mowi, co
   * uzytkownik wkleil (np. "to jest link do UTWORU").
   */
  const clientError = linkError(url, 'PLAYLIST');

  async function add(event) {
    event.preventDefault();
    setAdding(true);
    setError(null);
    try {
      setData(await profil.dodajPlayliste(url));
      setUrl('');
    } catch (problem) {
      const details = describeError(problem);
      setError(details.message ?? details.fieldErrors.url);
    } finally {
      setAdding(false);
    }
  }

  async function remove(id) {
    setError(null);
    try {
      setData(await profil.usunPlayliste(id));
    } catch (problem) {
      const details = describeError(problem);
      setError(details.message);
    }
  }

  return (
    <section className="mb-4">
      <div className="d-flex align-items-center justify-content-between mb-2">
        <h2 className="h5 mb-0">{t('playlists.title')}</h2>
        {canEdit && (
          <span className="text-body-secondary small">
            {items.length} / {max}
          </span>
        )}
      </div>

      {canEdit && <p className="text-body-secondary small">{t('playlists.whatItIs')}</p>}

      {error && <Alert variant="danger" className="py-2">{error}</Alert>}

      {items.length === 0 && (
        <p className="text-body-secondary small mb-2">{t('playlists.emptySelf')}</p>
      )}

      {items.length > 0 && (
        <HorizontalStrip className="mb-2" itemWidth={148}>
          {items.map((playlist) => (
            <div key={playlist.id} className="playlist-card">
              <button
                type="button"
                className="playlist-open"
                onClick={() => setPlaying(playlist)}
                title={t('playlists.play')}
              >
                <Cover playlist={playlist} />

                <span className="playlist-caption">
                  <span className="text-truncate d-block small fw-semibold">
                    {playlist.title ?? t('playlists.untitled')}
                  </span>
                  <span className="text-truncate d-block text-body-secondary"
                        style={{ fontSize: '.75rem' }}>
                    {t(`posts.providers.${playlist.provider}`)}
                  </span>
                </span>
              </button>

              {canEdit && (
                <button
                  type="button"
                  className="favorite-remove"
                  onClick={() => remove(playlist.id)}
                  aria-label={t('common.delete')}
                  title={t('common.delete')}
                >
                  ×
                </button>
              )}
            </div>
          ))}
        </HorizontalStrip>
      )}

      {canEdit && items.length < max && (
        <Form onSubmit={add}>
          <Form.Label htmlFor="playlistUrl" className="small mb-1">
            {t('playlists.addHint')}
          </Form.Label>
          <InputGroup size="sm" hasValidation>
            <InputGroup.Text><IconLink /></InputGroup.Text>
            <Form.Control
              id="playlistUrl"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder={t('playlists.placeholder')}
              disabled={adding}
              isInvalid={Boolean(clientError)}
            />
            <Button type="submit" disabled={adding || !url.trim() || Boolean(clientError)}>
              {adding ? t('playlists.adding') : t('playlists.add')}
            </Button>
            {clientError && (
              <Form.Control.Feedback type="invalid">{t(clientError)}</Form.Control.Feedback>
            )}
          </InputGroup>
        </Form>
      )}

      {canEdit && items.length >= max && (
        <p className="text-body-secondary small mb-0">{t('playlists.full')}</p>
      )}

      <Player playlist={playing} onHide={() => setPlaying(null)} />
    </section>
  );
}

/**
 * Okladka playlisty z zapasowym wygladem.
 *
 * <p>Adresy zdjec pochodza z cudzego CDN-u, wiec trzeba zalozyc, ze czasem
 * sie nie wczytaja. Puste miejsce po nieudanym obrazku wyglada jak bledny
 * uklad strony - dlatego wtedy pokazujemy ikone.</p>
 */
function Cover({ playlist }) {
  const [failed, setFailed] = useState(false);

  if (!playlist.thumbnailUrl || failed) {
    return (
      <span className="playlist-cover cover-placeholder" aria-hidden="true">
        <IconPlaylist size={22} />
      </span>
    );
  }

  return (
    <img
      src={playlist.thumbnailUrl}
      alt=""
      className="playlist-cover"
      loading="lazy"
      onError={() => setFailed(true)}
    />
  );
}

/**
 * Odtwarzacz w okienku.
 *
 * <p>Ramka powstaje dopiero razem z okienkiem, wiec zamkniecie go faktycznie
 * przerywa granie - inaczej muzyka leciałaby dalej z niewidocznego elementu.</p>
 */
function Player({ playlist, onHide }) {
  const { t } = useTranslation();

  if (!playlist) {
    return null;
  }

  const height = playerHeight(playlist.provider, 'PLAYLIST');
  const hasVideo = height === null;

  return (
    <Modal show onHide={onHide} centered size="lg">
      <Modal.Header closeButton>
        <Modal.Title as="h2" className="h6 mb-0 text-truncate">
          {playlist.title ?? t('playlists.untitled')}
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        <div className={`player-frame${hasVideo ? ' ratio ratio-16x9' : ''}`}>
          <iframe
            src={playlist.embedUrl}
            title={playlist.title ?? t('playlists.title')}
            width="100%"
            height={height ?? undefined}
            allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"
            allowFullScreen={hasVideo}
          />
        </div>

        <a
          href={playlist.pageUrl}
          target="_blank"
          rel="noreferrer"
          className="small d-inline-block mt-2"
        >
          {t('playlists.openInService', {
            service: t(`posts.providers.${playlist.provider}`),
          })}
        </a>
      </Modal.Body>
    </Modal>
  );
}
