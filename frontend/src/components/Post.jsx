import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Avatar from './Avatar';
import GaleriaZdjec from './GaleriaZdjec';
import Pole from './Pole';
import { IkonaKosz, IkonaOlowek } from './Ikony';
import client, { opiszBlad } from '../api/client';
import { sformatujDate } from '../utils/daty';
import { naMinuty, naSekundy } from '../utils/czas';

/**
 * Pojedynczy post na tablicy: autor, tresc, zdjecia i odtwarzacz Spotify.
 *
 * <p>Autor moze post edytowac i usunac, administrator - tylko usunac
 * (moderacja polega na kasowaniu, nie na przerabianiu cudzych tresci).
 * O tym, ktore przyciski sie pokazuja, decyduja pola {@code canEdit}
 * i {@code canDelete} wyliczane przez SERWER.</p>
 */
export default function Post({ post, onDelete, onUpdate }) {
  const { t, i18n } = useTranslation();
  const [edycja, setEdycja] = useState(false);

  return (
    <Card className="mb-3">
      <Card.Body>
        <div className="d-flex align-items-center gap-2 mb-3">
          <Avatar avatarUrl={post.authorAvatarUrl} username={post.authorUsername} rozmiar={40} />

          <div className="flex-grow-1">
            <div className="fw-semibold">{post.authorUsername}</div>
            <div className="text-body-secondary small">
              {sformatujDate(post.createdAt, i18n.language)}
            </div>
          </div>

          {!edycja && (
            <div className="d-flex gap-2">
              {post.canEdit && (
                <Button
                  variant="outline-secondary"
                  size="sm"
                  onClick={() => setEdycja(true)}
                  title={t('posts.edit')}
                >
                  <IkonaOlowek /> <span className="d-none d-sm-inline">{t('posts.edit')}</span>
                </Button>
              )}

              {post.canDelete && (
                <Button
                  variant="outline-danger"
                  size="sm"
                  onClick={() => onDelete(post.id)}
                  title={t('common.delete')}
                >
                  <IkonaKosz /> <span className="d-none d-sm-inline">{t('common.delete')}</span>
                </Button>
              )}
            </div>
          )}
        </div>

        {edycja ? (
          <FormularzEdycji
            post={post}
            onZapisano={(zaktualizowany) => {
              onUpdate(zaktualizowany);
              setEdycja(false);
            }}
            onAnuluj={() => setEdycja(false)}
          />
        ) : (
          <>
            {/* tresc-postu zachowuje przejscia do nowej linii wpisane przez autora */}
            <Card.Text className="tresc-postu">{post.content}</Card.Text>

            {post.imageUrls.length > 0 && (
              <div className="mb-3">
                <GaleriaZdjec adresy={post.imageUrls} autor={post.authorUsername} />
              </div>
            )}

            {post.spotifyEmbedUrl && (
              /*
               * Zaokraglenie musi byc na OTOCZCE z overflow: hidden, a nie na
               * samej ramce. Strona Spotify w srodku ma wlasne, prostokatne tlo -
               * przy border-radius na iframe wystawalo ono w rogach jako biale
               * naroznikii.
               */
              <div className="ramka-spotify">
                <iframe
                  src={post.spotifyEmbedUrl}
                  title={`Spotify - ${post.authorUsername}`}
                  width="100%"
                  height="152"
                  allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"
                  loading="lazy"
                />
              </div>
            )}
          </>
        )}
      </Card.Body>
    </Card>
  );
}

/** Formularz edycji - tresc i utwor. Zdjec nie da sie zmienic po opublikowaniu. */
function FormularzEdycji({ post, onZapisano, onAnuluj }) {
  const { t } = useTranslation();

  const [content, setContent] = useState(post.content);
  const [spotifyUrl, setSpotifyUrl] = useState(post.spotifyUrl ?? '');
  const [startAt, setStartAt] = useState(naMinuty(post.spotifyStartSeconds) || '');

  const [bledyPol, setBledyPol] = useState({});
  const [bladOgolny, setBladOgolny] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  async function wyslij(e) {
    e.preventDefault();
    setBledyPol({});
    setBladOgolny(null);
    setWysylanie(true);

    try {
      const odpowiedz = await client.put(`/posts/${post.id}`, {
        content,
        spotifyUrl: spotifyUrl || null,
        spotifyStartSeconds: naSekundy(startAt),
      });
      onZapisano(odpowiedz.data);
    } catch (error) {
      const opis = opiszBlad(error);
      setBledyPol(opis.fieldErrors);
      setBladOgolny(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <Form onSubmit={wyslij} noValidate>
      {bladOgolny && <Alert variant="danger">{bladOgolny}</Alert>}

      <Pole
        id={`content-${post.id}`}
        label={t('posts.content')}
        wartosc={content}
        onChange={setContent}
        blad={bledyPol.content}
        jakoObszarTekstu
        wiersze={3}
      />

      <Row>
        <Col md={8}>
          <Pole
            id={`spotify-${post.id}`}
            label={t('posts.spotify')}
            wartosc={spotifyUrl}
            onChange={setSpotifyUrl}
            blad={bledyPol.spotifyUrl}
            podpowiedz={t('posts.spotifyClearHint')}
            placeholder={t('posts.spotifyPlaceholder')}
            wymagane={false}
          />
        </Col>
        <Col md={4}>
          <Pole
            id={`start-${post.id}`}
            label={t('posts.startAt')}
            wartosc={startAt}
            onChange={setStartAt}
            blad={bledyPol.spotifyStartSeconds}
            podpowiedz={t('posts.startAtHint')}
            placeholder="1:23"
            wymagane={false}
          />
        </Col>
      </Row>

      {post.imageUrls.length > 0 && (
        <p className="text-body-secondary small">{t('posts.imagesNotEditable')}</p>
      )}

      <div className="d-flex gap-2">
        <Button type="submit" size="sm" disabled={wysylanie}>
          {wysylanie ? t('settings.saving') : t('common.save')}
        </Button>
        <Button type="button" size="sm" variant="outline-secondary" onClick={onAnuluj}>
          {t('common.cancel')}
        </Button>
      </div>
    </Form>
  );
}
