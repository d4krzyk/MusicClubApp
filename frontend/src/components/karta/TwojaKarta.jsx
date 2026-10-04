import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import Modal from 'react-bootstrap/Modal';
import { describeError } from '../../api/client';
import * as karta from '../../api/karta';
import * as poznawaj from '../../api/poznawaj';
import { IconArrowLeft, IconArrowRight, IconCamera, IconCross, IconPlus, IconTrash } from '../Icons';
import Zasieg from '../Zasieg';
import KartaPoznawaj from '../poznawaj/KartaPoznawaj';

export const MAKS_ZDJEC = 6;
export const MAKS_BIO = 300;
export const MAKS_ODPOWIEDZ = 150;
export const SZUKAM = ['CONCERT_BUDDIES', 'FESTIVALS', 'JAMMING', 'MUSIC_TALK', 'NEW_MUSIC', 'RECORD_SWAPS',
  'PARTIES', 'PLAYLISTS'];
const MAKS_SZUKAM = 3;
export const PYTANIA = ['FIRST_CONCERT', 'LIFE_CHANGING_ALBUM', 'DESERT_ISLAND', 'DREAM_GIG', 'GUILTY_PLEASURE',
  'ON_REPEAT', 'KARAOKE', 'PARTY_STARTER', 'UNPOPULAR_OPINION', 'INSTRUMENT', 'MORNING_SONG', 'UNDERRATED_ARTIST'];
const MAKS_PYTAN = 3;
const TYPY_ZDJEC = 'image/jpeg,image/png,image/webp';

/**
 * Ustawienia: "Twoja karta" - galeria zdjec (do 6, pierwsze = okladka, kolejnosc strzalkami albo
 * przeciaganiem), "o mnie", "szukam", pytania muzyczne, tryb Poznawaj z zasiegiem i podglad karty tak, jak
 * widza ja inni. Zdjecia zapisuja sie od razu; tekst - przyciskiem "Zapisz karte".
 */
