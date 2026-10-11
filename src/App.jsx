import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useApp } from './useApp.js';
import { I18nCtx, translator, detectLang } from './i18n.js';
import NapisMC from './NapisMC.jsx';
import Logo from './Logo.jsx';
import { go } from './nav.js';
import useWskaznik from './ui/useWskaznik.js';
import { ToastProvider } from './ui/Toast.jsx';
import { IkKoncerty, IkGust, IkUstawienia } from './ui/Ikony.jsx';
import Welcome from './screens/Welcome.jsx';
import EventsScreen from './screens/EventsScreen.jsx';
import EventScreen from './screens/EventScreen.jsx';
import TasteScreen from './screens/TasteScreen.jsx';
import SettingsScreen from './screens/SettingsScreen.jsx';

// Adres w #hash, bo aplikacja dziala bez serwera (otwarta z pliku, z GitHub Pages i z Androida tak samo).
function useRoute() {
  const czytaj = () => (window.location.hash.replace(/^#/, '') || '/');
  const [route, setRoute] = useState(czytaj);
  useEffect(() => {
    const f = () => setRoute(czytaj());
    window.addEventListener('hashchange', f);
    return () => window.removeEventListener('hashchange', f);
  }, []);
  return route;
}

const FILTRY = { widok: 'FOR_YOU', fraza: '', miasto: '', promien: null };

function Nawigacja({ aktywna, t }) {
  const ref = useRef(null);
  useWskaznik(ref, aktywna);
  const pozycje = [['koncerty', '#/', IkKoncerty, 'nav.events'], ['gust', '#/gust', IkGust, 'nav.taste'],
    ['ust', '#/ustawienia', IkUstawienia, 'nav.settings']];
  return (
    <nav className="mc-nav" aria-label="Menu">
      <div ref={ref} className="mc-nav-lista" role="tablist">
        {pozycje.map(([id, href, Ik, klucz]) => (
          <a key={id} href={href} role="tab" aria-selected={aktywna === id} aria-current={aktywna === id ? 'page' : undefined}
            className="mc-nav-link">
            <Ik size={22} /><span>{t(klucz)}</span>
          </a>
        ))}
      </div>
    </nav>
  );
}

export default function App() {
  const app = useApp();
  const route = useRoute();
  const [filtry, setFiltry] = useState(FILTRY);
  const lang = detectLang(app.settings.lang);
  const t = useMemo(() => translator(lang), [lang]);
  useEffect(() => { document.documentElement.lang = lang; }, [lang]);

  // Powrot z wydarzenia na liste wraca w to samo miejsce listy; kazdy inny ekran zaczyna od gory.
  const przewiniecie = useRef(0);
  const poprzedni = useRef(route);
  const naLiscie = (r) => r === '/' || r === '';
  useLayoutEffect(() => {
    if (naLiscie(poprzedni.current) && !naLiscie(route)) przewiniecie.current = window.scrollY;
    window.scrollTo(0, naLiscie(route) && poprzedni.current.startsWith('/e/') ? przewiniecie.current : 0);
    poprzedni.current = route;
  }, [route]);

  if (!app.ready) return <div className="mc-splash"><Logo size={72} /></div>;

  const potrzebujeKlucza = !app.settings.tmKey.trim() && !app.events.length && !app.calendars.length;
  const aktywna = route.startsWith('/gust') ? 'gust' : route.startsWith('/ustawienia') ? 'ust' : 'koncerty';
  const powitanie = potrzebujeKlucza && !route.startsWith('/ustawienia');

  let ekran;
  let klucz = route;
  if (powitanie) { ekran = <Welcome app={app} onDone={() => go('/')} />; klucz = 'powitanie'; }
  else if (route.startsWith('/e/')) ekran = <EventScreen app={app} id={decodeURIComponent(route.slice(3))} />;
  else if (route.startsWith('/gust')) ekran = <TasteScreen app={app} />;
  else if (route.startsWith('/ustawienia')) ekran = <SettingsScreen app={app} />;
  else { ekran = <EventsScreen app={app} filtry={filtry} setFiltry={setFiltry} />; klucz = '/'; }

  return (
    <I18nCtx.Provider value={{ lang, t }}>
      <ToastProvider>
        <div className={`mc-app${powitanie ? ' mc-app-powitanie' : ''}`}>
          {!powitanie && (
            <header className="mc-top">
              <a className="mc-brand" href="#/" aria-label="MusicClub">
                <NapisMC uklad="pionowy" className="mc-napis" aria-hidden="true" />
              </a>
              <Nawigacja aktywna={aktywna} t={t} />
            </header>
          )}
          <main className="mc-main">
            <div key={klucz} className={`mc-ekran${route.startsWith('/e/') ? ' mc-ekran-wglab' : ''}`}>{ekran}</div>
          </main>
        </div>
      </ToastProvider>
    </I18nCtx.Provider>
  );
}
