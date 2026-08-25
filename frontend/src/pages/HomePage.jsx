import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { sformatujDate } from '../utils/daty';

export default function HomePage() {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();

  return (
    <div className="karta">
      <h1>{t('home.welcome', { username: user.username })}</h1>

      <h2>{t('home.yourAccount')}</h2>
      <dl className="dane">
        <dt>{t('home.email')}</dt>
        <dd>{user.email}</dd>

        <dt>{t('home.role')}</dt>
        <dd>{user.role}</dd>

        {/* Wymaganie nr 4 - data z encji, pokazana w formacie wybranego jezyka */}
        <dt>{t('home.memberSince')}</dt>
        <dd>{sformatujDate(user.createdAt, i18n.language)}</dd>
      </dl>

      <h2>{t('home.nextSteps')}</h2>
      <p className="info">{t('home.nextStepsText')}</p>
    </div>
  );
}
