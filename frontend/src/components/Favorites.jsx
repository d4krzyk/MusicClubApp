import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import InputGroup from 'react-bootstrap/InputGroup';
import { describeError } from '../api/client';
import * as profil from '../api/profil';
import { useAuth } from '../auth/AuthContext';
import CatalogSearch from './CatalogSearch';
import HorizontalStrip from './HorizontalStrip';

/** Ulubieni artysci i utwory na profilu. */
export default function Favorites({ username, onChange }) {
  const { t } = useTranslation();
  const { user } = useAuth();

  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [importAvailable, setImportAvailable] = useState(false);
  const [showImport, setShowImport] = useState(false);
  const [lastFmUsername, setLastFmUsername] = useState('');
  const [importing, setImporting] = useState(false);
  const [summary, setSummary] = useState(null);

  const fetch = useCallback(async () => {
    try {
      setData(await profil.ulubieni(username));
    } catch {
      // Section ulubionych to czesc profilu, a nie caly profil - gdy padnie,
      // reszta strony ma dzialac dalej
      setData(null);
    }
  }, [username]);

  useEffect(() => {
    fetch();
  }, [fetch]);

  useEffect(() => {
    profil.stanImportuLastFm()
      .then((stan) => setImportAvailable(stan.available))
      .catch(() => setImportAvailable(false));
  }, []);

  /** Kazda zmiana ulubionych zwraca nowa, pelna liste - podmieniamy ja w calosci. */
  async function run(query) {
    setError(null);
    try {
      setData(await query());
      onChange?.();
    } catch (error) {
      const details = describeError(error);
      setError(details.message);
    }
  }

  async function runImport(event) {
    event.preventDefault();
    setImporting(true);
    setError(null);
    setSummary(null);
    try {
      setSummary(await profil.importujZLastFm(lastFmUsername));
      await fetch();
      onChange?.();
    } catch (error) {
      const details = describeError(error);
      setError(details.message ?? details.fieldErrors.username);
    } finally {
      setImporting(false);
    }
  }

  if (!data) {
    return null;
  }

  const { artists, tracks, canEdit, maxArtists, maxTracks } = data;

  // Nie pokazujemy pustej sekcji na cudzym profilu - to tylko dziura na stronie
  if (!canEdit && artists.length === 0 && tracks.length === 0) {
    return null;
  }

  return (
    <section className="profil-panel mb-4">
      <div className="d-flex align-items-center justify-content-between mb-2">
        <h2 className="h5 mb-0">{t('favorites.title')}</h2>

        {canEdit && importAvailable && (
          <Button
            size="sm"
            variant="outline-secondary"
            onClick={() => setShowImport((p) => !p)}
          >
            {t('favorites.importLastFm')}
          </Button>
        )}
      </div>

      {canEdit && (
        <p className="text-body-secondary small">{t('favorites.whyItMatters')}</p>
      )}

      {/*
        Gdy importu nie ma, przycisk sie nie pojawia - i wlasciciel profilu nie ma jak sie
        domyslic, dlaczego.
      */}
      {canEdit && !importAvailable && (
        <p className="text-body-secondary small mb-2">
          {t('favorites.importDisabled')}
          {user?.admin && ' ' + t('favorites.importDisabledAdmin')}
        </p>
      )}

      {error && <Alert variant="danger" className="py-2">{error}</Alert>}

      {summary && (
        <Alert variant="success" className="py-2 small">
          {t('favorites.importSummary', {
            artists: summary.addedArtists,
            tracks: summary.addedTracks,
          })}
          {/* Pominiete i juz istniejace pokazujemy OSOBNO. */}
          {summary.skipped > 0
            && ' ' + t('favorites.importSkipped', { count: summary.skipped })}
          {summary.alreadyPresent > 0
            && ' ' + t('favorites.importAlready', { count: summary.alreadyPresent })}
          {summary.limitReached && ' ' + t('favorites.importLimit')}
        </Alert>
      )}

      {canEdit && showImport && (
        <Form onSubmit={runImport} className="mb-3">
          <Form.Label htmlFor="lastFmUsername" className="small mb-1">
            {t('favorites.lastFmHint')}
          </Form.Label>
          <InputGroup size="sm">
            <Form.Control
              id="lastFmUsername"
              value={lastFmUsername}
              onChange={(e) => setLastFmUsername(e.target.value)}
              placeholder={t('favorites.lastFmPlaceholder')}
              disabled={importing}
            />
            <Button type="submit" disabled={importing || !lastFmUsername.trim()}>
              {importing ? t('favorites.importing') : t('favorites.importAction')}
            </Button>
          </InputGroup>
        </Form>
      )}

      <Section
        title={t('favorites.artists')}
        items={artists}
        emptyText={t(canEdit ? 'favorites.noArtistsSelf' : 'favorites.noArtists')}
        canEdit={canEdit}
        limitReached={artists.length >= maxArtists}
        kind="artists"
        onAdd={(p) => run(() => profil.dodajUlubionegoArtyste(p.externalId))}
        onRemove={(p) => run(() => profil.usunUlubionegoArtyste(p.externalId))}
      />

      <Section
        title={t('favorites.tracks')}
        items={tracks}
        emptyText={t(canEdit ? 'favorites.noTracksSelf' : 'favorites.noTracks')}
        canEdit={canEdit}
        limitReached={tracks.length >= maxTracks}
        kind="tracks"
        onAdd={(p) => run(() => profil.dodajUlubionyUtwor(p.externalId))}
        onRemove={(p) => run(() => profil.usunUlubionyUtwor(p.externalId))}
      />
    </section>
  );
}

/** Cover kafelka z zapasowym wygladem. */
function Cover({ url, caption }) {
  const [failed, setFailed] = useState(false);

  if (!url || failed) {
    return (
      <div className="favorite-cover cover-placeholder" aria-hidden="true">
        {(caption ?? '?').trim().charAt(0).toUpperCase()}
      </div>
    );
  }

  return (
    <img
      src={url}
      alt=""
      className="favorite-cover"
      loading="lazy"
      onError={() => setFailed(true)}
    />
  );
}

/** Jeden blok: naglowek, kafelki i (u wlasciciela) wyszukiwarka. */
function Section({ title, items, emptyText, canEdit, limitReached, kind, onAdd, onRemove }) {
  const { t } = useTranslation();

  return (
    <div className="mb-3">
      <h3 className="h6 text-body-secondary">{title}</h3>

      {items.length === 0 && <p className="text-body-secondary small mb-2">{emptyText}</p>}

      {/* Pasek poziomy zamiast zawijanej siatki. */}
      {items.length > 0 && (
        <HorizontalStrip className="mb-2" itemWidth={132}>
          {items.map((p, i) => (
            <div key={p.externalId} className="favorite-card mc-wejscie" style={{ '--i': i }}>
              <Cover url={p.imageUrl} caption={p.name ?? p.title} />

              <div className="favorite-caption">
                <div className="text-truncate small">{p.name ?? p.title}</div>
                {p.artistName && (
                  <div className="text-truncate text-body-secondary" style={{ fontSize: '.75rem' }}>
                    {p.artistName}
                  </div>
                )}
              </div>

              {canEdit && (
                <button
                  type="button"
                  className="favorite-remove"
                  onClick={() => onRemove(p)}
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

      {canEdit && (
        limitReached
          ? <p className="text-body-secondary small mb-0">{t('favorites.limitReached')}</p>
          : <CatalogSearch kind={kind} onWybor={onAdd} />
      )}
    </div>
  );
}
