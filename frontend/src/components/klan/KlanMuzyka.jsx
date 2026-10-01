import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import Avatar from '../Avatar';
import { IconCheckCircle, IconExternal, IconNote, IconTrash } from '../Icons';
import { formatDate } from '../../utils/dates';
import { linkError } from '../../utils/musicLinks';
import { playerHeight } from '../../utils/player';

/**
 * Muzyka klanu: gust (wykonawcy i gatunki wspolne dla kilku osob - bez wskazywania, kto)
 * oraz "utwor tygodnia": czlonkowie wrzucaja linki, reszta glosuje.
 */
export default function KlanMuzyka({ klan }) {
  return (
    <div className="d-grid gap-3">
      <UtworTygodnia klan={klan} />
      <Gust klan={klan} />
    </div>
  );
}

/* ------------------------------------------------------------------------ */
/*  Gust klanu                                                              */
/* ------------------------------------------------------------------------ */

/** Gust klanu - tez dla osob spoza klanu, gdy klan jest w przegladarce (dane zbiorcze, bez wskazywania osob). */
export function Gust({ klan }) {
  const { t } = useTranslation();
  const [gust, setGust] = useState(null);
  const [blad, setBlad] = useState(null);

  useEffect(() => {
    let anulowane = false;
    klany.gust(klan.id)
      .then((dane) => !anulowane && setGust(dane))
      .catch((p) => !anulowane && setBlad(describeError(p).message));
    return () => { anulowane = true; };
  }, [klan.id]);

  const pusty = gust && gust.artists.length === 0 && gust.genres.length === 0;

  return (
    <Card>
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.music.taste.title')}</Card.Title>
        <p className="small text-body-secondary">{t('clans.music.taste.intro')}</p>
        {blad && <Alert variant="danger">{blad}</Alert>}

        {pusty && <p className="mb-0">{t('clans.music.taste.none')}</p>}

        {gust && gust.artists.length > 0 && (
          <>
            <h3 className="h6 mt-2">{t('clans.music.taste.artists')}</h3>
            <ul className="klan-gust-artysci list-unstyled">
              {gust.artists.map((a) => (
                <li key={a.externalId} className="klan-gust-artysta">
                  <Avatar avatarUrl={a.imageUrl} username={a.name} size={36} />
                  <span className="klan-gust-nazwa">
                    <span className="klan-gust-imie">{a.name}</span>
                    <span className="small text-body-secondary">{t('clans.music.taste.people', { count: a.count })}</span>
                  </span>
                </li>
              ))}
            </ul>
          </>
        )}

        {gust && gust.genres.length > 0 && (
          <>
            <h3 className="h6 mt-3">{t('clans.music.taste.genres')}</h3>
            <div className="klan-gust-gatunki">
              {gust.genres.map((g) => (
                <span key={g.name} className="klan-gatunek">
                  {g.name}
                  <span className="klan-gatunek-liczba">{g.count}</span>
                </span>
              ))}
            </div>
          </>
        )}

        {gust && (
          <p className="small text-body-secondary mt-3 mb-0">
            {t('clans.music.taste.counted', { counted: gust.counted, members: gust.members })}
          </p>
        )}
      </Card.Body>
    </Card>
  );
}

/* ------------------------------------------------------------------------ */
/*  Utwor tygodnia                                                          */
/* ------------------------------------------------------------------------ */

