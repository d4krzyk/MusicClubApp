import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Navbar from 'react-bootstrap/Navbar';
import Nav from 'react-bootstrap/Nav';
import Container from 'react-bootstrap/Container';
import ButtonGroup from 'react-bootstrap/ButtonGroup';
import Badge from 'react-bootstrap/Badge';
import Button from 'react-bootstrap/Button';
import NavDropdown from 'react-bootstrap/NavDropdown';
import client from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';
import { LANGUAGES, changeLanguage } from '../i18n';
import Avatar from './Avatar';
import { IconGear, IconLogout, IconMoon, IconPerson, IconSun } from './Icons';

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
  const { dark, toggle } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();

  /*
   * Ile zaproszen czeka na odpowiedz. Odswiezamy przy kazdej zmianie adresu -
   * to najprostszy moment, w ktorym liczba moze sie zdezaktualizowac
   * (np. po przyjeciu zaproszenia na stronie "Znajomi").
   */
  const [pending, setPending] = useState(0);

  /*
   * Czy strona jest przewinieta. Sluzy tylko do dorysowania cienia pod
   * paskiem: dopoki tresc zaczyna sie tuz pod nim, cien wygladalby jak
   * przypadkowa kreska, a przy przewinietej stronie pokazuje, ze pasek
   * faktycznie na czyms lezy.
   */
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 4);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    if (!user) {
      setPending(0);
      return;
    }
    client.get('/friends/requests/count')
      .then((o) => setPending(o.data.count))
      .catch(() => setPending(0));   // licznik to dodatek, nie psujemy menu
  }, [user, location.pathname]);

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <>
      {/*
        sticky-top: pasek zostaje na gorze przy przewijaniu. Bez tego przy
        dluzszej tablicy trzeba bylo wracac na sam poczatek strony, zeby
        gdziekolwiek przejsc.
      */}
      <Navbar expand="md" sticky="top" className={`top-bar border-bottom${scrolled ? ' is-scrolled' : ''}`}>
        <Container>
          <Navbar.Brand as={Link} to="/" className="fw-bold text-primary">
            {t('app.name')}
          </Navbar.Brand>

          <Navbar.Toggle aria-controls="menu-glowne" />

          <Navbar.Collapse id="menu-glowne">
            <Nav className="me-auto">
              {user ? (
                <>
                  {/*
                    W menu zostaja WYLACZNIE miejsca wspolne dla wszystkich.
                    Tablica jest teraz strona glowna (prowadzi do niej logo),
                    a wszystko, co dotyczy wlasnego konta - profil i ustawienia -
                    siedzi pod awatarem po prawej. Menu z pozycjami "Strona
                    glowna" i "Tablica" wskazujacymi na to samo tylko myli.
                  */}
                  <Nav.Link as={NavLink} to="/znajomi">
                    {t('menu.friends')}
                    {/*
                      Liczba oczekujacych zaproszen. Pokazujemy ja tylko, gdy
                      jest wieksza od zera - stale "0" obok pozycji w menu
                      to szum, ktory nic nie wnosi.
                    */}
                    {pending > 0 && (
                      <Badge bg="primary" className="ms-1">{pending}</Badge>
                    )}
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
                {LANGUAGES.map((code) => (
                  <Button
                    key={code}
                    variant={i18n.language === code ? 'primary' : 'outline-secondary'}
                    onClick={() => changeLanguage(code)}
                    aria-pressed={i18n.language === code}
                  >
                    {code.toUpperCase()}
                  </Button>
                ))}
              </ButtonGroup>

              {/*
                Przelacznik motywu. Pokazujemy ikone tego, co WLACZYMY po
                kliknieciu (slonce = "wlacz jasny"), a nie tego, co jest teraz -
                przycisk ma mowic, co zrobi, a nie opisywac stan.
              */}
              <Button
                variant="outline-secondary"
                size="sm"
                onClick={toggle}
                title={dark ? t('menu.themeLight') : t('menu.themeDark')}
                aria-label={dark ? t('menu.themeLight') : t('menu.themeDark')}
              >
                {dark ? <IconSun /> : <IconMoon />}
              </Button>

              {user && (
                /*
                  Wszystko, co dotyczy wlasnego konta, w jednym miejscu:
                  klikniecie w awatar z nazwa rozwija profil, ustawienia
                  i wylogowanie. Trzy osobne pozycje w menu robily z paska
                  na gorze liste odnosnikow, w ktorej ginely te naprawde
                  wspolne - znajomi i (u administratora) konta.
                */
                <NavDropdown
                  align="end"
                  className="account-menu"
                  title={
                    <span className="d-inline-flex align-items-center gap-2">
                      <Avatar avatarUrl={user.avatarUrl} username={user.username} size={32} />
                      <span className="d-none d-lg-inline">{user.username}</span>
                    </span>
                  }
                  id="account-menu"
                >
                  <NavDropdown.Item as={Link} to="/profil">
                    <IconPerson className="me-2" />
                    {t('menu.myProfile')}
                  </NavDropdown.Item>

                  <NavDropdown.Item as={Link} to="/settings">
                    <IconGear className="me-2" />
                    {t('menu.settings')}
                  </NavDropdown.Item>

                  <NavDropdown.Divider />

                  <NavDropdown.Item onClick={handleLogout}>
                    <IconLogout className="me-2" />
                    {t('menu.logout')}
                  </NavDropdown.Item>
                </NavDropdown>
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
