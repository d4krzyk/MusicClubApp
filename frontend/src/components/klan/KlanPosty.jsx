import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Collapse from 'react-bootstrap/Collapse';
import { describeError } from '../../api/client';
import * as posty from '../../api/posty';
import EmptyState from '../EmptyState';
import { IconClan, IconCross, IconPlus } from '../Icons';
import Post from '../Post';
import PostForm from '../PostForm';
import PostSkeleton from '../PostSkeleton';
import useLiveReactions from '../../hooks/useLiveReactions';

const NA_STRONE = 10;

/**
 * Posty klanu - widoczne tylko dla jego czlonkow (i administratora aplikacji). To te same posty
 * co na tablicy (zdjecia, muzyka, reakcje), ale nie trafiaja ani na tablice, ani na profil.
 */
export default function KlanPosty({ klan }) {
  const { t } = useTranslation();
  const [lista, setLista] = useState([]);
  const [strona, setStrona] = useState(0);
  const [ostatnia, setOstatnia] = useState(true);
  const [ladowanie, setLadowanie] = useState(true);
  const [pierwsze, setPierwsze] = useState(true);
  const [blad, setBlad] = useState(null);
  const [komunikat, setKomunikat] = useState(null);
  const [formularz, setFormularz] = useState(false);
  const mozePisac = klan.myRole != null;

  const pobierz = useCallback(async (numer, dolacz) => {
    setLadowanie(true);
    setBlad(null);
    try {
      const dane = await posty.tablica({ strona: numer, rozmiar: NA_STRONE, klan: klan.id });
      setLista((poprzednie) => (dolacz ? [...poprzednie, ...dane.content] : dane.content));
      setOstatnia(dane.last);
      setStrona(dane.number);
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setLadowanie(false);
      setPierwsze(false);
    }
  }, [klan.id]);

  useEffect(() => {
    setLista([]);
    setPierwsze(true);
    pobierz(0, false);
  }, [pobierz]);

  const liczniki = useCallback((stan) => {
    setLista((poprzednie) => poprzednie.map((p) => (stan[p.id] ? { ...p, reactions: stan[p.id] } : p)));
  }, []);
  useLiveReactions(lista, liczniki);

  const podmien = (nowy) => setLista((poprzednie) => poprzednie.map((p) => (p.id === nowy.id ? nowy : p)));

  async function usun(id) {
    if (!window.confirm(t('common.confirmDelete'))) {
      return;
    }
    try {
      await posty.usun(id);
      setLista((poprzednie) => poprzednie.filter((p) => p.id !== id));
      setKomunikat(t('posts.deleted'));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }

  return (
    <section aria-label={t('clans.posts.title')}>
      {mozePisac && (
        <>
          <div className="d-flex justify-content-end mb-3">
            <Button
              variant={formularz ? 'outline-secondary' : 'primary'}
              onClick={() => setFormularz((f) => !f)}
              aria-expanded={formularz}
              aria-controls="formularz-posta-klanu"
            >
              {formularz ? <><IconCross /> {t('common.cancel')}</> : <><IconPlus /> {t('posts.newPost')}</>}
            </Button>
          </div>
          <Collapse in={formularz}>
            <div id="formularz-posta-klanu">
              <PostForm
                clanId={klan.id}
                idPola="tresc-posta-klanu"
                podpowiedz={t('clans.posts.placeholder')}
                bezWidocznosci
                onAdded={(nowy) => {
                  setLista((poprzednie) => [nowy, ...poprzednie]);
                  setKomunikat(t('posts.published'));
                  setFormularz(false);
                }}
              />
            </div>
          </Collapse>
        </>
      )}

      {komunikat && <Alert variant="success" dismissible onClose={() => setKomunikat(null)}>{komunikat}</Alert>}
      {blad && <Alert variant="danger">{blad}</Alert>}
      {pierwsze && ladowanie && <PostSkeleton count={2} />}

      {lista.map((p, i) => (
        <Post
          key={p.id}
          post={p}
          index={i % NA_STRONE}
          onDelete={usun}
          onUpdate={(nowy) => { podmien(nowy); setKomunikat(t('posts.updated')); }}
          onReaction={podmien}
          bezKlanu
        />
      ))}

      {!ladowanie && !blad && lista.length === 0 && (
        <EmptyState icon={IconClan} title={t('clans.posts.emptyTitle')} text={t('clans.posts.emptyText')} />
      )}

      {!ladowanie && !ostatnia && (
        <div className="text-center">
          <Button variant="outline-secondary" onClick={() => pobierz(strona + 1, true)}>{t('posts.loadMore')}</Button>
        </div>
      )}
    </section>
  );
}