function UtworTygodnia({ klan }) {
  const { t, i18n } = useTranslation();
  const [dane, setDane] = useState(null);
  const [blad, setBlad] = useState(null);
  const [info, setInfo] = useState(null);
  const [adres, setAdres] = useState('');
  const [notatka, setNotatka] = useState('');
  const [zajety, setZajety] = useState(false);
  const mozeDodawac = klan.myRole != null;

  const wczytaj = useCallback(async () => {
    try {
      setDane(await klany.utwory(klan.id));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }, [klan.id]);

  useEffect(() => { wczytaj(); }, [wczytaj]);

  async function wykonaj(akcja, komunikat) {
    setZajety(true);
    setBlad(null);
    setInfo(null);
    try {
      await akcja();
      await wczytaj();
      if (komunikat) {
        setInfo(komunikat);
      }
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  async function dodaj(e) {
    e.preventDefault();
    await wykonaj(() => klany.zaproponujUtwor(klan.id, adres.trim(), notatka.trim()), t('clans.music.weekly.added'));
    setAdres('');
    setNotatka('');
  }

  const kluczBledu = linkError(adres, 'TRACK');
  const zakres = dane
    ? t('clans.music.weekly.range', {
      from: formatDate(dane.weekStart, i18n.language), to: formatDate(dane.weekEnd, i18n.language),
    })
    : '';

  return (
    <Card>
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.music.weekly.title')}</Card.Title>
        <p className="small text-body-secondary mb-1">{t('clans.music.weekly.hint')}</p>
        {dane && <p className="small fw-semibold">{zakres}</p>}

        {blad && <Alert variant="danger">{blad}</Alert>}
        {info && <Alert variant="success" dismissible onClose={() => setInfo(null)}>{info}</Alert>}

        {mozeDodawac && dane && (
          dane.remaining > 0 ? (
            <Form onSubmit={dodaj} noValidate className="klan-utwor-formularz mb-3">
              <Form.Label htmlFor="klan-utwor-adres">{t('clans.music.weekly.url')}</Form.Label>
              <Form.Control id="klan-utwor-adres" type="url" inputMode="url" value={adres} maxLength={500}
                placeholder="https://open.spotify.com/track/…" onChange={(e) => setAdres(e.target.value)}
                isInvalid={Boolean(kluczBledu)} />
              <Form.Text>{t('clans.music.weekly.urlHint')}</Form.Text>
              {kluczBledu && <Form.Control.Feedback type="invalid">{t(kluczBledu)}</Form.Control.Feedback>}
              <Form.Control className="mt-2" value={notatka} maxLength={200} aria-label={t('clans.music.weekly.note')}
                placeholder={t('clans.music.weekly.note')} onChange={(e) => setNotatka(e.target.value)} />
              <div className="d-flex align-items-center flex-wrap gap-2 mt-2">
                <Button type="submit" size="sm" disabled={zajety || !adres.trim() || Boolean(kluczBledu)}>
                  {t('clans.music.weekly.add')}
                </Button>
                <span className="small text-body-secondary">{t('clans.music.weekly.remaining', { count: dane.remaining })}</span>
              </div>
            </Form>
          ) : (
            <p className="small text-body-secondary">{t('clans.music.weekly.noneLeft')}</p>
          )
        )}

        {dane && dane.tracks.length === 0 && <p className="mb-0">{t('clans.music.weekly.empty')}</p>}

        {dane && dane.tracks.length > 0 && (
          <ul className="list-unstyled klan-utwory">
            {dane.tracks.map((u) => (
              <li key={u.id}>
                <Utwor
                  utwor={u}
                  mozeGlosowac={mozeDodawac}
                  zajety={zajety}
                  onGlos={() => wykonaj(() => klany.glosujNaUtwor(klan.id, u.id, !u.iVoted))}
                  onUsun={() => {
                    if (window.confirm(t('clans.music.weekly.deleteConfirm'))) {
                      wykonaj(() => klany.usunUtwor(klan.id, u.id));
                    }
                  }}
                />
              </li>
            ))}
          </ul>
        )}

        {dane && dane.previous.length > 0 && (
          <>
            <h3 className="h6 mt-4">{t('clans.music.weekly.previous')}</h3>
            <ul className="list-unstyled klan-utwory">
              {dane.previous.map((u) => (
                <li key={u.id}>
                  <Utwor
                    utwor={u}
                    naglowek={t('clans.music.weekly.week', { date: formatDate(u.weekStart, i18n.language) })}
                    mozeGlosowac={false}
                    zajety={zajety}
                    onUsun={() => {
                      if (window.confirm(t('clans.music.weekly.deleteConfirm'))) {
                        wykonaj(() => klany.usunUtwor(klan.id, u.id));
                      }
                    }}
                  />
                </li>
              ))}
            </ul>
          </>
        )}
      </Card.Body>
    </Card>
  );
}

/** Jedna propozycja: odtwarzacz wchodzi dopiero po kliknieciu - na liscie moze byc kilkadziesiat utworow. */
function Utwor({ utwor, naglowek, mozeGlosowac, zajety, onGlos, onUsun }) {
  const { t } = useTranslation();
  const [gra, setGra] = useState(false);
  const wysokosc = playerHeight(utwor.provider, 'TRACK');

  return (
    <div className={`klan-utwor${utwor.leader ? ' is-prowadzi' : ''}`}>
      {naglowek && <div className="klan-utwor-tydzien small text-body-secondary">{naglowek}</div>}
      <div className="klan-utwor-gora">
        <span className="klan-utwor-okladka" aria-hidden="true">
          {utwor.thumbnailUrl ? <img src={utwor.thumbnailUrl} alt="" loading="lazy" /> : <IconNote size={22} />}
        </span>
        <span className="klan-utwor-opis">
          {utwor.leader && (
            <span className="klan-utwor-korona"><IconCheckCircle size={12} /> {t('clans.music.weekly.leader')}</span>
          )}
          <span className="klan-utwor-tytul">{utwor.title ?? t(`posts.providers.${utwor.provider}`)}</span>
          <span className="small text-body-secondary klan-utwor-autor">
            <Avatar avatarUrl={utwor.proposerAvatarUrl} username={utwor.proposerUsername} size={16} />
            {t('clans.music.weekly.proposedBy', { username: utwor.proposerUsername })}
          </span>
          {utwor.note && <span className="klan-utwor-notatka">{utwor.note}</span>}
        </span>
      </div>

      {gra && (
        <div className={`player-frame mt-2${wysokosc === null ? ' ratio ratio-16x9' : ''}`}>
          <iframe
            src={utwor.embedUrl}
            title={utwor.title ?? utwor.provider}
            width="100%"
            height={wysokosc ?? undefined}
            allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"
            loading="lazy"
          />
        </div>
      )}

      <div className="klan-utwor-dol">
        {!gra && (
          <Button variant="outline-secondary" size="sm" onClick={() => setGra(true)}>{t('clans.music.weekly.play')}</Button>
        )}
        <a className="btn btn-outline-secondary btn-sm" href={utwor.pageUrl} target="_blank" rel="noopener noreferrer">
          <IconExternal size={12} className="me-1" />{t('clans.music.weekly.open')}
        </a>
        {mozeGlosowac ? (
          <Button
            variant={utwor.iVoted ? 'primary' : 'outline-primary'}
            size="sm"
            aria-pressed={utwor.iVoted}
            disabled={zajety}
            onClick={onGlos}
          >
            {utwor.iVoted ? t('clans.music.weekly.voted') : t('clans.music.weekly.vote')}
          </Button>
        ) : null}
        <span className="klan-utwor-glosy small fw-semibold">{t('clans.music.weekly.votes', { count: utwor.votes })}</span>
        {utwor.canDelete && (
          <button type="button" className="btn btn-link btn-sm text-danger p-0 ms-auto" onClick={onUsun}
            aria-label={t('clans.music.weekly.delete')} title={t('clans.music.weekly.delete')}>
            <IconTrash size={14} />
          </button>
        )}
      </div>
    </div>
  );
}
