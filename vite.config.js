import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Aplikacja nie ma serwera: wszystko (w tym zapytania do Ticketmastera i Last.fm) idzie z urzadzenia.
// base './' - adresy wzgledne, zeby ta sama paczka dzialala z dowolnego miejsca (strona, GitHub Pages, Capacitor).
export default defineConfig({
  base: './',
  plugins: [react()],
  server: { port: 5174 },
  preview: { port: 4174 },
  build: { target: 'es2020' },
});
