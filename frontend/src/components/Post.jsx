import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import Alert from 'react-bootstrap/Alert';
import Avatar from './Avatar';
import ImageGallery from './ImageGallery';
import Field from './Field';
import Reactions from './Reactions';
import MusicPicker from './MusicPicker';
import VisibilityPicker from './VisibilityPicker';
import ReportButton from './ReportButton';
import { IconTrash, IconPencil, IconLock } from './Icons';
import client, { describeError } from '../api/client';
import { formatDate } from '../utils/dates';
import { playerHeight } from '../utils/player';
import { toMinutes, toSeconds } from '../utils/time';
import { linkError } from '../utils/musicLinks';

/**
 * Pojedynczy post na tablicy: autor, tresc, zdjecia i odtwarzacz Spotify.
 *
 * <p>Autor moze post edytowac i usunac, administrator - tylko usunac
 * (moderacja polega na kasowaniu, nie na przerabianiu cudzych tresci).
 * O tym, ktore przyciski sie pokazuja, decyduja pola {@code canEdit}
 * i {@code canDelete} wyliczane przez SERWER.</p>
 */
export default function Post({ post, onDelete, onUpdate, onReaction, index = 0 }) {
  const { t, i18n } = useTranslation();
  const [edycja, setEdycja] = useState(false);

  return (
    /*
      --i steruje opoznieniem wejscia karty (patrz styles.css). Liczy sie od
      poczatku PARTII, a nie od poczatku listy - inaczej dwudziesty post
      czekalby prawie sekunde, zanim by sie pokazal.
    */
    <Card className="mb-3 post-card" style={{ '--i': index }}>
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
            <Avatar avatarUrl={post.authorAvatarUrl} username={post.authorUsername} size={40} />
            <span className="fw-semibold author-link">{post.authorUsername}</span>
          </Link>

          <div className="flex-grow-1 d-flex align-items-center gap-2 flex-wrap">
            <span className="text-body-secondary small">
              {formatDate(post.createdAt, i18n.language)}
            </span>

            {/*
              Plakietke pokazujemy TYLKO przy postach dla znajomych.
              Publiczny jest domyslny, wiec podpisywanie go "publiczny"
              byloby szumem przy kazdym wpisie na tablicy - a plakietka
              ma zwracac uwage wtedy, gdy cos odbiega od normy.
            */}
            {post.visibility === 'FRIENDS' && (
              <span className="visibility-badge" title={t('posts.visibility.FRIENDSHint')}>
                <IconLock size={11} />
                {t('posts.visibility.FRIENDS')}
              </span>
            )}
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
                  <IconPencil /> <span className="d-none d-sm-inline">{t('posts.edit')}</span>
                </Button>
              )}

              {post.canDelete && (
                <Button
                  variant="outline-danger"
                  size="sm"
                  onClick={() => onDelete(post.id)}
                  title={t('common.delete')}
                >
                  <IconTrash /> <span className="d-none d-sm-inline">{t('common.delete')}</span>
                </Button>
              )}

              {/*
                Zgloszenie posta - tylko przy CUDZYCH wpisach. Wlasny post
                sie kasuje, a nie zglasza; przycisk przy nim byloby tylko
                mylacy. O tym, czy post jest moj, mowi pole canEdit -
                wylicza je serwer i ma je wylacznie autor.
              */}
              {!post.canEdit && (
                <ReportButton
                  username={post.authorUsername}
                  contexts={['POST']}
                  postId={post.id}
                  compact
                />
              )}
            </div>
          )}
        </div>

        {edycja ? (
          <EditForm
            post={post}
            onSaved={(updated) => {
              onUpdate(updated);
              setEdycja(false);
            }}
            onAnuluj={() => setEdycja(false)}
          />
        ) : (
          <>
            {/* post-content zachowuje przejscia do nowej linii wpisane przez autora */}
            <Card.Text className="post-content">{post.content}</Card.Text>

            {post.imageUrls.length > 0 && (
              <div className="mb-3">
                <ImageGallery urls={post.imageUrls} author={post.authorUsername} />
              </div>
            )}

            {post.musicEmbedUrl && <Player post={post} />}

            {/*
              Reakcja NIE jest edycja posta. Wczesniej obie rzeczy szly tym
              samym wywolaniem i klikniecie emotki pod CUDZYM postem pokazywalo
              komunikat "Post zostal zaktualizowany" - czyli aplikacja mowila,
              ze zmienilismy cudza tresc.
            */}
            <Reactions post={post} onChange={onReaction ?? onUpdate} />
          </>
        )}
      </Card.Body>
    </Card>
  );
}

