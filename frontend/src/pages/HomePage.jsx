import { Link } from 'react-router-dom';
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

      {/*
        Celowo NIE pokazujemy tu roli. Napis "USER" nic zwyklemu uzytkownikowi
        nie mowi, a tylko ujawnia, ze aplikacja dzieli konta na rodzaje.
        Backend zreszta w ogole nie przysyla juz tego pola - patrz UserResponse.
      */}
      <dl className="dane">
        <dt>{t('home.email')}</dt>
        <dd>{user.email}</dd>

        {/* Wymaganie nr 4 - data z encji, pokazana w formacie wybranego jezyka */}
        <dt>{t('home.memberSince')}</dt>
        <dd>{sformatujDate(user.createdAt, i18n.language)}</dd>
      </dl>

      <p className="pod-formularzem" style={{ textAlign: 'left' }}>
        <Link to="/settings">{t('home.goToSettings')}</Link>
      </p>

      <h2>{t('home.nextSteps')}</h2>
      <p className="info">{t('home.nextStepsText')}</p>
    </div>
  );
}
