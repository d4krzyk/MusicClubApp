import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import Tytul from './Tytul';

const TRYBY = ['MANUAL', 'SELF', 'AUTO'];
const METRYKI = ['MESSAGES', 'POSTS', 'TRACKS', 'VOTES', 'REACTIONS', 'DAYS'];
const MAKS_NAZWA = 24;

/** Gotowe tytuly do jednego klikniecia - wypelniaja formularz, zarzad moze je jeszcze zmienic. */
const SZABLONY = [
  { klucz: 'chatty', tryb: 'AUTO', metryka: 'MESSAGES', prog: 100, kolor: 'ORANGE' },
  { klucz: 'regular', tryb: 'AUTO', metryka: 'DAYS', prog: 30, kolor: 'TEAL' },
  { klucz: 'explorer', tryb: 'AUTO', metryka: 'TRACKS', prog: 5, kolor: 'PINK' },
  { klucz: 'voter', tryb: 'AUTO', metryka: 'VOTES', prog: 20, kolor: 'INDIGO' },
  { klucz: 'concertGoer', tryb: 'SELF', metryka: 'MESSAGES', prog: 50, kolor: 'RED' },
  { klucz: 'dj', tryb: 'MANUAL', metryka: 'MESSAGES', prog: 50, kolor: 'VIOLET' },
];

const PUSTY = { nazwa: '', kolor: 'VIOLET', tryb: 'MANUAL', metryka: 'MESSAGES', prog: 50 };

/**
 * Tytuly (role) w klanie. Definiuje je zarzad; osoba dostaje tytul na jeden z trzech sposobow:
 * nada go zarzad (MANUAL), wezmie go sama (SELF) albo przyjdzie sam z aktywnosci (AUTO).
 * Tytul to ozdoba i informacja - uprawnien nie zmienia.
 */
