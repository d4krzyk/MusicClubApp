import { useEffect, useMemo, useState } from 'react';
import { useApp } from './useApp.js';
import { I18nCtx, translator, detectLang } from './i18n.js';
import Logo from './Logo.jsx';
import { go } from './nav.js';
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

export default function App() {
  const app = useApp();
  const route = useRoute();
  const [filtry, setFiltry] = useState(FILTRY);
  const lang = detectLang(app.settings.lang);
  const t = useMemo(() => translator(lang), [lang]);
  useEffect(() => { document.documentElement.lang = lang; }, [lang]);

  if (!app.ready) return <div className="mc-splash"><Logo size={72} /></div>;

  const potrzebujeKlucza = !app.settings.tmKey.trim() && !app.data.events.length;
  const aktywna = route.startsWith('/gust') ? 'gust' : route.startsWith('/ustawienia') ? 'ust' : 'koncerty';

  let ekran;
  if (potrzebujeKlucza && !route.startsWith('/ustawienia')) ekran = <Welcome app={app} onDone={() => go('/')} />;
  else if (route.startsWith('/e/')) ekran = <EventScreen app={app} id={decodeURIComponent(route.slice(3))} />;
  else if (route.startsWith('/gust')) ekran = <TasteScreen app={app} />;
  else if (route.startsWith('/ustawienia')) ekran = <SettingsScreen app={app} />;
  else ekran = <EventsScreen app={app} filtry={filtry} setFiltry={setFiltry} />;

  return (
    <I18nCtx.Provider value={{ lang, t }}>
      <div className="mc-app">
        <header className="mc-top">
          <a className="mc-brand" href="#/" aria-label="MusicClub"><Logo size={30} /><span>MusicClub</span></a>
          <nav className="mc-nav" aria-label="Menu">
            <a href="#/" aria-current={aktywna === 'koncerty' ? 'page' : undefined}>{t('nav.events')}</a>
            <a href="#/gust" aria-current={aktywna === 'gust' ? 'page' : undefined}>{t('nav.taste')}</a>
            <a href="#/ustawienia" aria-current={aktywna === 'ust' ? 'page' : undefined}>{t('nav.settings')}</a>
          </nav>
        </header>
        <main className="mc-main">{ekran}</main>
      </div>
    </I18nCtx.Provider>
  );
}
