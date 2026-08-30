import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Navbar from 'react-bootstrap/Navbar';
import Nav from 'react-bootstrap/Nav';
import Container from 'react-bootstrap/Container';
import NavDropdown from 'react-bootstrap/NavDropdown';
import { licznikZaproszen } from '../api/znajomi';
import { licznikOtwartych } from '../api/moderacja';
import AccountSwitchNotice from './AccountSwitchNotice';
import { useAuth } from '../auth/AuthContext';
import Avatar from './Avatar';
import ChatDrawer from './ChatDrawer';
import ChatLauncher from './ChatLauncher';
import {
  IconBoard, IconFlag, IconFriends, IconGear, IconLogout, IconNote, IconPerson,
  IconShield, IconShieldAlert,
} from './Icons';
import LanguageSwitch from './LanguageSwitch';
import NotificationsBell from './NotificationsBell';
import ThemeToggle from './ThemeToggle';
import { scrollToTop } from '../utils/scroll';

/** Wspolna rama strony: gorne menu, tresc i stopka. */
export default function Layout({ children }) {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  /* Ile zaproszen czeka na odpowiedz. */
  const [pending, setPending] = useState(0);

  /* Ile zgloszen czeka na decyzje. */
  const [openReports, setOpenReports] = useState(0);

  /* Czy strona jest przewinieta. */
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
    licznikZaproszen()
      .then(setPending)
      .catch(() => setPending(0));   // licznik to dodatek, nie psujemy menu

    if (user.admin) {
      licznikOtwartych()
        .then(setOpenReports)
        .catch(() => setOpenReports(0));
    }
  }, [user, location.pathname]);

  /* Zmiana strony zaczyna sie OD GORY. */
  useEffect(() => {
    scrollToTop(false);
  }, [location.pathname]);

  /** Klikniecie w logo albo w ikone tablicy. */
  function goToFeed(event) {
    event.preventDefault();

    const alreadyHere = location.pathname === '/';
    navigate('/', {
      state: { refreshAt: Date.now() },
      // Na tablicy nie dokladamy wpisow do historii - inaczej "wstecz"
      // cofaloby przez kilkanascie odswiezen tej samej strony
      replace: alreadyHere,
    });

    if (alreadyHere) {
      scrollToTop(true);
    }
  }

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <>
      {/* Pasek gorny w ukladzie trzech kolumn: logo | ikony | konto. */}
      <Navbar
        sticky="top"
        className={`top-bar${scrolled ? ' is-scrolled' : ''}`}
      >
        <Container className="nav-grid">
          <Navbar.Brand
            as={Link}
            to="/"
            className="brand"
            aria-label={t('app.name')}
            onClick={goToFeed}
          >
            {/* Znak marki ma NUTKE, a nie tę samą ikonę co pozycja "Tablica" w menu obok. */}
            <span className="brand-mark" aria-hidden="true">
              <IconNote size={18} />
            </span>
            <span className="brand-text">{t('app.name')}</span>
          </Navbar.Brand>

          <Nav className="nav-icons">
            {user ? (
              <>
                <NavIcon to="/" end label={t('menu.feed')} onClick={goToFeed}>
                  <IconBoard size={20} />
                </NavIcon>

                <NavIcon to="/znajomi" label={t('menu.friends')} badge={pending}>
                  <IconFriends size={20} />
                </NavIcon>

                {/* Panel administratora. */}
                {user.admin && (
                  <NavIcon to="/users" label={t('menu.users')}>
                    <IconShield size={20} />
                  </NavIcon>
                )}

                {user.admin && (
                  <NavIcon
                    to="/zgloszenia"
                    label={t('menu.reports')}
                    badge={openReports}
                  >
                    <IconShieldAlert size={20} />
                  </NavIcon>
                )}
              </>
            ) : (
              <>
                <Nav.Link as={NavLink} to="/login" className="nav-text-link">
                  {t('menu.login')}
                </Nav.Link>
                <Nav.Link as={NavLink} to="/register" className="nav-text-link">
                  {t('menu.register')}
                </Nav.Link>
              </>
            )}
          </Nav>

          <div className="nav-right">
            {/*
              Dzwonek stoi przy awatarze, bo jedno i drugie dotyczy MNIE - w odroznieniu od jezyka
              i motywu, ktore dotycza calej strony
            */}
            {user && <ChatLauncher />}
            {user && <NotificationsBell />}

            <LanguageSwitch />
            <ThemeToggle />

            {user && (
              /*
               * Wszystko, co dotyczy wlasnego konta, w jednym miejscu: klikniecie w awatar z nazwa
               * rozwija profil, ustawienia i wylogowanie.
               */
              <NavDropdown
                align="end"
                className="account-menu"
                title={
                  <span className="account-trigger">
                    <span className="avatar-ring">
                      <Avatar avatarUrl={user.avatarUrl} username={user.username} size={30} />
                    </span>
                    <span className="d-none d-lg-inline">{user.username}</span>
                  </span>
                }
                id="account-menu"
              >
                <NavDropdown.Item as={Link} to="/profil">
                  <IconPerson className="me-2" />
                  {t('menu.myProfile')}
                </NavDropdown.Item>

                <NavDropdown.Item as={Link} to="/moje-zgloszenia">
                  <IconFlag className="me-2" />
                  {t('menu.myReports')}
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
        </Container>
      </Navbar>

      <main className="flex-grow-1 py-4">
        <Container>
          <AccountSwitchNotice />
          {children}
        </Container>
      </main>

      {/* Panel czatu stoi POZA <main>, bo nie jest czescia tresci strony - wysuwa sie nad nia. */}
      {user && <ChatDrawer />}

      <footer className="border-top py-3 text-center text-body-secondary small">
        {t('app.tagline')}
      </footer>
    </>
  );
}

/** Jedna pozycja nawigacji: sama ikona z podpowiedzia i licznikiem. */
function NavIcon({ to, end, label, badge = 0, onClick, children }) {
  return (
    <Nav.Link
      as={NavLink}
      to={to}
      end={end}
      className="nav-icon"
      title={label}
      aria-label={label}
      onClick={onClick}
    >
      {children}

      {/* Liczba oczekujacych zaproszen. */}
      {badge > 0 && <span className="nav-badge">{badge}</span>}
    </Nav.Link>
  );
}
