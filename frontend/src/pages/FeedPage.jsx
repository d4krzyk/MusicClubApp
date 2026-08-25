import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Spinner from 'react-bootstrap/Spinner';
import client, { opiszBlad } from '../api/client';
import Pole from '../components/Pole';
import Post from '../components/Post';
import { naSekundy } from '../utils/czas';

/** Ile postow pobieramy za jednym razem. */
const NA_STRONE = 10;

/**
 * Tablica: formularz nowego posta i lista wpisow.
 *
 * <p>Posty doladowujemy przyciskiem "pokaz starsze" zamiast klasycznego
 * stronicowania z numerami - na tablicy spolecznosciowej naturalniej jest
 * doklejac kolejne wpisy pod spodem. Backend i tak stronicuje normalnie
 * (wymagania nr 3 i 5), tylko frontend inaczej to pokazuje.</p>
 */
export default function FeedPage() {
  const { t } = useTranslation();

  const [posty, setPosty] = useState([]);
  const [strona, setStrona] = useState(0);
  const [ostatnia, setOstatnia] = useState(true);
  const [ladowanie, setLadowanie] = useState(true);
  const [bladListy, setBladListy] = useState(null);
  const [komunikat, setKomunikat] = useState(null);

  const pobierz = useCallback(async (numerStrony, dolacz) => {
    setLadowanie(true);
    setBladListy(null);
    try {
      const odpowiedz = await client.get('/posts', {
        params: { page: numerStrony, size: NA_STRONE, direction: 'desc' },
      });
      const dane = odpowiedz.data;

      setPosty((poprzednie) => (dolacz ? [...poprzednie, ...dane.content] : dane.content));
      setOstatnia(dane.last);
      setStrona(dane.number);
    } catch (error) {
      const opis = opiszBlad(error);
      setBladListy(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    } finally {
      setLadowanie(false);
    }
  }, [t]);

  useEffect(() => {
    pobierz(0, false);
  }, [pobierz]);

  function poDodaniu(nowy) {
    // Nowy post ma byc na gorze - to najszybszy sposob, bez ponownego pobierania
    setPosty((poprzednie) => [nowy, ...poprzednie]);
    setKomunikat(t('posts.published'));
  }

  async function usun(id) {
    if (!window.confirm(t('common.confirmDelete'))) {
      return;
    }
    try {
      await client.delete(`/posts/${id}`);
      setPosty((poprzednie) => poprzednie.filter((p) => p.id !== id));
      setKomunikat(t('posts.deleted'));
    } catch (error) {
      const opis = opiszBlad(error);
      setBladListy(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    }
  }

  return (
    <Row className="justify-content-center">
      <Col lg={8}>
        <FormularzPostu onDodano={poDodaniu} />

        {komunikat && (
          <Alert variant="success" dismissible onClose={() => setKomunikat(null)}>
            {komunikat}
          </Alert>
        )}
        {bladListy && <Alert variant="danger">{bladListy}</Alert>}

        {posty.map((post) => (
          <Post key={post.id} post={post} onDelete={usun} />
        ))}

        {!ladowanie && posty.length === 0 && !bladListy && (
          <p className="text-body-secondary text-center py-4">{t('posts.empty')}</p>
        )}

        {ladowanie && (
          <div className="text-center py-3 text-body-secondary">
            <Spinner animation="border" size="sm" className="me-2" />
            {t('common.loading')}
          </div>
        )}

        {!ladowanie && !ostatnia && (
          <div className="text-center">
            <Button variant="outline-secondary" onClick={() => pobierz(strona + 1, true)}>
              {t('posts.loadMore')}
            </Button>
          </div>
        )}
      </Col>
    </Row>
  );
}

/** Formularz dodawania posta: tekst, zdjecia i utwor ze Spotify. */
function FormularzPostu({ onDodano }) {
  const { t } = useTranslation();

  const [content, setContent] = useState('');
  const [spotifyUrl, setSpotifyUrl] = useState('');
  const [startAt, setStartAt] = useState('');
  const [pliki, setPliki] = useState([]);

  const [bledyPol, setBledyPol] = useState({});
  const [bladOgolny, setBladOgolny] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  function wyczysc() {
    setContent('');
    setSpotifyUrl('');
    setStartAt('');
    setPliki([]);
  }

  async function wyslij(e) {
    e.preventDefault();
    setBledyPol({});
    setBladOgolny(null);
    setWysylanie(true);

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
            spotifyUrl: spotifyUrl || null,
            spotifyStartSeconds: naSekundy(startAt),
          })],
          { type: 'application/json' }
        )
      );
      pliki.forEach((plik) => formData.append('images', plik));

      const odpowiedz = await client.post('/posts', formData);

      wyczysc();
      onDodano(odpowiedz.data);
    } catch (error) {
      const opis = opiszBlad(error);
      setBledyPol(opis.fieldErrors);
      setBladOgolny(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <Card className="mb-4">
      <Card.Body>
        <Card.Title as="h2" className="h5 mb-3">
          {t('posts.newPost')}
        </Card.Title>

        {bladOgolny && <Alert variant="danger">{bladOgolny}</Alert>}

        <Form onSubmit={wyslij} noValidate>
          <Pole
            id="content"
            label={t('posts.content')}
            wartosc={content}
            onChange={setContent}
            blad={bledyPol.content}
            placeholder={t('posts.contentPlaceholder')}
            jakoObszarTekstu
            wiersze={3}
          />

          <Form.Group className="mb-3" controlId="images">
            <Form.Label>{t('posts.images')}</Form.Label>
            <Form.Control
              type="file"
              accept="image/*"
              multiple
              isInvalid={Boolean(bledyPol.images)}
              onChange={(e) => setPliki(Array.from(e.target.files).slice(0, 10))}
            />
            {bledyPol.images ? (
              <Form.Control.Feedback type="invalid">{bledyPol.images}</Form.Control.Feedback>
            ) : (
              <Form.Text muted>
                {pliki.length > 0
                  ? t('posts.imagesSelected', { count: pliki.length })
                  : t('posts.imagesHint')}
              </Form.Text>
            )}
          </Form.Group>

          <Row>
            <Col md={8}>
              <Pole
                id="spotifyUrl"
                label={t('posts.spotify')}
                wartosc={spotifyUrl}
                onChange={setSpotifyUrl}
                blad={bledyPol.spotifyUrl}
                podpowiedz={t('posts.spotifyHint')}
                placeholder={t('posts.spotifyPlaceholder')}
                wymagane={false}
              />
            </Col>
            <Col md={4}>
              <Pole
                id="startAt"
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

          {/* Uczciwe ostrzezenie - Spotify bywa kapryśne z parametrem czasu */}
          {startAt && spotifyUrl && (
            <p className="text-body-secondary small">{t('posts.startAtWarning')}</p>
          )}

          <Button type="submit" disabled={wysylanie}>
            {wysylanie ? t('posts.publishing') : t('posts.publish')}
          </Button>
        </Form>
      </Card.Body>
    </Card>
  );
}