export default function TwojaKarta() {
  const { t } = useTranslation();
  const [dane, setDane] = useState(null);
  const [stan, setStan] = useState(null);
  const [formularz, setFormularz] = useState({ bio: '', lookingFor: [], prompts: [] });
  const [bledyPol, setBledyPol] = useState({});
  const [blad, setBlad] = useState(null);
  const [zapisano, setZapisano] = useState(false);
  const [wysylanie, setWysylanie] = useState(false);
  const [wgrywane, setWgrywane] = useState(0);
  const [podglad, setPodglad] = useState(false);
  const [przeciagane, setPrzeciagane] = useState(null);
  const plik = useRef(null);

  useEffect(() => {
    Promise.all([karta.moja(), poznawaj.stan()])
      .then(([k, s]) => { przyjmij(k); setStan(s); })
      .catch((p) => setBlad(describeError(p).message));
  }, []);

  function przyjmij(k) {
    setDane(k);
    setFormularz({
      bio: k.bio ?? '',
      lookingFor: k.lookingFor,
      prompts: k.prompts.map((p) => ({ prompt: p.prompt, answer: p.answer })),
    });
  }

  function zmien(zmiana) {
    setFormularz((f) => ({ ...f, ...zmiana }));
    setZapisano(false);
  }

  async function wykonaj(akcja) {
    setBlad(null);
    try {
      const k = await akcja();
      setDane((d) => ({ ...d, photos: k.photos }));
      // Zdjecia zmieniaja tez podglad i liste brakow
      setStan(await poznawaj.stan());
    } catch (problem) {
      const s = describeError(problem);
      setBlad(s.fieldErrors.images ?? s.fieldErrors.file ?? s.message);
    }
  }

  async function dodajZdjecia(e) {
    const wolne = MAKS_ZDJEC - dane.photos.length;
    const pliki = Array.from(e.target.files ?? []).slice(0, Math.max(0, wolne));
    e.target.value = '';
    for (const p of pliki) {
      setWgrywane((n) => n + 1);
      // Po kolei - serwer dopisuje kazde na koniec, wiec kolejnosc zostaje taka, jak w wyborze
      // eslint-disable-next-line no-await-in-loop
      await wykonaj(() => karta.dodajZdjecie(p));
      setWgrywane((n) => n - 1);
    }
  }

  function przesun(z, na) {
    if (na < 0 || na >= dane.photos.length || z === na) {
      return;
    }
    const ids = dane.photos.map((p) => p.id);
    const [ruszone] = ids.splice(z, 1);
    ids.splice(na, 0, ruszone);
    // Od razu na ekranie, serwer potwierdza
    setDane((d) => ({ ...d, photos: ids.map((id) => d.photos.find((p) => p.id === id)) }));
    wykonaj(() => karta.ulozZdjecia(ids));
  }

  async function zapisz(e) {
    e.preventDefault();
    setWysylanie(true);
    setBlad(null);
    setBledyPol({});
    try {
      const k = await karta.zapisz({
        bio: formularz.bio,
        lookingFor: formularz.lookingFor,
        prompts: formularz.prompts.filter((p) => p.prompt),
      });
      przyjmij(k);
      setZapisano(true);
      setStan(await poznawaj.stan());
    } catch (problem) {
      const s = describeError(problem);
      setBledyPol(s.fieldErrors);
      setBlad(s.message ?? t('card.fixErrors'));
    } finally {
      setWysylanie(false);
    }
  }

  async function ustawPoznawaj(wlaczony, zasieg) {
    try {
      setStan(await poznawaj.ustawienia(wlaczony, zasieg));
    } catch (problem) {
      setBlad(describeError(problem).message);
    }
  }

  function przelaczSzukam(l) {
    const jest = formularz.lookingFor.includes(l);
    if (!jest && formularz.lookingFor.length >= MAKS_SZUKAM) {
      return;
    }
    zmien({ lookingFor: jest ? formularz.lookingFor.filter((x) => x !== l) : [...formularz.lookingFor, l] });
  }

  function zmienPytanie(i, zmiana) {
    zmien({ prompts: formularz.prompts.map((p, j) => (j === i ? { ...p, ...zmiana } : p)) });
  }

  if (!dane) {
    return (
      <Card className="mb-4" id="karta">
        <Card.Body>
          {blad ? <Alert variant="danger" className="mb-0">{blad}</Alert>
            : <div className="karta-szkielet" aria-busy="true" aria-label={t('common.loading')} />}
        </Card.Body>
      </Card>
    );
  }

  const zajete = new Set(formularz.prompts.map((p) => p.prompt));

  return (
    <Card className="mb-4" id="karta">
      <Card.Body>
        <div className="d-flex align-items-start justify-content-between gap-2 flex-wrap">
          <div>
            <Card.Title as="h2" className="h6 text-uppercase text-body-secondary mb-1">{t('card.title')}</Card.Title>
            <p className="small text-body-secondary mb-3">{t('card.subtitle')}</p>
          </div>
          <Button size="sm" variant="outline-primary" onClick={() => setPodglad(true)} disabled={!stan}>
            {t('card.preview')}
          </Button>
        </div>

        {blad && <Alert variant="danger" dismissible onClose={() => setBlad(null)} className="py-2">{blad}</Alert>}

        {/* --- Zdjecia --- */}
        <h3 className="karta-podtytul">{t('card.photos')}</h3>
        <ul className="karta-zdjecia list-unstyled">
          {dane.photos.map((p, i) => (
            <li
              key={p.id}
              className={`karta-zdjecie${przeciagane === i ? ' is-przeciagane' : ''}`}
              draggable
              onDragStart={() => setPrzeciagane(i)}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => { e.preventDefault(); if (przeciagane !== null) przesun(przeciagane, i); setPrzeciagane(null); }}
              onDragEnd={() => setPrzeciagane(null)}
            >
              <img src={p.url} alt={t('card.photoN', { n: i + 1 })} draggable={false} />
              {i === 0 && <span className="karta-okladka">{t('card.cover')}</span>}
              <div className="karta-zdjecie-akcje">
                <button type="button" onClick={() => przesun(i, i - 1)} disabled={i === 0}
                  aria-label={t('card.moveEarlier', { n: i + 1 })} title={t('card.moveEarlier', { n: i + 1 })}>
                  <IconArrowLeft size={12} />
                </button>
                <button type="button" onClick={() => przesun(i, i + 1)} disabled={i === dane.photos.length - 1}
                  aria-label={t('card.moveLater', { n: i + 1 })} title={t('card.moveLater', { n: i + 1 })}>
                  <IconArrowRight size={12} />
                </button>
                <button type="button" className="is-usun" onClick={() => wykonaj(() => karta.usunZdjecie(p.id))}
                  aria-label={t('card.removePhoto', { n: i + 1 })} title={t('card.removePhoto', { n: i + 1 })}>
                  <IconTrash size={12} />
                </button>
              </div>
            </li>
          ))}
          {Array.from({ length: Math.min(wgrywane, MAKS_ZDJEC - dane.photos.length) }).map((_, i) => (
            // eslint-disable-next-line react/no-array-index-key
            <li key={`w${i}`} className="karta-zdjecie is-wgrywane" aria-busy="true">
              <span className="spinner-border spinner-border-sm" role="status" aria-label={t('card.uploading')} />
            </li>
          ))}
          {dane.photos.length + wgrywane < MAKS_ZDJEC && (
            <li className="karta-zdjecie is-puste">
              <button type="button" onClick={() => plik.current?.click()}>
                <IconCamera size={22} />
                <span>{t('card.addPhoto')}</span>
              </button>
            </li>
          )}
        </ul>
        <input ref={plik} type="file" accept={TYPY_ZDJEC} multiple hidden onChange={dodajZdjecia}
          aria-label={t('card.addPhoto')} />
        <p className="small text-body-secondary">{t('card.photosHint', { max: MAKS_ZDJEC })}</p>

        <Form onSubmit={zapisz} noValidate>
          {/* --- O mnie --- */}
          <Form.Group controlId="karta-bio" className="mb-3">
            <Form.Label className="karta-podtytul">{t('card.bio')}</Form.Label>
            <Form.Control
              as="textarea"
              rows={3}
              maxLength={MAKS_BIO}
              value={formularz.bio}
              onChange={(e) => zmien({ bio: e.target.value })}
              placeholder={t('card.bioPlaceholder')}
              isInvalid={Boolean(bledyPol.bio)}
            />
            <div className="d-flex justify-content-between">
              <Form.Control.Feedback type="invalid">{bledyPol.bio}</Form.Control.Feedback>
              <Form.Text className="ms-auto">{formularz.bio.length}/{MAKS_BIO}</Form.Text>
            </div>
          </Form.Group>

          {/* --- Szukam --- */}
          <fieldset className="mb-3">
            <legend className="karta-podtytul">{t('card.lookingFor')}</legend>
            <div className="karta-szukam">
              {SZUKAM.map((l) => {
                const wybrane = formularz.lookingFor.includes(l);
                return (
                  <button
                    key={l}
                    type="button"
                    className={`pz-szukam-chip is-przycisk${wybrane ? ' is-wybrany' : ''}`}
                    aria-pressed={wybrane}
                    disabled={!wybrane && formularz.lookingFor.length >= MAKS_SZUKAM}
                    onClick={() => przelaczSzukam(l)}
                  >
                    {t(`card.looking.${l}`)}
                  </button>
                );
              })}
            </div>
            <Form.Text>{t('card.lookingForHint', { max: MAKS_SZUKAM })}</Form.Text>
          </fieldset>

          {/* --- Pytania --- */}
          <fieldset className="mb-3">
            <legend className="karta-podtytul">{t('card.prompts')}</legend>
            <p className="small text-body-secondary mb-2">{t('card.promptsHint', { max: MAKS_PYTAN })}</p>
            {formularz.prompts.map((p, i) => (
              // eslint-disable-next-line react/no-array-index-key
              <div key={i} className="karta-pytanie">
                <div className="d-flex gap-2 align-items-center mb-2">
                  <Form.Select
                    size="sm"
                    value={p.prompt ?? ''}
                    onChange={(e) => zmienPytanie(i, { prompt: e.target.value || null })}
                    aria-label={t('card.choosePrompt')}
                  >
                    <option value="">{t('card.choosePrompt')}</option>
                    {PYTANIA.map((q) => (
                      <option key={q} value={q} disabled={zajete.has(q) && q !== p.prompt}>{t(`card.prompt.${q}`)}</option>
                    ))}
                  </Form.Select>
                  <button type="button" className="karta-pytanie-usun"
                    onClick={() => zmien({ prompts: formularz.prompts.filter((_, j) => j !== i) })}
                    aria-label={t('card.removePrompt')} title={t('card.removePrompt')}>
                    <IconCross size={12} />
                  </button>
                </div>
                <Form.Control
                  as="textarea"
                  rows={2}
                  maxLength={MAKS_ODPOWIEDZ}
                  value={p.answer}
                  onChange={(e) => zmienPytanie(i, { answer: e.target.value })}
                  placeholder={t('card.answerPlaceholder')}
                  aria-label={p.prompt ? t(`card.prompt.${p.prompt}`) : t('card.answerPlaceholder')}
                  isInvalid={Boolean(bledyPol[`prompts[${i}].answer`])}
                />
                <div className="d-flex justify-content-between">
                  <Form.Control.Feedback type="invalid">{bledyPol[`prompts[${i}].answer`]}</Form.Control.Feedback>
                  <Form.Text className="ms-auto">{p.answer.length}/{MAKS_ODPOWIEDZ}</Form.Text>
                </div>
              </div>
            ))}
            {formularz.prompts.length < MAKS_PYTAN && (
              <Button size="sm" variant="outline-secondary"
                onClick={() => zmien({ prompts: [...formularz.prompts, { prompt: null, answer: '' }] })}>
                <IconPlus /> {t('card.addPrompt')}
              </Button>
            )}
          </fieldset>

          {zapisano && <Alert variant="success" className="py-2">{t('card.saved')}</Alert>}
          <Button type="submit" disabled={wysylanie}>{wysylanie ? t('settings.saving') : t('card.save')}</Button>
        </Form>

        {/* --- Poznawaj --- */}
        {stan && (
          <div className="border-top mt-4 pt-3">
            <h3 className="karta-podtytul">{t('discover.settings.title')}</h3>
            <Form.Check
              type="switch"
              id="poznawaj-wlaczony"
              checked={stan.enabled}
              onChange={(e) => ustawPoznawaj(e.target.checked, stan.radiusKm)}
              label={t('discover.settings.toggle')}
            />
            <p className="small text-body-secondary mt-1">{t('discover.settings.toggleHint')}</p>
            {stan.city ? (
              <div className="d-flex align-items-center gap-2 flex-wrap">
                <span className="small fw-semibold">{t('discover.range')}</span>
                <div style={{ maxWidth: '16rem' }}>
                  <Zasieg wartosc={stan.radiusKm} onChange={(km) => ustawPoznawaj(stan.enabled, km)}
                    miasto={stan.city} className="form-select-sm" />
                </div>
              </div>
            ) : (
              <p className="small text-body-secondary mb-0">{t('discover.start.noCity')}</p>
            )}
          </div>
        )}
      </Card.Body>

      <Modal show={podglad} onHide={() => setPodglad(false)} centered dialogClassName="pz-podglad-okno">
        <Modal.Header closeButton>
          <Modal.Title as="h2" className="h6 mb-0">{t('card.previewTitle')}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {stan && (
            <div className="pz-podglad-ramka">
              <KartaPoznawaj karta={{
                ...stan.preview,
                bio: formularz.bio.trim() || null,
                lookingFor: formularz.lookingFor,
                prompts: formularz.prompts.filter((p) => p.prompt && p.answer.trim()),
                photos: dane.photos.map((p) => p.url),
              }} podglad />
            </div>
          )}
        </Modal.Body>
      </Modal>
    </Card>
  );
}
