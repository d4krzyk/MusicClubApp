import React from 'react';
import ReactDOM from 'react-dom/client';

// Bootstrap MUSI byc zaimportowany przed naszym styles.css - inaczej nasze
// nieliczne poprawki zostalyby nadpisane przez style Bootstrapa.
import 'bootstrap/dist/css/bootstrap.min.css';

import App from './App';
import { ThemeProvider } from './theme/ThemeContext';
import './i18n'; // musi byc zaimportowane PRZED pierwszym uzyciem useTranslation
import './styles.css';

/*
 * Motyw (jasny/ciemny) ustawia maly skrypt w index.html - jeszcze zanim przegladarka cokolwiek
 * narysuje.
 */

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {/*
      ThemeProvider owija cala aplikacje - motyw dotyczy kazdego ekranu, takze logowania, ktore
      jest poza routingiem chronionym
    */}
    <ThemeProvider>
      <App />
    </ThemeProvider>
  </React.StrictMode>
);

/*
 * Service worker - to on sprawia, ze aplikacje da sie zainstalowac na telefonie.
 *
 * Rejestrujemy go WYLACZNIE w wersji zbudowanej. W trybie deweloperskim Vite
 * podaje pliki inaczej przy kazdej zmianie, a service worker trzymalby ich stare
 * kopie - poprawki przestawalyby byc widoczne bez czyszczenia pamieci przegladarki.
 *
 * Czekamy na "load", zeby pobieranie pliku nie konkurowalo o lacze z pierwszym
 * wyswietleniem strony.
 */
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      /* Brak service workera to mniej wygody, a nie blad - aplikacja dziala dalej */
    });
  });
}
