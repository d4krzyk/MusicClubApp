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
 * Motyw (jasny/ciemny) ustawia maly skrypt w index.html - jeszcze zanim
 * przegladarka cokolwiek narysuje. Tutaj celowo go NIE ustawiamy, bo React
 * startuje za pozno i strona zdazylaby mignac w zlych kolorach.
 * Przelaczaniem zajmuje sie potem ThemeContext.
 */

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {/* ThemeProvider owija cala aplikacje - motyw dotyczy kazdego ekranu,
        takze logowania, ktore jest poza routingiem chronionym */}
    <ThemeProvider>
      <App />
    </ThemeProvider>
  </React.StrictMode>
);
