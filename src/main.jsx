import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.jsx';
import './ui.css';

ReactDOM.createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);

// Service worker: pozwala otworzyc aplikacje bez internetu i zainstalowac ja na telefonie. Tylko w wersji zbudowanej.
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch(() => { /* bez service workera jest mniej wygodnie, ale dziala */ });
  });
}
