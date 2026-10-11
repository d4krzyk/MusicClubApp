import { Capacitor } from '@capacitor/core';
import { App as AppNatywna } from '@capacitor/app';

// Przycisk "wstecz" Androida. Bez obslugi Capacitor zamyka aplikacje od razu - takze z ekranu wydarzenia.
// Kolejnosc: najpierw zamyka to, co otwarte na wierzchu (arkusz), potem cofa ekran (#/e/... -> lista), a dopiero
// na liscie koncertow wychodzi z aplikacji.

const stos = [];

// Otwarty arkusz rejestruje swoje "zamknij"; zwraca funkcje do wyrejestrowania.
export function naWstecz(zamknij) {
  stos.push(zamknij);
  return () => {
    const i = stos.lastIndexOf(zamknij);
    if (i >= 0) stos.splice(i, 1);
  };
}

export function wstecz({ wyjdz }) {
  if (stos.length) {
    stos[stos.length - 1]();
    return 'zamkniete';
  }
  const h = window.location.hash.replace(/^#/, '') || '/';
  if (h !== '/') {
    if (window.history.length > 1) window.history.back();
    else window.location.hash = '/';
    return 'cofniete';
  }
  wyjdz();
  return 'wyjscie';
}

export function podepnijWstecz() {
  // do sprawdzania w przegladarce (Playwright) - ta sama funkcja, co wola Android
  window.__musicclubWstecz = (wyjdz = () => {}) => wstecz({ wyjdz });
  if (!Capacitor.isNativePlatform()) return;
  AppNatywna.addListener('backButton', () => wstecz({ wyjdz: () => AppNatywna.exitApp() }));
}
