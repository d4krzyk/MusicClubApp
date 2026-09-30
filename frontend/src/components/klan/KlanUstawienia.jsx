import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import { IconClan } from '../Icons';

/**
 * Ustawienia klanu: ikona i zdjecie oraz opis (zarzad), nazwa i skrot (tylko zalozyciel),
 * rozwiazanie klanu (tylko zalozyciel; administrator aplikacji tez moze - na stronie klanu).
 */
export default function KlanUstawienia({ klan, onZmiana, onRozwiazany }) {
  const { t } = useTranslation();
  const zalozyciel = klan.myRole === 'FOUNDER';
  const [opis, setOpis] = useState(klan.description ?? '');
  const [nazwa, setNazwa] = useState(klan.name);
  const [skrot, setSkrot] = useState(klan.tag);
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);
  const [info, setInfo] = useState(null);
  const ikona = useRef(null);
  const zdjecie = useRef(null);

  async function wykonaj(akcja, komunikat) {
    setZajety(true);
    setBlad(null);
    setInfo(null);
    try {
      const wynik = await akcja();
      if (wynik) {
        onZmiana(wynik);
      }
      setInfo(komunikat);
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZajety(false);
    }
  }

  function zapisz(e) {
    e.preventDefault();
    const dane = { description: opis };
    if (zalozyciel) {
      dane.name = nazwa;
      dane.tag = skrot;
    }
    return wykonaj(() => klany.zmien(klan.id, dane), t('clans.settings.saved'));
  }

  function wgraj(rodzaj, pole) {
    const plik = pole.current?.files?.[0];
    if (plik) {
      wykonaj(() => klany.wgrajObraz(klan.id, rodzaj, plik), t('clans.settings.imageSaved'));
      pole.current.value = '';
    }
  }

  async function rozwiaz() {
    if (!window.confirm(t('clans.settings.disbandConfirm', { name: klan.name }))) {
      return;
    }
    setZajety(true);
    try {
      await klany.rozwiaz(klan.id);
      onRozwiazany();
    } catch (problem) {
      setBlad(describeError(problem).message);
      setZajety(false);
    }
  }

  return (
    <div className="d-grid gap-3">
      {blad && <Alert variant="danger" className="mb-0">{blad}</Alert>}
      {info && <Alert variant="success" className="mb-0" dismissible onClose={() => setInfo(null)}>{info}</Alert>}

      <Card>
        <Card.Body>
          <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.settings.images')}</Card.Title>
          <div className="d-flex flex-wrap gap-4">
            <div>
              <div className="klan-ikona" style={{ marginTop: 0 }}>
                {klan.iconUrl ? <img src={klan.iconUrl} alt="" /> : <IconClan size={36} />}
              </div>
              <Form.Label htmlFor="klan-ikona" className="small mb-1">{t('clans.settings.icon')}</Form.Label>
              <Form.Control id="klan-ikona" type="file" accept="image/*" ref={ikona} size="sm" disabled={zajety}
                onChange={() => wgraj('icon', ikona)} />
              {klan.iconUrl && (
                <Button variant="link" size="sm" className="px-0 text-danger" disabled={zajety}
                  onClick={() => wykonaj(() => klany.usunObraz(klan.id, 'icon'), t('clans.settings.imageRemoved'))}>
                  {t('clans.settings.remove')}
                </Button>
              )}
            </div>
            <div className="flex-grow-1" style={{ minWidth: 200 }}>
              <div className="klan-zdjecie rounded mb-2" style={{ height: 84 }}>
                {klan.photoUrl && <img src={klan.photoUrl} alt="" />}
              </div>
              <Form.Label htmlFor="klan-zdjecie" className="small mb-1">{t('clans.settings.photo')}</Form.Label>
              <Form.Control id="klan-zdjecie" type="file" accept="image/*" ref={zdjecie} size="sm" disabled={zajety}
                onChange={() => wgraj('photo', zdjecie)} />
              {klan.photoUrl && (
                <Button variant="link" size="sm" className="px-0 text-danger" disabled={zajety}
                  onClick={() => wykonaj(() => klany.usunObraz(klan.id, 'photo'), t('clans.settings.imageRemoved'))}>
                  {t('clans.settings.remove')}
                </Button>
              )}
            </div>
          </div>
          <p className="small text-body-secondary mb-0 mt-2">{t('clans.settings.imagesHint')}</p>
        </Card.Body>
      </Card>

      <Card>
        <Card.Body>
          <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.settings.details')}</Card.Title>
          <Form onSubmit={zapisz} noValidate>
            {zalozyciel && (
              <div className="row g-2 mb-3">
                <div className="col-12 col-sm-8">
                  <Form.Label htmlFor="klan-nazwa">{t('clans.create.name')}</Form.Label>
                  <Form.Control id="klan-nazwa" value={nazwa} maxLength={32} onChange={(e) => setNazwa(e.target.value)} />
                </div>
                <div className="col-12 col-sm-4">
                  <Form.Label htmlFor="klan-skrot">{t('clans.create.tag')}</Form.Label>
                  <Form.Control id="klan-skrot" value={skrot} maxLength={5}
                    onChange={(e) => setSkrot(e.target.value.toUpperCase())} />
                </div>
              </div>
            )}
            <Form.Label htmlFor="klan-opis">{t('clans.create.description')}</Form.Label>
            <Form.Control id="klan-opis" as="textarea" rows={3} maxLength={300} value={opis}
              onChange={(e) => setOpis(e.target.value)} className="mb-3" />
            <Button type="submit" disabled={zajety}>{t('common.save')}</Button>
          </Form>
        </Card.Body>
      </Card>

      {zalozyciel && (
        <Card border="danger">
          <Card.Body>
            <Card.Title as="h2" className="h6 text-uppercase text-danger">{t('clans.settings.danger')}</Card.Title>
            <p className="small text-body-secondary">{t('clans.settings.disbandHint')}</p>
            <Button variant="outline-danger" disabled={zajety} onClick={rozwiaz}>{t('clans.settings.disband')}</Button>
          </Card.Body>
        </Card>
      )}
    </div>
  );
}
