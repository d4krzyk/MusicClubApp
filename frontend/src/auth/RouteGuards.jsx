import { Navigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from './AuthContext';

/** Prosty ekran "ladowanie" pokazywany, zanim poznamy stan sesji. */
function Ladowanie() {
  const { t } = useTranslation();
  return <p className="info">{t('common.loading')}</p>;
}

/**
 * Strona tylko dla ZALOGOWANYCH.
 *
 * <p>Niezalogowanego odsylamy na ekran logowania i zapamietujemy, dokad
 * chcial wejsc ({@code state.from}) - po zalogowaniu wracamy dokladnie tam,
 * zamiast zawsze na strone glowna.</p>
 */
export function TylkoZalogowany({ children }) {
  const { user, sprawdzanieSesji } = useAuth();
  const location = useLocation();

  if (sprawdzanieSesji) {
    return <Ladowanie />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return children;
}

/**
 * Strona tylko dla NIEZALOGOWANYCH (logowanie, rejestracja).
 *
 * <p>Bez tego zalogowany uzytkownik mogl wejsc na /login i zobaczyc formularz,
 * mimo ze jest juz w srodku - mylace.</p>
 */
export function TylkoNiezalogowany({ children }) {
  const { user, sprawdzanieSesji } = useAuth();

  if (sprawdzanieSesji) {
    return <Ladowanie />;
  }
  if (user) {
    return <Navigate to="/" replace />;
  }
  return children;
}
