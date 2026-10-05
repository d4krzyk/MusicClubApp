import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import * as wydarzenia from '../../api/wydarzenia';
import {
  domyslneGodziny, doPol, koniecPoPolnocy, MAKS_MIEJSCE, MAKS_NOTATKA, PRZYPOMNIENIA, sprawdzSpotkanie, zPol,
} from '../../utils/spotkania';
import { IconCross, IconPin } from '../Icons';
import MapaPunktu from '../mapa/MapaPunktu';
import { czasPrzypomnienia } from './KartaSpotkania';

/**
 * Panel nad polem pisania: wyslij spotkanie. Miejsce to zawsze tekst ("pod wejsciem do Progresji"); punkt na mapie
 * jest dodatkiem - z mojej pozycji (jednorazowo, na moje klikniecie), stuknieciem w mape albo z miejsca koncertu,
 * na ktory ide. Godziny w czasie przegladarki, do serwera ida jako chwila w UTC.
 */
export default function SpotkanieForm({ idPrefix = 'spotkanie', onWyslij, onZamknij }) {
  const { t, i18n } = useTranslation();
  const start = domyslneGodziny();
  const [miejsce, setMiejsce] = useState('');
  const [punkt, setPunkt] = useState(null);
  const [mapaOtwarta, setMapaOtwarta] = useState(false);
  const [data, setData] = useState(doPol(start.od).data);
  const [od, setOd] = useState(doPol(start.od).godzina);
  const [doG, setDoG] = useState(doPol(start.do).godzina);
  const [przypomnienie, setPrzypomnienie] = useState(30);
  const [notatka, setNotatka] = useState('');
  const [bledy, setBledy] = useState({});
  const [blad, setBlad] = useState(null);
  const [pozycja, setPozycja] = useState(null);
  const [wysylanie, setWysylanie] = useState(false);
  const [koncerty, setKoncerty] = useState([]);

  // Moje nadchodzace koncerty - jednym stuknieciem miejsce i godzina spotkania "przed koncertem"
  useEffect(() => {
    let anulowane = false;
    wydarzenia.lista({ widok: 'moje', rozmiar: 6 })
      .then((d) => !anulowane && setKoncerty((d.content ?? []).filter((w) => !w.withdrawn)))
      .catch(() => {});
    return () => { anulowane = true; };
  }, []);

  async function podKoncertem(w) {
    setBlad(null);
    try {
      const pelne = await wydarzenia.jedno(w.id);
      setMiejsce([pelne.venueName, pelne.city].filter(Boolean).join(', ').slice(0, MAKS_MIEJSCE));
      if (pelne.latitude != null && pelne.longitude != null) {
        setPunkt({ lat: pelne.latitude, lon: pelne.longitude });
      }
      setData(pelne.date);
      // Godzina przed koncertem: spotkanie konczy sie, gdy koncert sie zaczyna
      const poczatek = pelne.time ? pelne.time.slice(0, 5) : '19:00';
      const [g, m] = poczatek.split(':').map(Number);
      const wczesniej = new Date(2000, 0, 1, g, m - 60);
      setOd(doPol(wczesniej).godzina);
      setDoG(poczatek);
    } catch {
      setBlad(t('meetings.errors.concertFailed'));
    }
  }

  function mojaPozycja() {
    if (!navigator.geolocation) {
      setPozycja('meetings.geo.unsupported');
      return;
    }
    setPozycja('meetings.geo.locating');
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setPunkt({ lat: Math.round(p.coords.latitude * 1e6) / 1e6, lon: Math.round(p.coords.longitude * 1e6) / 1e6 });
        setMapaOtwarta(true);
        setPozycja(null);
      },
      (e) => setPozycja(e.code === 1 ? 'meetings.geo.denied' : 'meetings.geo.unavailable'),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    );
  }

  async function wyslij(e) {
    e.preventDefault();
    const odD = zPol(data, od);
    const doD = koniecPoPolnocy(odD, zPol(data, doG));
    const b = sprawdzSpotkanie({ miejsce, od: odD, doK: doD, notatka });
    setBledy(b);
    if (Object.keys(b).length > 0) {
      return;
    }
    setWysylanie(true);
    setBlad(null);
    try {
      await onWyslij({
        place: miejsce.trim(),
        note: notatka.trim() || null,
        latitude: punkt?.lat ?? null,
        longitude: punkt?.lon ?? null,
        startsAt: odD.toISOString(),
        endsAt: doD.toISOString(),
        remindMinutes: przypomnienie,
      });
    } catch (problem) {
      setBlad(problem?.message || t('meetings.errors.sendFailed'));
    } finally {
      setWysylanie(false);
    }
  }

  const krotkaData = new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'short' });

  return (
    <form className="spotkanie-form" onSubmit={wyslij} noValidate aria-label={t('meetings.formTitle')}>
      <div className="spotkanie-form-glowa">
        <strong><IconPin size={14} /> {t('meetings.formTitle')}</strong>
        <button type="button" className="klan-akcja" onClick={onZamknij} aria-label={t('common.close')} title={t('common.close')}>
          <IconCross size={12} />
        </button>
      </div>

      {koncerty.length > 0 && (
        <div className="spotkanie-koncerty">
          <span className="small text-body-secondary">{t('meetings.beforeConcert')}</span>
          {koncerty.map((w) => (
            <button key={w.id} type="button" className="spotkanie-koncert" onClick={() => podKoncertem(w)}>
              {w.venueName ?? w.name} · {krotkaData.format(new Date(w.date))}
            </button>
          ))}
        </div>
      )}

      <Form.Group controlId={`${idPrefix}-miejsce`} className="mb-2">
        <Form.Label className="small fw-semibold mb-1">{t('meetings.place')}</Form.Label>
        <Form.Control
          value={miejsce}
          maxLength={MAKS_MIEJSCE}
          onChange={(e) => setMiejsce(e.target.value)}
          placeholder={t('meetings.placePlaceholder')}
          isInvalid={Boolean(bledy.miejsce)}
        />
        <Form.Control.Feedback type="invalid">{bledy.miejsce && t(bledy.miejsce)}</Form.Control.Feedback>
      </Form.Group>

      <div className="spotkanie-punkt">
        <Button size="sm" variant="outline-secondary" onClick={mojaPozycja}>{t('meetings.useMyLocation')}</Button>
        <Button size="sm" variant="outline-secondary" aria-pressed={mapaOtwarta} onClick={() => setMapaOtwarta((m) => !m)}>
          {mapaOtwarta ? t('meetings.hideMap') : t('meetings.pickOnMap')}
        </Button>
        {punkt && (
          <Button size="sm" variant="link" className="p-0" onClick={() => setPunkt(null)}>{t('meetings.removePoint')}</Button>
        )}
      </div>
      {pozycja && <div className="small text-body-secondary mb-1" role="status">{t(pozycja)}</div>}
      {mapaOtwarta && (
        <div className="mb-2">
          <MapaPunktu lat={punkt?.lat} lon={punkt?.lon} podpis={miejsce || t('meetings.title')} wysokosc={190}
            onWybierz={(lat, lon) => setPunkt({ lat, lon })} />
          <div className="small text-body-secondary mt-1">{t('map.pickHint')}</div>
        </div>
      )}

      <div className="spotkanie-czasy">
        <Form.Group controlId={`${idPrefix}-data`}>
          <Form.Label className="small fw-semibold mb-1">{t('meetings.date')}</Form.Label>
          <Form.Control type="date" value={data} onChange={(e) => setData(e.target.value)} isInvalid={Boolean(bledy.od)} />
        </Form.Group>
        <Form.Group controlId={`${idPrefix}-od`}>
          <Form.Label className="small fw-semibold mb-1">{t('meetings.from')}</Form.Label>
          <Form.Control type="time" value={od} onChange={(e) => setOd(e.target.value)} isInvalid={Boolean(bledy.od)} />
        </Form.Group>
        <Form.Group controlId={`${idPrefix}-do`}>
          <Form.Label className="small fw-semibold mb-1">{t('meetings.to')}</Form.Label>
          <Form.Control type="time" value={doG} onChange={(e) => setDoG(e.target.value)} isInvalid={Boolean(bledy.do)} />
        </Form.Group>
      </div>
      {(bledy.od || bledy.do) && <div className="small text-danger mb-1">{t(bledy.od ?? bledy.do)}</div>}

      <Form.Group controlId={`${idPrefix}-przypomnienie`} className="mb-2">
        <Form.Label className="small fw-semibold mb-1">{t('meetings.reminder')}</Form.Label>
        <Form.Select value={przypomnienie} onChange={(e) => setPrzypomnienie(Number(e.target.value))}>
          {PRZYPOMNIENIA.map((m) => (
            <option key={m} value={m}>{m === 0 ? t('meetings.remind.none') : t('meetings.remind.before', { czas: czasPrzypomnienia(m, t) })}</option>
          ))}
        </Form.Select>
      </Form.Group>

      <Form.Group controlId={`${idPrefix}-notatka`} className="mb-2">
        <Form.Label className="small fw-semibold mb-1">{t('meetings.note')}</Form.Label>
        <Form.Control as="textarea" rows={2} maxLength={MAKS_NOTATKA} value={notatka}
          onChange={(e) => setNotatka(e.target.value)} placeholder={t('meetings.notePlaceholder')}
          isInvalid={Boolean(bledy.notatka)} />
      </Form.Group>

      {blad && <div className="chat-error">{blad}</div>}
      <Button type="submit" size="sm" disabled={wysylanie}>{wysylanie ? t('settings.saving') : t('meetings.send')}</Button>
    </form>
  );
}
