import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { JEZYKI, zmienJezyk } from '../i18n';

/**
 * Wspolna rama strony: naglowek z menu, przelacznik jezyka i tresc.
 *
 * <p>Menu w dwoch jezykach to czesc czerwonego wymagania nr 2 - lista mowi
 * wprost o "wiadomosciach podczas bledow walidacji" ORAZ "menu na stronie".</p>
 */
export default function Layout({ children }) {
  const { t, i18n } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function wyloguj() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="strona">
      <header className="naglowek">
        <Link to="/" className="logo">
          {t('app.name')}
        </Link>

        <nav className="menu">
          {user ? (
            <>
              <NavLink to="/" end>
                {t('menu.home')}
              </NavLink>
              <NavLink to="/users">{t('menu.users')}</NavLink>
              <button type="button" className="link-button" onClick={wyloguj}>
                {t('menu.logout')}
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">{t('menu.login')}</NavLink>
              <NavLink to="/register">{t('menu.register')}</NavLink>
            </>
          )}

          {/* Przelacznik jezyka - widoczny zawsze, takze przed zalogowaniem */}
          <div className="jezyki" aria-label={t('menu.language')}>
            {JEZYKI.map((kod) => (
              <button
                key={kod}
                type="button"
                onClick={() => zmienJezyk(kod)}
                className={i18n.language === kod ? 'jezyk aktywny' : 'jezyk'}
                aria-pressed={i18n.language === kod}
              >
                {kod.toUpperCase()}
              </button>
            ))}
          </div>
        </nav>
      </header>

      <main className="tresc">{children}</main>

      <footer className="stopka">{t('app.tagline')}</footer>
    </div>
  );
}
