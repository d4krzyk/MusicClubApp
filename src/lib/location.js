import csv from './miastaDane.js';
import { cityKey } from './nameKeys.js';

// Lista miast Polski ze srodkiem miasta: nazwa;szerokosc;dlugosc;inne nazwy po przecinku.
export function parseCities(text) {
  const wynik = [];
  for (const linia of text.split(/\r?\n/)) {
    if (!linia.trim() || linia.startsWith('#')) continue;
    const [nazwa, lat, lon, inne = ''] = linia.split(';');
    wynik.push({ name: nazwa.trim(), lat: Number(lat), lon: Number(lon),
      keys: [nazwa, ...inne.split(',')].map((n) => cityKey(n)).filter(Boolean) });
  }
  return wynik;
}

export const CITIES = parseCities(csv);
const PO_KLUCZU = new Map(CITIES.flatMap((c) => c.keys.map((k) => [k, c])));

export const cityByName = (name) => PO_KLUCZU.get(cityKey(name)) ?? null;

// Odleglosc w linii prostej, km.
export function distanceKm(lat1, lon1, lat2, lon2) {
  const r = Math.PI / 180;
  const c = Math.cos(lat1 * r) * Math.cos(lat2 * r) * Math.cos((lon2 - lon1) * r) + Math.sin(lat1 * r) * Math.sin(lat2 * r);
  return 6371 * Math.acos(Math.max(-1, Math.min(1, c)));
}

// Poziom bliskosci 0-5: to samo miasto / <=30 / <=60 / <=120 / <=250 km / dalej albo nie wiadomo.
export function level(km) {
  if (km == null) return 0;
  if (km < 1) return 5;
  if (km <= 30) return 4;
  if (km <= 60) return 3;
  if (km <= 120) return 2;
  if (km <= 250) return 1;
  return 0;
}

// Odleglosc wydarzenia od miasta uzytkownika (origin = {lat, lon}): ze wspolrzednych sali albo ze srodka miasta.
export function eventDistance(origin, event) {
  if (!origin) return null;
  let lat = event.lat;
  let lon = event.lon;
  if (lat == null || lon == null) {
    const c = event.cityKey ? PO_KLUCZU.get(event.cityKey) : null;
    if (!c) return null;
    lat = c.lat;
    lon = c.lon;
  }
  return distanceKm(origin.lat, origin.lon, lat, lon);
}
