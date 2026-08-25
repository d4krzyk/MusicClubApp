import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Spinner from 'react-bootstrap/Spinner';
import client, { opiszBlad } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import Avatar from '../components/Avatar';
import Post from '../components/Post';
import { sformatujDate } from '../utils/daty';

/** Ile postow pobieramy za jednym razem. */
const NA_STRONE = 10;

/**
 * Profil uzytkownika - wlasny albo cudzy.
 *
 * <p><b>Jedna strona na oba przypadki.</b> Roznica sprowadza sie do kilku
 * przyciskow, wiec osobny komponent na "moj profil" oznaczalby dwa pliki
 * robiace prawie to samo - i dwa miejsca do poprawiania przy kazdej zmianie.
 * O tym, ktory wariant widzimy, decyduje pole {@code self} <b>z serwera</b>.</p>
 *
 * <p>Adres {@code /profil} (bez nazwy) pokazuje profil zalogowanego
 * uzytkownika - wygodny link z menu.</p>
 *
 * <p>Miejsce na liste znajomych i ulubionych artystow ze Spotify jest
 * przygotowane nizej - dojda w kolejnych krokach.</p>
 */
export default function ProfilePage() {
  const { t, i18n } = useTranslation();
  const { username } = useParams();
  const { user } = useAuth();

  // Bez nazwy w adresie ogladamy siebie
  const kogo = username ?? user?.username;

  const [profil, setProfil] = useState(null);
  const [posty, setPosty] = useState([]);
  const [strona, setStrona] = useState(0);
  const [ostatnia, setOstatnia] = useState(true);
  const [ladowanie, setLadowanie] = useState(true);
  const [blad, setBlad] = useState(null);

  const pobierzProfil = useCallback(async () => {
    setLadowanie(true);
    setBlad(null);
    try {
      const odpowiedz = await client.get(`/profiles/${encodeURIComponent(kogo)}`);
      setProfil(odpowiedz.data);
    } catch (error) {
      /*
       * Przy 404 pokazujemy WLASNY komunikat. Serwer odsyla ogolne
       * "Nie znaleziono: user o identyfikatorze ...", co jest w porzadku
       * w logach i w Swaggerze, ale odwiedzajacemu profil nic nie mowi -
       * "user" to nazwa z kodu, nie slowo z jego swiata.
       */
      const opis = opiszBlad(error);
      setBlad(error.response?.status === 404
        ? t('profile.notFound')
        : opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
      setProfil(null);
    } finally {
      setLadowanie(false);
    }
  }, [kogo, t]);

  const pobierzPosty = useCallback(async (numerStrony, dolacz) => {
    try {
      const odpowiedz = await client.get('/posts', {
        params: { page: numerStrony, size: NA_STRONE, direction: 'desc', author: kogo },
      });
      const dane = odpowiedz.data;

      setPosty((poprzednie) => (dolacz ? [...poprzednie, ...dane.content] : dane.content));
      setOstatnia(dane.last);
      setStrona(dane.number);
    } catch (error) {
      const opis = opiszBlad(error);
      setBlad(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    }
  }, [kogo, t]);

  useEffect(() => {
    if (!kogo) {
      return;
    }
    // Nowy profil = czyscimy poprzednie posty, inaczej mignelyby cudze wpisy
    setPosty([]);
    pobierzProfil();
    pobierzPosty(0, false);
  }, [kogo, pobierzProfil, pobierzPosty]);

  function poZmianiePostu(zaktualizowany) {
    setPosty((poprzednie) =>
      poprzednie.map((p) => (p.id === zaktualizowany.id ? zaktualizowany : p)));
  }

  async function usunPost(id) {
    if (!window.confirm(t('common.confirmDelete'))) {
      return;
    }
    try {
      await client.delete(`/posts/${id}`);
      setPosty((poprzednie) => poprzednie.filter((p) => p.id !== id));
      // Licznik postow w naglowku musi sie zgadzac z tym, co widac nizej
      setProfil((p) => (p ? { ...p, postCount: Math.max(p.postCount - 1, 0) } : p));
    } catch (error) {
      const opis = opiszBlad(error);
      setBlad(opis.message ?? (opis.messageKey ? t(opis.messageKey) : null));
    }
  }

  if (ladowanie && !profil) {
    return (
      <div className="text-center py-5 text-body-secondary">
        <Spinner animation="border" size="sm" className="me-2" />
        {t('common.loading')}
      </div>
    );
  }

  if (!profil) {
    return (
      <Row className="justify-content-center">
        <Col lg={8}>
          <Alert variant="danger">{blad ?? t('profile.notFound')}</Alert>
          <Link to="/feed" className="btn btn-outline-secondary">
            {t('menu.feed')}
          </Link>
        </Col>
      </Row>
    );
  }

  return (
    <Row className="justify-content-center">
      <Col lg={8}>
        <Card className="mb-4">
          <Card.Body className="d-flex align-items-center gap-3 flex-wrap">
            <Avatar avatarUrl={profil.avatarUrl} username={profil.username} rozmiar={80} />

            <div className="flex-grow-1">
              <h1 className="h4 mb-1">{profil.username}</h1>
              <div className="text-body-secondary small">
                {t('profile.memberSince', {
                  date: sformatujDate(profil.createdAt, i18n.language),
                })}
              </div>
              <div className="text-body-secondary small">
                {t('profile.postCount', { count: profil.postCount })}
              </div>
            </div>

            {/*
              Ustawienia konta (login, e-mail, haslo) sa czyms innym niz profil -
              dlatego przycisk prowadzi do osobnej strony i widzi go tylko
              wlasciciel. O tym, czy to jego profil, mowi serwer.
            */}
            {profil.self && (
              <Link to="/settings" className="btn btn-outline-secondary btn-sm">
                {t('profile.editAccount')}
              </Link>
            )}
          </Card.Body>
        </Card>

        {/*
          Tu w kolejnych krokach dojda ulubieni artysci i utwory ze Spotify
          oraz lista znajomych z klikalnymi miniaturami.
        */}

        <h2 className="h5 mb-3">{t('profile.posts')}</h2>

        {blad && <Alert variant="danger">{blad}</Alert>}

        {posty.map((post) => (
          <Post key={post.id} post={post} onDelete={usunPost} onUpdate={poZmianiePostu} />
        ))}

        {posty.length === 0 && (
          <p className="text-body-secondary text-center py-4">
            {profil.self ? t('profile.noPostsSelf') : t('profile.noPosts')}
          </p>
        )}

        {!ostatnia && (
          <div className="text-center">
            <Button variant="outline-secondary" onClick={() => pobierzPosty(strona + 1, true)}>
              {t('posts.loadMore')}
            </Button>
          </div>
        )}
      </Col>
    </Row>
  );
}
