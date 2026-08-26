import React from 'react';
import ReactDOM from 'react-dom/client';

// Bootstrap MUSI byc zaimportowany przed naszym styles.css - inaczej nasze
// nieliczne poprawki zostalyby nadpisane przez style Bootstrapa.
import 'bootstrap/dist/css/bootstrap.min.css';

import App from './App';
import { MotywProvider } from './theme/MotywContext';
import './i18n'; // musi byc zaimportowane PRZED pierwszym uzyciem useTranslation
import './styles.css';

/*
 * Motyw (jasny/ciemny) ustawia maly skrypt w index.html - jeszcze zanim
 * przegladarka cokolwiek narysuje. Tutaj celowo go NIE ustawiamy, bo React
 * startuje za pozno i strona zdazylaby mignac w zlych kolorach.
 * Przelaczaniem zajmuje sie potem MotywContext.
 */

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {/* MotywProvider owija cala aplikacje - motyw dotyczy kazdego ekranu,
        takze logowania, ktore jest poza routingiem chronionym */}
    <MotywProvider>
      <App />
    </MotywProvider>
  </React.StrictMode>
);
