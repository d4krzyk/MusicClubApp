import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Konfiguracja narzedzia Vite (serwer deweloperski + budowanie wersji produkcyjnej).

/*
 * PRZEKIEROWANIA - najwazniejsza rzecz w tym pliku.
 *
 * Frontend chodzi na porcie 5173, backend na 8080. Gdyby React wolal
 * bezposrednio "http://localhost:8080/api/...", przegladarka traktowalaby to
 * jako zapytanie do obcego serwera i trzeba by walczyc z CORS-em oraz
 * ciasteczkami miedzy portami. Dzieki przekierowaniu React wola po prostu
 * "/api/..." - czyli swoj wlasny adres - a Vite po cichu przekazuje zapytanie
 * do backendu. Dla przegladarki wszystko dzieje sie pod jednym adresem, wiec
 * ciasteczko sesji i token CSRF dzialaja bez zadnych sztuczek.
 *
 * Wgrane zdjecia (/uploads) tez leza po stronie backendu i tez musza tu byc.
 * Bez tego wpisu Vite - jak kazdy serwer aplikacji jednostronicowej - oddaje
 * na taki adres index.html ze statusem 200, wiec przegladarka dostaje HTML
 * zamiast obrazka i w tablicy widac puste ramki. Blad jest podstepny, bo
 * status odpowiedzi wyglada poprawnie.
 *
 * Ten sam zestaw obowiazuje "npm run preview", ktory podaje zbudowana wersje.
 * Tylko w niej rejestruje sie service worker (patrz src/main.jsx), wiec bez
 * przekierowan nie dalo by sie sprawdzic gotowej aplikacji przed wdrozeniem.
 */
const przekierowania = {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
  '/uploads': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
};

export default defineConfig({
  plugins: [react()],

  server: {
    port: 5173,
    proxy: przekierowania,
  },

  preview: {
    port: 4173,
    proxy: przekierowania,
  },
});
