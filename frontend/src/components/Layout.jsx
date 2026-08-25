import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Navbar from 'react-bootstrap/Navbar';
import Nav from 'react-bootstrap/Nav';
import Container from 'react-bootstrap/Container';
import ButtonGroup from 'react-bootstrap/ButtonGroup';
import Button from 'react-bootstrap/Button';
import { useAuth } from '../auth/AuthContext';
import { JEZYKI, zmienJezyk } from '../i18n';
import Avatar from './Avatar';

/**
 * Wspolna rama strony: gorne menu, tresc i stopka.
 *
 * <p>Menu w dwoch jezykach to czesc czerwonego wymagania nr 2 - lista mowi
 * wprost o "wiadomosciach podczas bledow walidacji" ORAZ "menu na stronie".</p>
 *
 * <p>{@code Navbar expand="md"} z Bootstrapa sam zwija menu w "hamburgera"
 * na waskich ekranach - wczesniej trzeba by to obsluzyc wlasnym CSS-em.</p>
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
    <>
      <Navbar expand="md" bg="body-tertiary" className="border-bottom">
        <Container>
          <Navbar.Brand as={Link} to="/" className="fw-bold text-primary">
            {t('app.name')}
          </Navbar.Brand>

          <Navbar.Toggle aria-controls="menu-glowne" />

          <Navbar.Collapse id="menu-glowne">
            <Nav className="me-auto">
              {user ? (
                <>
                  <Nav.Link as={NavLink} to="/" end>
                    {t('menu.home')}
                  </Nav.Link>
                  <Nav.Link as={NavLink} to="/feed">
                    {t('menu.feed')}
                  </Nav.Link>
                  <Nav.Link as={NavLink} to="/profil" end>
                    {t('menu.profile')}
                  </Nav.Link>
                  <Nav.Link as={NavLink} to="/settings">
                    {t('menu.settings')}
                  </Nav.Link>

                  {/*
                    Lista uzytkownikow to funkcja administracyjna - zwykly
                    uzytkownik nie widzi nawet linku. Prawdziwa blokada siedzi
                    w backendzie (403), to tylko porzadek w menu.
                  */}
                  {user.admin && (
                    <Nav.Link as={NavLink} to="/users">
                      {t('menu.users')}
                    </Nav.Link>
                  )}
                </>
              ) : (
                <>
                  <Nav.Link as={NavLink} to="/login">
                    {t('menu.login')}
                  </Nav.Link>
                  <Nav.Link as={NavLink} to="/register">
                    {t('menu.register')}
                  </Nav.Link>
                </>
              )}
            </Nav>

            <div className="d-flex align-items-center gap-3">
              {/* Przelacznik jezyka - widoczny zawsze, takze przed zalogowaniem */}
              <ButtonGroup size="sm" aria-label={t('menu.language')}>
                {JEZYKI.map((kod) => (
                  <Button
                    key={kod}
                    variant={i18n.language === kod ? 'primary' : 'outline-secondary'}
                    onClick={() => zmienJezyk(kod)}
                    aria-pressed={i18n.language === kod}
                  >
                    {kod.toUpperCase()}
                  </Button>
                ))}
              </ButtonGroup>

              {user && (
                <>
                  {/*
                    Wlasny awatar prowadzi na PROFIL, nie do ustawien -
                    tak dziala to w kazdym serwisie spolecznosciowym.
                    Ustawienia maja swoja pozycje w menu obok.
                  */}
                  <Link
                    to="/profil"
                    className="d-flex align-items-center gap-2 text-decoration-none text-body"
                  >
                    <Avatar avatarUrl={user.avatarUrl} username={user.username} rozmiar={32} />
                    <span className="d-none d-lg-inline">{user.username}</span>
                  </Link>

                  <Button variant="outline-secondary" size="sm" onClick={wyloguj}>
                    {t('menu.logout')}
                  </Button>
                </>
              )}
            </div>
          </Navbar.Collapse>
        </Container>
      </Navbar>

      <main className="flex-grow-1 py-4">
        <Container>{children}</Container>
      </main>

      <footer className="border-top py-3 text-center text-body-secondary small">
        {t('app.tagline')}
      </footer>
    </>
  );
}
