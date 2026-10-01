import { useCallback, useEffect, useState } from 'react';
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
import AkceptacjaRegulaminu from './AkceptacjaRegulaminu';
import ChatLauncher from './ChatLauncher';
import {
  IconBoard, IconCalendar, IconClan, IconFlag, IconFriends, IconGear, IconLogout, IconPerson,
  IconSearch, IconShield, IconShieldAlert,
} from './Icons';
import LanguageSwitch from './LanguageSwitch';
import LogoMC from './LogoMC';
import NapisMC from './NapisMC';
import NotificationsBell from './NotificationsBell';
import ThemeToggle from './ThemeToggle';
import { scrollToTop } from '../utils/scroll';
import { nieprzeczytane as klanNieprzeczytane } from '../api/klany';
import { ODSWIEZ_LICZNIK } from '../utils/klan';
import useOdswiezanie from '../hooks/useOdswiezanie';

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

  /* Ile nieprzeczytanych wiadomosci czeka na czacie mojego klanu. */
  const [klanNowe, setKlanNowe] = useState(0);

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

  /* Licznik klanu: przy zmianie strony, co pol minuty (gdy karta na wierzchu) i zaraz po przeczytaniu czatu */
  const odswiezKlan = useCallback(() => {
    if (!user) {
      setKlanNowe(0);
      return;
    }
    klanNieprzeczytane()
      .then((odpowiedz) => setKlanNowe(odpowiedz.unread))
      .catch(() => setKlanNowe(0));   // licznik to dodatek, nie psujemy menu
  }, [user]);

  useEffect(() => { odswiezKlan(); }, [odswiezKlan, location.pathname]);
  useOdswiezanie(odswiezKlan, 30_000, Boolean(user));
  useEffect(() => {
    window.addEventListener(ODSWIEZ_LICZNIK, odswiezKlan);
    return () => window.removeEventListener(ODSWIEZ_LICZNIK, odswiezKlan);
  }, [odswiezKlan]);

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
            {/*
              Nazwa siedzi w samym znaku: jego M i C sa pierwszymi literami slowa.
              Dlatego nie ma tu juz osobnego napisu obok sygnetu - te dwie litery
              staly wczesniej dwa razy.

              Trzy wersje czekaja obok siebie, a o tym, ktora widac, decyduje w CSS
              miejsce zostawione przez ikony: caly napis w jednej linii, napis
              dwuliniowy, a na telefonie sam znak - tam na cokolwiek szerszego
              po prostu nie ma miejsca obok ikon administratora.

              Nazwa dla czytnikow ekranu zostaje na samym odnosniku, wiec rysunki
              sa przed nimi ukryte.
            */}
            <NapisMC uklad="poziomy" className="brand-napis brand-napis-szeroki" aria-hidden="true" />
            <NapisMC uklad="pionowy" className="brand-napis brand-napis-waski" aria-hidden="true" />
            <span className="brand-napis brand-znak-sam" aria-hidden="true">
              <LogoMC size={36} />
            </span>
          </Navbar.Brand>

          {/*
            Srodkowa kolumna nalezy do ikon nawigacji, a te ma tylko zalogowany.
            Gdy ich nie ma, zostaje tu pusty element - siatka ma trzy kolumny
            i musi je dostac, inaczej prawa kolumna przesuwa sie na srodek.
          */}
          {user ? (
            <Nav className="nav-icons">
              <NavIcon to="/" end label={t('menu.feed')} onClick={goToFeed}>
                <IconBoard size={20} />
              </NavIcon>

              <NavIcon to="/znajomi" label={t('menu.friends')} badge={pending}>
                <IconFriends size={20} />
              </NavIcon>

              <NavIcon to="/wydarzenia" label={t('menu.events')}>
                <IconCalendar size={20} />
              </NavIcon>

              {/*
                Panel administratora. Na telefonie te dwie ikony przechodza do
                menu konta (nizej) - z nimi pasek administratora mial piec ikon
                i przy 360 px wychodzil poza ekran o 41 px (zmierzone).
              */}
              {user.admin && (
                <NavIcon to="/users" label={t('menu.users')} className="nav-icon-szeroki">
                  <IconShield size={20} />
                </NavIcon>
              )}

              {user.admin && (
                <NavIcon
                  to="/zgloszenia"
                  label={t('menu.reports')}
                  badge={openReports}
                  className="nav-icon-szeroki"
                >
                  <IconShieldAlert size={20} />
                </NavIcon>
              )}
            </Nav>
          ) : (
            <span aria-hidden="true" />
          )}

          <div className="nav-right">
            {/*
              Bez konta pasek ma tylko logo i te dwa odnosniki. Stoja po prawej,
              czyli tam, gdzie u zalogowanego jest menu konta - inaczej wisialy
              w srodkowej kolumnie i wygladaly na przekrzywione, bo po ich
              prawej stronie nie bylo juz nic, co by je wyrownalo.
            */}
            {!user && (
              <Nav className="nav-goscie">
                <Nav.Link as={NavLink} to="/login" className="nav-text-link">
                  {t('menu.login')}
                </Nav.Link>
                <Nav.Link as={NavLink} to="/register" className="nav-text-link">
                  {t('menu.register')}
                </Nav.Link>
              </Nav>
            )}

            {/*
              Dzwonek stoi przy awatarze, bo jedno i drugie dotyczy MNIE - w odroznieniu od jezyka
              i motywu, ktore dotycza calej strony
            */}
            {user && <ChatLauncher />}
            {user && <NotificationsBell />}

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

                    {/* Na telefonie licznik zgloszen nie ma juz swojej ikony - siada na awatarze */}
                    {user.admin && openReports > 0 && (
                      <span className="nav-badge konto-badge d-sm-none">{openReports}</span>
                    )}

                    {/* Nieprzeczytane w klanie - sama kropka, liczba jest przy pozycji "Mój klan" */}
                    {klanNowe > 0 && <span className="konto-kropka" aria-hidden="true" />}
                  </span>
                }
                id="account-menu"
              >
                <NavDropdown.Item as={Link} to="/profil">
                  <IconPerson className="me-2" />
                  {t('menu.myProfile')}
                </NavDropdown.Item>

                <NavDropdown.Item as={Link} to="/klan">
                  <IconClan className="me-2" />
                  {t('menu.clan')}
                  {klanNowe > 0 && (
                    <span className="klan-zakladka-licznik" aria-label={t('clans.unread', { count: klanNowe })}>
                      {klanNowe > 99 ? '99+' : klanNowe}
                    </span>
                  )}
                </NavDropdown.Item>

                <NavDropdown.Item as={Link} to="/klany">
                  <IconSearch className="me-2" />
                  {t('menu.clans')}
                </NavDropdown.Item>

                <NavDropdown.Item as={Link} to="/moje-zgloszenia">
                  <IconFlag className="me-2" />
                  {t('menu.myReports')}
                </NavDropdown.Item>

                <NavDropdown.Item as={Link} to="/settings">
                  <IconGear className="me-2" />
                  {t('menu.settings')}
                </NavDropdown.Item>

                {/* Panel administratora - tu tylko na telefonie, na wiekszym ekranie jest w pasku */}
                {user.admin && (
                  <>
                    <NavDropdown.Divider className="d-sm-none" />
                    <NavDropdown.Item as={Link} to="/users" className="d-sm-none">
                      <IconShield className="me-2" />
                      {t('menu.users')}
                    </NavDropdown.Item>
                    <NavDropdown.Item as={Link} to="/zgloszenia" className="d-sm-none">
                      <IconShieldAlert className="me-2" />
                      {t('menu.reports')}
                      {openReports > 0 && (
                        <span className="badge rounded-pill text-bg-danger ms-2">{openReports}</span>
                      )}
                    </NavDropdown.Item>
                  </>
                )}

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
          <AkceptacjaRegulaminu />
          {children}
        </Container>
      </main>

      {/* Panel czatu stoi POZA <main>, bo nie jest czescia tresci strony - wysuwa sie nad nia. */}
      {user && <ChatDrawer />}

      <footer className="border-top py-3 text-center text-body-secondary small">
        <div>{t('app.tagline')}</div>
        <div className="mt-1">
          <Link to="/regulamin" className="text-body-secondary">{t('legal.terms')}</Link>
          {' · '}
          <Link to="/polityka-prywatnosci" className="text-body-secondary">{t('legal.privacy')}</Link>
        </div>

        {/*
          Zalogowany zmienia jezyk i motyw w Ustawieniach. Kto nie ma konta, tam nie dojdzie,
          a to wlasnie on najczesciej trafia tu na angielski interfejs i szuka polskiego.
        */}
        {!user && (
          <div className="d-flex justify-content-center gap-2 mt-2">
            <LanguageSwitch />
            <ThemeToggle />
          </div>
        )}
      </footer>
    </>
  );
}

/** Jedna pozycja nawigacji: sama ikona z podpowiedzia i licznikiem. */
function NavIcon({ to, end, label, badge = 0, onClick, className = '', children }) {
  return (
    <Nav.Link
      as={NavLink}
      to={to}
      end={end}
      className={`nav-icon ${className}`.trim()}
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
