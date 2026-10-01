import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import { IconCross } from '../Icons';
import {
  MAKS_GATUNEK, MAKS_GATUNKOW, MAKS_HASLO, MAKS_MIASTO, PODPOWIEDZI_GATUNKOW,
} from '../../utils/klan';

/** Wartosci wizytowki dla nowego klanu: klan jest w przegladarce, ale tylko na zaproszenie. */
export const PUSTA_WIZYTOWKA = { motto: '', city: '', genres: [], joinPolicy: 'INVITE_ONLY', listed: true };

/** Wizytowka istniejacego klanu - to, co klan o sobie mowi w przegladarce. */
export function wizytowkaKlanu(klan) {
  return {
    motto: klan.motto ?? '',
    city: klan.city ?? '',
    genres: klan.genres ?? [],
    joinPolicy: klan.joinPolicy ?? 'INVITE_ONLY',
    listed: klan.listed ?? true,
  };
}

/**
 * Pola "wizytowki" klanu: haslo, miasto, do trzech gatunkow, sposob dolaczania i to, czy klan
 * ma sie pokazywac w przegladarce. Uzywane przy zakladaniu klanu i w jego ustawieniach.
 * Stan trzyma rodzic - tu tylko pola.
 */
export default function KlanWizytowka({ wartosc, onZmien, zablokowane = false }) {
  const { t } = useTranslation();
  const id = useId();
  const [nowy, setNowy] = useState('');
  const pelno = wartosc.genres.length >= MAKS_GATUNKOW;

  function dodajGatunek() {
    const gatunek = nowy.trim().replace(/\s+/g, ' ').toLowerCase();
    if (gatunek.length < 2 || pelno || wartosc.genres.includes(gatunek)) {
      return;
    }
    onZmien({ genres: [...wartosc.genres, gatunek] });
    setNowy('');
  }

  function nacisniecie(e) {
    // Enter w tym polu dodaje gatunek, a nie wysyla calego formularza
    if (e.key === 'Enter') {
      e.preventDefault();
      dodajGatunek();
    }
  }

  return (
    <div className="d-grid gap-3">
      <div>
        <Form.Label htmlFor={`${id}-haslo`}>{t('clans.card.motto')}</Form.Label>
        <Form.Control id={`${id}-haslo`} value={wartosc.motto} maxLength={MAKS_HASLO} disabled={zablokowane}
          placeholder={t('clans.card.mottoPlaceholder')} onChange={(e) => onZmien({ motto: e.target.value })} />
      </div>

      <div className="row g-2">
        <div className="col-12 col-sm-6">
          <Form.Label htmlFor={`${id}-miasto`}>{t('clans.card.city')}</Form.Label>
          <Form.Control id={`${id}-miasto`} value={wartosc.city} maxLength={MAKS_MIASTO} disabled={zablokowane}
            placeholder={t('clans.card.cityPlaceholder')} onChange={(e) => onZmien({ city: e.target.value })} />
        </div>
        <div className="col-12 col-sm-6">
          <Form.Label htmlFor={`${id}-gatunek`}>
            {t('clans.card.genres')} <span className="text-body-secondary">({wartosc.genres.length}/{MAKS_GATUNKOW})</span>
          </Form.Label>
          <div className="d-flex gap-2">
            <Form.Control id={`${id}-gatunek`} value={nowy} maxLength={MAKS_GATUNEK} list={`${id}-podpowiedzi`}
              disabled={zablokowane || pelno} autoComplete="off" autoCapitalize="none"
              placeholder={pelno ? t('clans.card.genresFull') : t('clans.card.genrePlaceholder')}
              onChange={(e) => setNowy(e.target.value)} onKeyDown={nacisniecie} />
            <datalist id={`${id}-podpowiedzi`}>
              {PODPOWIEDZI_GATUNKOW.filter((g) => !wartosc.genres.includes(g)).map((g) => <option key={g} value={g} />)}
            </datalist>
            <Button type="button" variant="outline-secondary" disabled={zablokowane || pelno || nowy.trim().length < 2}
              onClick={dodajGatunek}>
              {t('clans.card.addGenre')}
            </Button>
          </div>
        </div>
      </div>

      {wartosc.genres.length > 0 && (
        <ul className="klan-gatunki-wybrane list-unstyled mb-0" aria-label={t('clans.card.genres')}>
          {wartosc.genres.map((g) => (
            <li key={g} className="klan-gatunek klan-gatunek-wybrany">
              {g}
              <button type="button" className="klan-gatunek-usun" disabled={zablokowane}
                aria-label={t('clans.card.removeGenre', { genre: g })}
                onClick={() => onZmien({ genres: wartosc.genres.filter((x) => x !== g) })}>
                <IconCross size={10} />
              </button>
            </li>
          ))}
        </ul>
      )}
      <Form.Text className="mt-n2">{t('clans.card.genresHint')}</Form.Text>

      <fieldset className="klan-nabor" disabled={zablokowane}>
        <legend className="form-label fs-6 mb-1">{t('clans.card.joinPolicy')}</legend>
        <Form.Check type="radio" id={`${id}-nabor-prosby`} name={`${id}-nabor`}
          label={t('clans.card.policy.REQUESTS')} checked={wartosc.joinPolicy === 'REQUESTS'}
          onChange={() => onZmien({ joinPolicy: 'REQUESTS' })} />
        <Form.Check type="radio" id={`${id}-nabor-zaproszenia`} name={`${id}-nabor`}
          label={t('clans.card.policy.INVITE_ONLY')} checked={wartosc.joinPolicy === 'INVITE_ONLY'}
          onChange={() => onZmien({ joinPolicy: 'INVITE_ONLY' })} />
        <Form.Text className="d-block">{t('clans.card.joinPolicyHint')}</Form.Text>
      </fieldset>

      <div>
        <Form.Check type="switch" id={`${id}-widoczny`} label={t('clans.card.listed')} checked={wartosc.listed}
          disabled={zablokowane} onChange={(e) => onZmien({ listed: e.target.checked })} />
        <Form.Text className="d-block">{t('clans.card.listedHint')}</Form.Text>
      </div>
    </div>
  );
}
