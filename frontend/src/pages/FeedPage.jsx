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
import client, { opiszBlad } from '../api/client';
import Pole from '../components/Pole';
import Post from '../components/Post';
import WybieraczZdjec from '../components/WybieraczZdjec';
import WyborMuzyki from '../components/WyborMuzyki';
import { IkonaKrzyzyk, IkonaPlus } from '../components/Ikony';
import { naSekundy } from '../utils/czas';
import { bladLinku } from '../utils/linkiMuzyczne';

/** Ile postow pobieramy za jednym razem. */
const NA_STRONE = 10;

/** Limit zdjec w jednym poscie - taki sam jak po stronie backendu. */
const MAKS_ZDJEC = 10;

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

  const [posty, setPosty] = useState([]);
  const [strona, setStrona] = useState(0);
  const [ostatnia, setOstatnia] = useState(true);
  const [ladowanie, setLadowanie] = useState(true);
  const [bladListy, setBladListy] = useState(null);
  const [komunikat, setKomunikat] = useState(null);
  const [formularzOtwarty, setFormularzOtwarty] = useState(false);

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
    setFormularzOtwarty(false);
  }

  function poEdycji(zaktualizowany) {
    setPosty((poprzednie) =>
      poprzednie.map((p) => (p.id === zaktualizowany.id ? zaktualizowany : p)));
    setKomunikat(t('posts.updated'));
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
        <div className="d-flex align-items-center justify-content-between mb-3">
          <h1 className="h4 mb-0">{t('posts.title')}</h1>

          <Button
            variant={formularzOtwarty ? 'outline-secondary' : 'primary'}
            onClick={() => setFormularzOtwarty((otwarty) => !otwarty)}
            aria-expanded={formularzOtwarty}
            aria-controls="formularz-postu"
          >
            {formularzOtwarty ? (
              <>
                <IkonaKrzyzyk /> {t('common.cancel')}
              </>
            ) : (
              <>
                <IkonaPlus /> {t('posts.newPost')}
              </>
            )}
          </Button>
        </div>

        <Collapse in={formularzOtwarty}>
          <div id="formularz-postu">
            <FormularzPostu onDodano={poDodaniu} />
          </div>
        </Collapse>

        {komunikat && (
          <Alert variant="success" dismissible onClose={() => setKomunikat(null)}>
            {komunikat}
          </Alert>
        )}
        {bladListy && <Alert variant="danger">{bladListy}</Alert>}

        {posty.map((post) => (
          <Post key={post.id} post={post} onDelete={usun} onUpdate={poEdycji} />
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
  const [musicUrl, setMusicUrl] = useState('');
  const [musicKind, setMusicKind] = useState('TRACK');
  const [startAt, setStartAt] = useState('');
  const [pliki, setPliki] = useState([]);

  const [bledyPol, setBledyPol] = useState({});
  const [bladOgolny, setBladOgolny] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  function wyczysc() {
    setContent('');
    setMusicUrl('');
    setMusicKind('TRACK');
    setStartAt('');
    setPliki([]);
  }

  /*
   * Zly link BLOKUJE wysylke. Wczesniej taki adres byl po cichu polykany:
   * post powstawal bez odtwarzacza i bez slowa wyjasnienia, co dla
   * uzytkownika wygladalo jak zepsuta aplikacja.
   */
  const blokada = bladLinku(musicUrl, musicKind);

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
            musicUrl: musicUrl || null,
            // Bez linku rodzaj nie ma do czego sie odnosic - serwer to odrzuci
            musicKind: musicUrl ? musicKind : null,
            musicStartSeconds: musicKind === 'TRACK' ? naSekundy(startAt) : null,
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

          <WybieraczZdjec
            pliki={pliki}
            onZmiana={setPliki}
            maks={MAKS_ZDJEC}
            blad={bledyPol.images}
          />

          <WyborMuzyki
            rodzaj={musicKind}
            onRodzaj={setMusicKind}
            link={musicUrl}
            onLink={setMusicUrl}
            moment={startAt}
            onMoment={setStartAt}
            bledySerwera={bledyPol}
          />

          <Button type="submit" disabled={wysylanie || Boolean(blokada)}>
            {wysylanie ? t('posts.publishing') : t('posts.publish')}
          </Button>
        </Form>
      </Card.Body>
    </Card>
  );
}