export default function KlanTytuly({ klan, onZmiana }) {
  const { t } = useTranslation();
  const zarzad = klan.myRole === 'FOUNDER' || klan.myRole === 'ADMIN';
  const [formularz, setFormularz] = useState(null);   // null = zamkniety; { id?, ...pola }
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);

  async function wykonaj(akcja) {
    setZajety(true);
    setBlad(null);
    try {
      onZmiana(await akcja());
      return true;
    } catch (problem) {
      setBlad(describeError(problem).message);
      return false;
    } finally {
      setZajety(false);
    }
  }

  async function zapisz(e) {
    e.preventDefault();
    const dane = {
      name: formularz.nazwa.trim(),
      color: formularz.kolor,
      mode: formularz.tryb,
      metric: formularz.tryb === 'AUTO' ? formularz.metryka : null,
      threshold: formularz.tryb === 'AUTO' ? Number(formularz.prog) : null,
    };
    const udalo = await wykonaj(() => (formularz.id
      ? klany.zmienTytul(klan.id, formularz.id, dane)
      : klany.dodajTytul(klan.id, dane)));
    if (udalo) {
      setFormularz(null);
    }
  }

  function edytuj(tytul) {
    setFormularz({
      id: tytul.id,
      nazwa: tytul.name,
      kolor: tytul.color,
      tryb: tytul.mode,
      metryka: tytul.metric ?? 'MESSAGES',
      prog: tytul.threshold ?? 50,
    });
  }

  function opisZasady(tytul) {
    if (tytul.mode === 'AUTO') {
      return t(`clans.titles.rule.${tytul.metric}`, { count: tytul.threshold });
    }
    return t(`clans.titles.mode.${tytul.mode}`);
  }

  return (
    <div className="d-grid gap-3">
      {blad && <Alert variant="danger" className="mb-0">{blad}</Alert>}

      <Card>
        <Card.Body>
          <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.titles.title')}</Card.Title>
          <p className="small text-body-secondary">{t('clans.titles.intro')}</p>

          {klan.titles.length === 0 && (
            <p className="mb-0">{zarzad ? t('clans.titles.noneManager') : t('clans.titles.none')}</p>
          )}

          <ul className="list-unstyled mb-0">
            {klan.titles.map((tytul) => (
              <li key={tytul.id} className="klan-tytul-wiersz">
                <div className="klan-tytul-opis">
                  <Tytul tytul={{ name: tytul.name, colorHex: tytul.colorHex }} />
                  <span className="small text-body-secondary">
                    {opisZasady(tytul)}
                    {' · '}{t('clans.titles.holders', { count: tytul.holders })}
                    {tytul.mine && <strong className="klan-tytul-moj">{' · '}{t('clans.titles.mine')}</strong>}
                  </span>
                </div>
                <span className="klan-czlonek-akcje">
                  {tytul.mode === 'SELF' && tytul.mine && (
                    <Button size="sm" variant="outline-secondary" disabled={zajety}
                      onClick={() => wykonaj(() => klany.oddajTytul(klan.id, tytul.id))}>
                      {t('clans.titles.giveBack')}
                    </Button>
                  )}
                  {tytul.canClaim && (
                    <Button size="sm" disabled={zajety} onClick={() => wykonaj(() => klany.wezTytul(klan.id, tytul.id))}>
                      {t('clans.titles.claim')}
                    </Button>
                  )}
                  {zarzad && (
                    <>
                      <Button size="sm" variant="outline-secondary" disabled={zajety} onClick={() => edytuj(tytul)}>
                        {t('clans.titles.edit')}
                      </Button>
                      <Button size="sm" variant="outline-danger" disabled={zajety}
                        onClick={() => {
                          if (window.confirm(t('clans.titles.deleteConfirm', { name: tytul.name }))) {
                            wykonaj(() => klany.usunTytul(klan.id, tytul.id));
                          }
                        }}>
                        {t('clans.titles.delete')}
                      </Button>
                    </>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </Card.Body>
      </Card>

      {zarzad && !formularz && (
        <Card>
          <Card.Body>
            <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.titles.newTitle')}</Card.Title>
            <p className="small text-body-secondary">{t('clans.titles.presetsHint')}</p>
            <div className="d-flex flex-wrap gap-2 mb-3">
              {SZABLONY.map((s) => (
                <Button key={s.klucz} size="sm" variant="outline-secondary"
                  onClick={() => setFormularz({
                    nazwa: t(`clans.titles.presets.${s.klucz}`), kolor: s.kolor, tryb: s.tryb, metryka: s.metryka, prog: s.prog,
                  })}>
                  {t(`clans.titles.presets.${s.klucz}`)}
                </Button>
              ))}
            </div>
            <Button onClick={() => setFormularz({ ...PUSTY })}>{t('clans.titles.create')}</Button>
          </Card.Body>
        </Card>
      )}

      {zarzad && formularz && (
        <Card>
          <Card.Body>
            <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">
              {formularz.id ? t('clans.titles.editTitle') : t('clans.titles.newTitle')}
            </Card.Title>
            <Form onSubmit={zapisz} noValidate>
              <Form.Label htmlFor="tytul-nazwa">{t('clans.titles.name')}</Form.Label>
              <Form.Control id="tytul-nazwa" value={formularz.nazwa} maxLength={MAKS_NAZWA} className="mb-3"
                onChange={(e) => setFormularz({ ...formularz, nazwa: e.target.value })} />

              <span className="form-label d-block" id="tytul-kolor-etykieta">{t('clans.titles.color')}</span>
              <div className="klan-paleta mb-3" role="group" aria-labelledby="tytul-kolor-etykieta">
                {klan.palette.map((k) => (
                  <button key={k.key} type="button"
                    className={`klan-kolor${formularz.kolor === k.key ? ' is-moj' : ''}`}
                    style={{ '--probka': k.hex }} aria-pressed={formularz.kolor === k.key}
                    title={t(`clans.color.names.${k.key}`)}
                    onClick={() => setFormularz({ ...formularz, kolor: k.key })}>
                    <span className="klan-kolor-probka" />
                    <span>{t(`clans.color.names.${k.key}`)}</span>
                  </button>
                ))}
              </div>

              <fieldset className="mb-3">
                <legend className="form-label fs-6 mb-1">{t('clans.titles.how')}</legend>
                {TRYBY.map((tryb) => (
                  <Form.Check key={tryb} type="radio" id={`tytul-tryb-${tryb}`} name="tytul-tryb"
                    label={t(`clans.titles.howOption.${tryb}`)} checked={formularz.tryb === tryb}
                    onChange={() => setFormularz({ ...formularz, tryb })} />
                ))}
              </fieldset>

              {formularz.tryb === 'AUTO' && (
                <div className="row g-2 mb-3">
                  <div className="col-12 col-sm-7">
                    <Form.Label htmlFor="tytul-metryka">{t('clans.titles.metric')}</Form.Label>
                    <Form.Select id="tytul-metryka" value={formularz.metryka}
                      onChange={(e) => setFormularz({ ...formularz, metryka: e.target.value })}>
                      {METRYKI.map((m) => <option key={m} value={m}>{t(`clans.titles.metricName.${m}`)}</option>)}
                    </Form.Select>
                  </div>
                  <div className="col-12 col-sm-5">
                    <Form.Label htmlFor="tytul-prog">{t('clans.titles.threshold')}</Form.Label>
                    <Form.Control id="tytul-prog" type="number" min={1} max={100000} inputMode="numeric"
                      value={formularz.prog} onChange={(e) => setFormularz({ ...formularz, prog: e.target.value })} />
                  </div>
                  <Form.Text className="d-block">{t('clans.titles.autoHint')}</Form.Text>
                </div>
              )}

              <div className="d-flex align-items-center gap-3 flex-wrap">
                <Tytul tytul={{ name: formularz.nazwa.trim() || t('clans.titles.preview'),
                  colorHex: klan.palette.find((k) => k.key === formularz.kolor)?.hex }} />
                <Button type="submit" disabled={zajety || formularz.nazwa.trim().length < 2}>{t('common.save')}</Button>
                <Button variant="outline-secondary" disabled={zajety} onClick={() => setFormularz(null)}>
                  {t('common.cancel')}
                </Button>
              </div>
            </Form>
          </Card.Body>
        </Card>
      )}
    </div>
  );
}
