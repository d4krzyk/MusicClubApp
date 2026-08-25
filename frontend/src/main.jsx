import React from 'react';
import ReactDOM from 'react-dom/client';

// Bootstrap MUSI byc zaimportowany przed naszym styles.css - inaczej nasze
// nieliczne poprawki zostalyby nadpisane przez style Bootstrapa.
import 'bootstrap/dist/css/bootstrap.min.css';

import App from './App';
import './i18n'; // musi byc zaimportowane PRZED pierwszym uzyciem useTranslation
import './styles.css';

/*
 * Ciemny motyw Bootstrapa 5.3 - wystarczy jeden atrybut na <html>.
 * Wczesniej mielismy na to wlasne zmienne CSS; teraz robi to framework.
 */
document.documentElement.setAttribute('data-bs-theme', 'dark');

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
