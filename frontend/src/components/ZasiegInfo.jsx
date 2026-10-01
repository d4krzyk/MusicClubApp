import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

/** Mala linijka pod filtrami: od jakiego miasta liczy sie zasieg, z odnosnikiem do jego zmiany. */
export default function ZasiegInfo({ km, miasto, className = '' }) {
  const { t } = useTranslation();
  if (!km || !miasto) {
    return null;
  }
  return (
    <p className={`small text-body-secondary zasieg-info ${className}`}>
      {t('location.rangeNote', { km, city: miasto })}{' '}
      <Link to="/settings#miasto">{t('location.changeCity')}</Link>
    </p>
  );
}
