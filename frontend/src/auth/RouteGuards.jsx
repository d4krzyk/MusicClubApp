import { Navigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Spinner from 'react-bootstrap/Spinner';
import { useAuth } from './AuthContext';

/** Ekran "ladowanie" pokazywany, zanim poznamy stan sesji. */
function LoadingScreen() {
  const { t } = useTranslation();
  return (
    <div className="text-center py-5 text-body-secondary">
      <Spinner animation="border" size="sm" className="me-2" />
      {t('common.loading')}
    </div>
  );
}

/** Strona tylko dla ZALOGOWANYCH. */
export function RequireAuth({ children }) {
  const { user, checkingSession } = useAuth();
  const location = useLocation();

  if (checkingSession) {
    return <LoadingScreen />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return children;
}

/** Strona tylko dla ADMINISTRATORA. */
export function RequireAdmin({ children }) {
  const { user, checkingSession } = useAuth();
  const location = useLocation();

  if (checkingSession) {
    return <LoadingScreen />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (!user.admin) {
    return <Navigate to="/" replace />;
  }
  return children;
}

/** Strona tylko dla NIEZALOGOWANYCH (logowanie, rejestracja). */
export function RequireAnonymous({ children }) {
  const { user, checkingSession } = useAuth();

  if (checkingSession) {
    return <LoadingScreen />;
  }
  if (user) {
    return <Navigate to="/" replace />;
  }
  return children;
}
