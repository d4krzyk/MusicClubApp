import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Form from 'react-bootstrap/Form';
import { describeError } from '../../api/client';
import * as klany from '../../api/klany';
import { DOMYSLNY_KOLOR_KLANU } from '../../utils/klan';
import ClanBadge from '../ClanBadge';
import { IconClan } from '../Icons';
import KlanWizytowka, { PUSTA_WIZYTOWKA } from './KlanWizytowka';

/**
 * Formularz zakladania klanu: nazwa, skrot, opis, ikona (z podgladem plakietki tak, jak
 * zobacza ja inni obok loginu), a pod "Wiecej" haslo, miasto, gatunki i sposob dolaczania.
 *
 * Ikone wysyla sie dopiero PO zalozeniu klanu (potrzebny numer) - gdyby wgranie sie nie
 * udalo, klan i tak powstaje, a komunikat mowi, ze ikone mozna dodac w ustawieniach.
 */
export default function KlanZakladanie({ onZalozony }) {
  const { t } = useTranslation();
  const [nazwa, setNazwa] = useState('');
  const [skrot, setSkrot] = useState('');
  const [opis, setOpis] = useState('');
  const [wizytowka, setWizytowka] = useState(PUSTA_WIZYTOWKA);
  const [ikona, setIkona] = useState(null);
  const [podglad, setPodglad] = useState(null);
  const [zajety, setZajety] = useState(false);
  const [blad, setBlad] = useState(null);
  const pole = useRef(null);

  // Adres podgladu trzeba zwolnic, gdy plik sie zmienia albo formularz znika
  useEffect(() => {
    if (!ikona) {
      setPodglad(null);
      return undefined;
    }
    const adres = URL.createObjectURL(ikona);
    setPodglad(adres);
    return () => URL.revokeObjectURL(adres);
  }, [ikona]);

  function wybierz(e) {
    const plik = e.target.files?.[0] ?? null;
    if (plik && !plik.type.startsWith('image/')) {
      setBlad(t('clans.create.iconNotImage'));
      e.target.value = '';
      return;
    }
    setBlad(null);
    setIkona(plik);
  }

  function zdejmij() {
    setIkona(null);
    if (pole.current) {
      pole.current.value = '';
    }
  }

  async function zaloz(e) {
    e.preventDefault();
    setZajety(true);
    setBlad(null);
    let klan;
    try {
      klan = await klany.zaloz({
        name: nazwa,
        tag: skrot,
        description: opis,
        motto: wizytowka.motto,
        city: wizytowka.city,
        genres: wizytowka.genres,
        joinPolicy: wizytowka.joinPolicy,
        listed: wizytowka.listed,
      });
    } catch (problem) {
      setBlad(describeError(problem).message);
      setZajety(false);
      return;
    }

    let ostrzezenie = null;
    if (ikona) {
      try {
        klan = await klany.wgrajObraz(klan.id, 'icon', ikona);
      } catch (problem) {
        // klan juz istnieje - nie cofamy go z powodu obrazka
        ostrzezenie = t('clans.create.iconFailed', { reason: describeError(problem).message });
      }
    }
    onZalozony(klan, ostrzezenie);
  }

  const wyglad = { tag: skrot || 'MC', name: nazwa, colorHex: DOMYSLNY_KOLOR_KLANU, iconUrl: podglad };

  return (
    <Card>
      <Card.Body>
        <Card.Title as="h2" className="h6 text-uppercase text-body-secondary">{t('clans.create.title')}</Card.Title>
        <p className="small text-body-secondary">{t('clans.create.hint')}</p>
        {blad && <Alert variant="danger">{blad}</Alert>}
        <Form onSubmit={zaloz} noValidate>
          <div className="row g-2 mb-3">
            <div className="col-12 col-sm-8">
              <Form.Label htmlFor="nowy-klan-nazwa">{t('clans.create.name')}</Form.Label>
              <Form.Control id="nowy-klan-nazwa" value={nazwa} maxLength={32} onChange={(e) => setNazwa(e.target.value)} />
            </div>
            <div className="col-12 col-sm-4">
              <Form.Label htmlFor="nowy-klan-skrot">{t('clans.create.tag')}</Form.Label>
              <Form.Control id="nowy-klan-skrot" value={skrot} maxLength={5} autoCapitalize="characters"
                onChange={(e) => setSkrot(e.target.value.toUpperCase())} />
              <Form.Text>{t('clans.create.tagHint')}</Form.Text>
            </div>
          </div>

          {/* Ikona i podglad plakietki: tak klan bedzie wygladal obok loginu */}
          <div className="klan-zakladanie-ikona mb-3">
            <div className="klan-ikona-podglad" aria-hidden="true">
              {podglad ? <img src={podglad} alt="" /> : <IconClan size={28} />}
            </div>
            <div className="klan-zakladanie-ikona-pola">
              <Form.Label htmlFor="nowy-klan-ikona" className="mb-1">{t('clans.create.icon')}</Form.Label>
              <Form.Control id="nowy-klan-ikona" type="file" accept="image/*" size="sm" ref={pole} onChange={wybierz}
                aria-describedby="nowy-klan-ikona-podp" />
              <Form.Text id="nowy-klan-ikona-podp" className="d-block">{t('clans.create.iconHint')}</Form.Text>
              {ikona && (
                <Button variant="link" size="sm" className="px-0 text-danger" onClick={zdejmij}>
                  {t('clans.settings.remove')}
                </Button>
              )}
            </div>
            <div className="klan-podglad-plakietki" aria-live="polite">
              <span className="small text-body-secondary d-block mb-1">{t('clans.create.preview')}</span>
              <span className="klan-podglad-wiersz">
                <span className="fw-semibold text-truncate">{t('clans.create.previewUser')}</span>
                <ClanBadge clan={wyglad} link={false} />
              </span>
            </div>
          </div>

          <Form.Label htmlFor="nowy-klan-opis">{t('clans.create.description')}</Form.Label>
          <Form.Control id="nowy-klan-opis" as="textarea" rows={3} maxLength={300} value={opis}
            onChange={(e) => setOpis(e.target.value)} className="mb-3" />

          <details className="klan-wiecej mb-3">
            <summary>{t('clans.create.more')}</summary>
            <div className="pt-3">
              <KlanWizytowka wartosc={wizytowka} zablokowane={zajety}
                onZmien={(zmiana) => setWizytowka((obecna) => ({ ...obecna, ...zmiana }))} />
            </div>
          </details>

          <Button type="submit" disabled={zajety || !nazwa.trim() || !skrot.trim()}>{t('clans.create.submit')}</Button>
        </Form>
      </Card.Body>
    </Card>
  );
}
