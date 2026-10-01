import { useTranslation } from 'react-i18next';
import Form from 'react-bootstrap/Form';
import { ZASIEGI } from '../hooks/useZasieg';

/** Lista "Zasieg": do 30, 50, 100, 200 km od mojego miasta albo caly kraj. */
export default function Zasieg({ wartosc, onChange, miasto, className = '', disabled = false }) {
  const { t } = useTranslation();
  return (
    <Form.Select
      value={wartosc}
      onChange={(e) => onChange(Number(e.target.value))}
      aria-label={t('location.rangeLabel', { city: miasto })}
      title={t('location.rangeLabel', { city: miasto })}
      className={className}
      disabled={disabled}
    >
      {ZASIEGI.map((km) => (
        <option key={km} value={km}>
          {km === 0 ? t('location.rangeAll') : t('location.rangeKm', { km })}
        </option>
      ))}
    </Form.Select>
  );
}