/** Formularz edycji - tresc i utwor. Zdjec nie da sie zmienic po opublikowaniu. */
function EditForm({ post, onSaved, onAnuluj }) {
  const { t } = useTranslation();

  const [content, setContent] = useState(post.content);
  const [musicUrl, setMusicUrl] = useState(post.musicUrl ?? '');
  const [musicKind, setMusicKind] = useState(post.musicKind ?? 'TRACK');
  const [startAt, setStartAt] = useState(toMinutes(post.musicStartSeconds) || '');
  const [visibility, setVisibility] = useState(post.visibility ?? 'PUBLIC');

  const [fieldErrors, setFieldErrors] = useState({});
  const [generalError, setGeneralError] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);
    setWysylanie(true);

    try {
      const response = await client.put(`/posts/${post.id}`, {
        content,
        musicUrl: musicUrl || null,
        musicKind: musicUrl ? musicKind : null,
        musicStartSeconds: musicKind === 'TRACK' ? toSeconds(startAt) : null,
        visibility,
      });
      onSaved(response.data);
    } catch (error) {
      const details = describeError(error);
      setFieldErrors(details.fieldErrors);
      setGeneralError(details.message ?? (details.messageKey ? t(details.messageKey) : null));
    } finally {
      setWysylanie(false);
    }
  }

  return (
    <Form onSubmit={submit} noValidate>
      {generalError && <Alert variant="danger">{generalError}</Alert>}

      <Field
        id={`content-${post.id}`}
        label={t('posts.content')}
        value={content}
        onChange={setContent}
        error={fieldErrors.content}
        asTextarea
        rows={3}
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

      <p className="text-body-secondary small">{t('posts.musicClearHint')}</p>

      {/*
        Widocznosc da sie zmienic takze po opublikowaniu - ale dziala to
        WYLACZNIE na przyszlosc: kto post juz przeczytal, ten go przeczytal.
        To nie jest wada tego rozwiazania, tylko wlasciwosc kazdej publikacji.
      */}
      <VisibilityPicker
        id={`visibility-${post.id}`}
        value={visibility}
        onChange={setVisibility}
      />

      {post.imageUrls.length > 0 && (
        <p className="text-body-secondary small">{t('posts.imagesNotEditable')}</p>
      )}

      <div className="d-flex gap-2">
        <Button type="submit" size="sm" disabled={wysylanie || Boolean(linkError(musicUrl, musicKind))}>
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
 * Player nagrania.
 *
 * <p><b>Adres skladamy na SERWERZE</b>, nie tutaj. Kazdy serwis ma inny format
 * adresu osadzenia (i inny parametr momentu startu), a gdyby wiedza o tym
 * siedziala w Reakcie, dolozenie kolejnego serwisu wymagaloby zmian
 * w dwoch miejscach.</p>
 *
 * <p>Tutaj zostaje wylacznie to, czego serwer nie moze wiedziec: <b>ile
 * miejsca</b> odtwarzacz potrzebuje na ekranie.</p>
 */
function Player({ post }) {
  const { t } = useTranslation();

  const height = playerHeight(post.musicProvider, post.musicKind);
  const hasVideo = height === null;

  const frame = (
    <iframe
      src={post.musicEmbedUrl}
      title={post.musicTitle ?? `${post.musicProvider} - ${post.authorUsername}`}
      width="100%"
      height={height ?? undefined}
      allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"
      allowFullScreen={hasVideo}
      loading="lazy"
    />
  );

  return (
    <div>
      {/*
        Zaokraglenie musi byc na OTOCZCE z overflow: hidden, a nie na samej
        ramce. Strona serwisu w srodku ma wlasne, prostokatne tlo - przy
        border-radius na iframe wystawalo ono w rogach jako biale narozniki.

        "ratio ratio-16x9" to gotowe klasy Bootstrapa: otoczka dostaje
        wysokosc rowna 56,25% swojej szerokosci, a ramka w srodku wypelnia
        ja w calosci. Dzieki temu film skaluje sie razem z szerokoscia
        tablicy i na telefonie nie trzeba niczego przeliczac.
      */}
      <div className={`player-frame${hasVideo ? ' ratio ratio-16x9' : ''}`}>
        {frame}
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
