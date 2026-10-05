import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import { describeError } from '../../api/client';

export const MIEJSCA = [2, 3, 4, 5, 6, 8, 10, 12];
const MAKS_TYTUL = 60;
const MAKS_OPIS = 300;
const MAKS_MIASTO = 60;

/**
 * Zakladanie albo zmiana ekipy: nazwa (opcjonalna), kilka slow o tym, jak jedziecie, skad wyruszacie, ile miejsc
 * i czy wchodzi sie od razu, czy za zgoda. Miasto wyjazdu ustawia kolejnosc ekip u innych (blizej = wyzej).
 */
export default function EkipaForm({ poczatek = null, miastoDomyslne = '', onZapisz, onAnuluj, idPrefix = 'ekipa' }) {
  const { t } = useTranslation();
  const [tytul, setTytul] = useState(poczatek?.title ?? '');
  const [opis, setOpis] = useState(poczatek?.description ?? '');
  const [miejsca, setMiejsca] = useState(poczatek?.capacity ?? 6);
  const [nabor, setNabor] = useState(poczatek?.joinPolicy ?? 'OPEN');
  const [miasto, setMiasto] = useState(poczatek?.departureCity ?? miastoDomyslne ?? '');
  const [zapis, setZapis] = useState(false);
  const [blad, setBlad] = useState(null);

  async function wyslij(e) {
    e.preventDefault();
    setZapis(true);
    setBlad(null);
    try {
      await onZapisz({
        title: tytul.trim() || null,
        description: opis.trim() || null,
        capacity: Number(miejsca),
        joinPolicy: nabor,
        departureCity: miasto.trim() || null,
      });
    } catch (problem) {
      setBlad(describeError(problem).message);
    } finally {
      setZapis(false);
    }
  }

  return (
    <form className="ekipa-form" onSubmit={wyslij} aria-label={poczatek ? t('crews.form.editTitle') : t('crews.form.title')}>
      <Form.Group controlId={`${idPrefix}-tytul`} className="mb-2">
        <Form.Label className="small fw-semibold mb-1">{t('crews.form.name')}</Form.Label>
        <Form.Control value={tytul} maxLength={MAKS_TYTUL} onChange={(e) => setTytul(e.target.value)}
          placeholder={t('crews.form.namePlaceholder')} />
      </Form.Group>
      <Form.Group controlId={`${idPrefix}-opis`} className="mb-2">
        <Form.Label className="small fw-semibold mb-1">{t('crews.form.description')}</Form.Label>
        <Form.Control as="textarea" rows={2} value={opis} maxLength={MAKS_OPIS} onChange={(e) => setOpis(e.target.value)}
          placeholder={t('crews.form.descriptionPlaceholder')} />
      </Form.Group>
      <div className="ekipa-form-rzad">
        <Form.Group controlId={`${idPrefix}-miasto`}>
          <Form.Label className="small fw-semibold mb-1">{t('crews.form.city')}</Form.Label>
          <Form.Control value={miasto} maxLength={MAKS_MIASTO} onChange={(e) => setMiasto(e.target.value)}
            placeholder={t('crews.form.cityPlaceholder')} />
        </Form.Group>
        <Form.Group controlId={`${idPrefix}-miejsca`}>
          <Form.Label className="small fw-semibold mb-1">{t('crews.form.capacity')}</Form.Label>
          <Form.Select value={miejsca} onChange={(e) => setMiejsca(e.target.value)}>
            {MIEJSCA.map((n) => <option key={n} value={n}>{t('crews.form.people', { count: n })}</option>)}
          </Form.Select>
        </Form.Group>
      </div>
      <fieldset className="mb-2">
        <legend className="small fw-semibold mb-1">{t('crews.form.policy')}</legend>
        <Form.Check type="radio" id={`${idPrefix}-otwarta`} name={`${idPrefix}-nabor`} checked={nabor === 'OPEN'}
          onChange={() => setNabor('OPEN')} label={t('crews.policy.OPEN')} />
        <Form.Check type="radio" id={`${idPrefix}-zgoda`} name={`${idPrefix}-nabor`} checked={nabor === 'APPROVAL'}
          onChange={() => setNabor('APPROVAL')} label={t('crews.policy.APPROVAL')} />
      </fieldset>
      {blad && <div className="small text-danger mb-2" role="alert">{blad}</div>}
      <div className="d-flex gap-2">
        <Button type="submit" size="sm" disabled={zapis}>
          {zapis ? t('settings.saving') : poczatek ? t('crews.form.save') : t('crews.form.create')}
        </Button>
        {onAnuluj && <Button size="sm" variant="outline-secondary" onClick={onAnuluj}>{t('common.cancel')}</Button>}
      </div>
    </form>
  );
}
