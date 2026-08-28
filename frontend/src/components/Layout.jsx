import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Navbar from 'react-bootstrap/Navbar';
import Nav from 'react-bootstrap/Nav';
import Container from 'react-bootstrap/Container';
import NavDropdown from 'react-bootstrap/NavDropdown';
import client from '../api/client';
import AccountSwitchNotice from './AccountSwitchNotice';
import { useAuth } from '../auth/AuthContext';
import Avatar from './Avatar';
import ChatDrawer from './ChatDrawer';
import ChatLauncher from './ChatLauncher';
import {
  IconBoard, IconFriends, IconGear, IconLogout, IconNote, IconPerson, IconShield,
  IconShieldAlert,
} from './Icons';
import LanguageSwitch from './LanguageSwitch';
import NotificationsBell from './NotificationsBell';
import ThemeToggle from './ThemeToggle';
import { scrollToTop } from '../utils/scroll';

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
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  /*
   * Ile zaproszen czeka na odpowiedz. Odswiezamy przy kazdej zmianie adresu -
   * to najprostszy moment, w ktorym liczba moze sie zdezaktualizowac
   * (np. po przyjeciu zaproszenia na stronie "Znajomi").
   */
  const [pending, setPending] = useState(0);

  /*
   * Ile zgloszen czeka na decyzje. Tylko dla administratora - zwykly
   * uzytkownik nie ma nawet po co pytac, a zapytanie i tak skonczyloby sie
   * odmowa 403 w konsoli przy kazdej zmianie strony.
   */
  const [openReports, setOpenReports] = useState(0);

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

    if (user.admin) {
      client.get('/reports/admin/open-count')
        .then((o) => setOpenReports(o.data.count))
        .catch(() => setOpenReports(0));
    }
  }, [user, location.pathname]);

  /*
   * Zmiana strony zaczyna sie OD GORY. Bez tego przejscie z polowy dlugiej
   * tablicy na ekran znajomych zostawialo widok w tym samym miejscu -
   * nowa strona pokazywala sie od srodka i wygladalo to jak usterka.
   * Bez animacji, bo tu nie ma czego animowac: to jest inna strona.
   */
  useEffect(() => {
    scrollToTop(false);
  }, [location.pathname]);

  /**
   * Klikniecie w logo albo w ikone tablicy.
   *
   * <p>Zawsze konczy sie na gorze tablicy - takze wtedy, gdy juz sie na niej
   * jest. To najczestszy sposob, w jaki ludzie wracaja "do poczatku" i bez
   * tego trzeba bylo przewijac dluga liste recznie.</p>
   *
   * <p>Przy okazji <b>odswiezamy wpisy</b>: kto wraca na gore, ten zwykle
   * chce zobaczyc, czy cos nowego doszlo. Sygnalem jest znacznik czasu
   * w stanie trasy - tablica nasluchuje jego zmiany.</p>
   */
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
      {/*
        Pasek gorny w ukladzie trzech kolumn: logo | ikony | konto.
        Srodkowa kolumna jest wysrodkowana WZGLEDEM STRONY, a nie wzgledem
        tego, co zostalo po bokach - dlatego siatka, a nie zwykly flex
        z "mx-auto". Przy roznej dlugosci nazwy uzytkownika ikony
        przesuwalyby sie inaczej u kazdego.

        Nie ma tu "hamburgera": po zamianie pozycji menu na ikony caly
        srodek miesci sie nawet na telefonie, a schowanie nawigacji za
        dodatkowym klknieciem tylko by ja oddalilo.
      */}
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
            {/*
              Znak marki ma NUTKE, a nie tę samą ikonę co pozycja "Tablica"
              w menu obok. Wcześniej były identyczne - logo przestawało być
              rozpoznawalne, a aplikacja o muzyce nie miała w nim niczego
              muzycznego.
            */}
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

                {/*
                  Panel administratora. Zwykly uzytkownik nie widzi nawet
                  ikony - prawdziwa blokada siedzi w backendzie (403),
                  to tylko porzadek w menu.
                */}
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
            {/* Dzwonek stoi przy awatarze, bo jedno i drugie dotyczy MNIE -
                w odroznieniu od jezyka i motywu, ktore dotycza calej strony */}
            {user && <ChatLauncher />}
            {user && <NotificationsBell />}

            <LanguageSwitch />
            <ThemeToggle />

            {user && (
              /*
                Wszystko, co dotyczy wlasnego konta, w jednym miejscu:
                klikniecie w awatar z nazwa rozwija profil, ustawienia
                i wylogowanie.
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

      {/*
        Panel czatu stoi POZA <main>, bo nie jest czescia tresci strony -
        wysuwa sie nad nia. Gdyby siedzial w srodku, przewijalby sie razem
        z tablica i znikal przy jej dolnej krawedzi.
      */}
      {user && <ChatDrawer />}

      <footer className="border-top py-3 text-center text-body-secondary small">
        {t('app.tagline')}
      </footer>
    </>
  );
}

/**
 * Jedna pozycja nawigacji: sama ikona z podpowiedzia i licznikiem.
 *
 * <p><b>Ikona bez podpisu wymaga etykiety dla czytnikow ekranu</b> -
 * {@code aria-label} jest tu obowiazkowy, a nie ozdobny. {@code title}
 * daje to samo myszce w postaci dymka.</p>
 *
 * <p>Aktywna pozycja dostaje gradient marki - ten sam, co logo i glowny
 * przycisk. Przy samych ikonach to jedyny sposob, zeby bylo widac,
 * gdzie sie jest.</p>
 */
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

      {/*
        Liczba oczekujacych zaproszen. Pokazujemy ja tylko, gdy jest
        wieksza od zera - stale "0" przy ikonie to szum.
      */}
      {badge > 0 && <span className="nav-badge">{badge}</span>}
    </Nav.Link>
  );
}
