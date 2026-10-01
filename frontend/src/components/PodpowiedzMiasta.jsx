import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from 'react-bootstrap/Alert';
import { useAuth } from '../auth/AuthContext';
import { IconPin } from './Icons';

/**
 * Zacheta do ustawienia miasta, pokazywana tam, gdzie miasto cos zmienia (wydarzenia, klany, znajomi).
 * Znika, gdy miasto jest ustawione.
 */
export default function PodpowiedzMiasta({ tekst, className = '' }) {
  const { t } = useTranslation();
  const { user } = useAuth();
  if (!user || user.city) {
    return null;
  }
  return (
    <Alert variant="info" className={`py-2 small d-flex align-items-start gap-2 ${className}`}>
      <IconPin size={16} className="flex-shrink-0 mt-1" />
      <span>
        {tekst}{' '}
        <Link to="/settings#miasto">{t('location.setCity')}</Link>
      </span>
    </Alert>
  );
}
