import { cityKey } from './nameKeys.js';
import { cityByName } from './location.js';
import { wallToUtc } from './ics.js';

// Twoje poprawki wydarzenia z kalendarza (Facebook): godzina, miejsce, opis... Zapisane osobno (settings.edits[id]) i
// nakladane na wydarzenie przy kazdym wczytaniu - odswiezenie kalendarza ich nie kasuje.

// lat/lon: wspolrzedne z podpowiedzi adresu (odleglosc liczy sie wtedy od sali, nie od srodka miasta)
export const POLA_EDYCJI = ['name', 'date', 'time', 'venueName', 'address', 'city', 'lat', 'lon', 'description', 'ticketUrl'];

const pusty = (v) => v == null || String(v).trim() === '';

// Tylko pola, ktore roznia sie od oryginalu (puste pole = "nie wiem", tez jest poprawka).
export function diff(oryginal, formularz) {
  const wynik = {};
  for (const p of POLA_EDYCJI) {
    const nowe = pusty(formularz[p]) ? null : String(formularz[p]).trim();
    const stare = pusty(oryginal[p]) ? null : String(oryginal[p]).trim();
    if (nowe !== stare) wynik[p] = nowe;
  }
  return wynik;
}

// Wydarzenie z naniesionymi poprawkami. Zmiana daty albo godziny przelicza chwile startu (w strefie wydarzenia albo
// polskiej) i przesuwa koniec o ten sam czas trwania.
export function applyEdit(e, edycja) {
  if (!edycja || !Object.keys(edycja).length) return e;
  const w = { ...e, ...edycja, edited: true };
  if (!w.name) w.name = e.name; // nazwy nie da sie usunac
  if (!/^\d{4}-\d{2}-\d{2}$/.test(w.date ?? '')) w.date = e.date;
  if (w.time != null && !/^\d{2}:\d{2}$/.test(w.time)) w.time = e.time;
  if ('city' in edycja) {
    const c = cityByName(w.city);
    w.city = c?.name ?? w.city;
    w.cityKey = cityKey(w.city);
  }
  if ('date' in edycja || 'time' in edycja) {
    const trwa = e.startUtc && e.endUtc ? Date.parse(e.endUtc) - Date.parse(e.startUtc) : null;
    if (w.time) {
      const [y, m, d] = w.date.split('-').map(Number);
      const [h, mi] = w.time.split(':').map(Number);
      const ms = wallToUtc(y, m, d, h, mi, 0, e.timezone ?? 'Europe/Warsaw');
      w.startUtc = new Date(ms).toISOString();
      w.endUtc = trwa ? new Date(ms + trwa).toISOString() : null;
    } else {
      w.startUtc = null;
      w.endUtc = null;
    }
  }
  for (const p of ['lat', 'lon']) {
    if (!(p in edycja)) continue;
    const n = edycja[p] == null ? NaN : Number(edycja[p]);
    w[p] = Number.isFinite(n) && Math.abs(n) <= (p === 'lat' ? 90 : 180) ? n : null;
  }
  if (w.lat == null || w.lon == null) { w.lat = null; w.lon = null; } // jedna wspolrzedna bez drugiej nic nie znaczy
  if ('ticketUrl' in edycja && w.ticketUrl && !/^https?:\/\/[^\s]+$/i.test(w.ticketUrl)) w.ticketUrl = e.ticketUrl ?? null;
  return w;
}
