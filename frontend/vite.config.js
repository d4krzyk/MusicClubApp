import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Konfiguracja narzedzia Vite (serwer deweloperski + budowanie wersji produkcyjnej).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    /*
     * PROXY - najwazniejsza rzecz w tym pliku.
     *
     * Frontend chodzi na porcie 5173, backend na 8080. Gdyby React wolal
     * bezposrednio "http://localhost:8080/api/...", przegladarka traktowalaby
     * to jako zapytanie do obcego serwera i trzeba by walczyc z CORS-em
     * oraz ciasteczkami miedzy portami.
     *
     * Dzieki proxy React wola po prostu "/api/..." - czyli swoj wlasny adres -
     * a Vite po cichu przekazuje to zapytanie do backendu. Dla przegladarki
     * wszystko dzieje sie pod jednym adresem, wiec ciasteczko sesji
     * i token CSRF dzialaja bez zadnych sztuczek.
     */
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },

      /*
       * Wgrane zdjecia (avatary, obrazki w postach) tez leza po stronie
       * backendu i tez musza tu byc wymienione.
       *
       * Bez tego wpisu Vite nie wie, co zrobic z adresem /uploads/... i - jak
       * kazdy serwer aplikacji jednostronicowej - oddaje index.html ze
       * statusem 200. Przegladarka dostaje wiec HTML zamiast obrazka
       * i w tablicy widac same puste ramki. Blad jest o tyle podstepny,
       * ze status odpowiedzi wyglada poprawnie.
       */
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
