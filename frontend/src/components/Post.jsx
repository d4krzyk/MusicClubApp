import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Avatar from './Avatar';
import GaleriaZdjec from './GaleriaZdjec';
import Pole from './Pole';
import Reakcje from './Reakcje';
import WyborMuzyki from './WyborMuzyki';
import { IkonaKosz, IkonaOlowek } from './Ikony';
import client, { opiszBlad } from '../api/client';
import { sformatujDate } from '../utils/daty';
import { naMinuty, naSekundy } from '../utils/czas';
import { bladLinku } from '../utils/linkiMuzyczne';

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
          {/*
            Awatar i nazwa prowadza na profil autora - tak jak na Facebooku.
            Oba sa w JEDNYM linku, zeby czytnik ekranu przeczytal to jako
            jedno odniesienie ("profil uzytkownika X"), a nie dwa osobne.
          */}
          <Link
            to={`/profil/${post.authorUsername}`}
            className="d-flex align-items-center gap-2 text-decoration-none text-body"
            title={t('profile.visit', { username: post.authorUsername })}
          >
            <Avatar avatarUrl={post.authorAvatarUrl} username={post.authorUsername} rozmiar={40} />
            <span className="fw-semibold link-autora">{post.authorUsername}</span>
          </Link>

          <div className="flex-grow-1">
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

            {post.musicEmbedUrl && <Odtwarzacz post={post} />}

            <Reakcje post={post} onZmiana={onUpdate} />
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
  const [musicUrl, setMusicUrl] = useState(post.musicUrl ?? '');
  const [musicKind, setMusicKind] = useState(post.musicKind ?? 'TRACK');
  const [startAt, setStartAt] = useState(naMinuty(post.musicStartSeconds) || '');

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
        musicUrl: musicUrl || null,
        musicKind: musicUrl ? musicKind : null,
        musicStartSeconds: musicKind === 'TRACK' ? naSekundy(startAt) : null,
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

      <WyborMuzyki
        rodzaj={musicKind}
        onRodzaj={setMusicKind}
        link={musicUrl}
        onLink={setMusicUrl}
        moment={startAt}
        onMoment={setStartAt}
        bledySerwera={bledyPol}
      />

      <p className="text-body-secondary small">{t('posts.musicClearHint')}</p>

      {post.imageUrls.length > 0 && (
        <p className="text-body-secondary small">{t('posts.imagesNotEditable')}</p>
      )}

      <div className="d-flex gap-2">
        <Button type="submit" size="sm" disabled={wysylanie || Boolean(bladLinku(musicUrl, musicKind))}>
          {wysylanie ? t('settings.saving') : t('common.save')}
        </Button>
        <Button type="button" size="sm" variant="outline-secondary" onClick={onAnuluj}>
          {t('common.cancel')}
        </Button>
      </div>
    </Form>
  );
}

/**
 * Odtwarzacz nagrania - dziala tak samo dla kazdego serwisu.
 *
 * <p><b>Adres skladamy na SERWERZE</b>, nie tutaj. Kazdy serwis ma inny format
 * adresu osadzenia (i inny parametr momentu startu), a gdyby wiedza o tym
 * siedziala w Reakcie, dolozenie trzeciego serwisu wymagaloby zmian
 * w dwoch miejscach.</p>
 *
 * <p>Wysokosc zalezy od rodzaju: album i profil artysty pokazuja liste
 * nagran, wiec potrzebuja wiecej miejsca niz pojedynczy utwor.</p>
 */
function Odtwarzacz({ post }) {
  const { t } = useTranslation();

  const wysokosc = post.musicKind === 'TRACK' ? 152 : 352;

  return (
    <div>
      {/*
        Zaokraglenie musi byc na OTOCZCE z overflow: hidden, a nie na samej
        ramce. Strona serwisu w srodku ma wlasne, prostokatne tlo - przy
        border-radius na iframe wystawalo ono w rogach jako biale narozniki.
      */}
      <div className="ramka-spotify">
        <iframe
          src={post.musicEmbedUrl}
          title={post.musicTitle ?? `${post.musicProvider} - ${post.authorUsername}`}
          width="100%"
          height={wysokosc}
          allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"
          loading="lazy"
        />
      </div>

      {/*
        Tytul pobrany przy dodawaniu posta. Moze go nie byc, gdy serwis
        wtedy nie odpowiedzial - wtedy pokazujemy sama nazwe serwisu,
        bo odtwarzacz i tak wyswietla wszystko sam.
      */}
      <div className="text-body-secondary small mt-1 d-flex gap-2 align-items-center">
        <span>{t(`posts.providers.${post.musicProvider}`)}</span>
        {post.musicTitle && <span className="text-truncate">· {post.musicTitle}</span>}
      </div>
    </div>
  );
}
