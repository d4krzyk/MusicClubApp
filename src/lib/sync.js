import { fetchCountry } from './ticketmaster.js';
import * as lastfm from './lastfm.js';
import { nameKey } from './nameKeys.js';
import { dbGet, dbSet } from './store.js';

export const WAZNOSC_TAGOW_MS = 60 * 24 * 3600 * 1000; // gatunki wykonawcy: 60 dni
export const NA_PRZEBIEG = 150;                          // tylu nowych wykonawcow pytamy o gatunki w jednym odswiezeniu
export const MIN_ODSWIEZANIA_MS = 6 * 3600 * 1000;

const pauza = (ms) => new Promise((r) => setTimeout(r, ms));

export async function loadEvents() {
  return (await dbGet('events')) ?? { events: [], syncedAt: null, country: null };
}

// Pobiera wydarzenia kraju i podmienia kopie. Przy bledzie stara kopia zostaje (zadne wydarzenie nie znika przez awarie).
export async function syncEvents(settings, { fetchFn = fetch, signal, onProgress, now = new Date(), pauseMs } = {}) {
  if (!settings.tmKey.trim()) throw new Error('Brak klucza Ticketmastera');
  const { events } = await fetchCountry({ key: settings.tmKey.trim(), country: settings.country, now,
    months: settings.months, fetchFn, signal, onProgress, pauseMs });
  const wynik = { events, syncedAt: now.toISOString(), country: settings.country };
  await dbSet('events', wynik);
  return wynik;
}

export async function loadTags() {
  return (await dbGet('tags')) ?? {};
}

const swieze = (wpis, teraz) => wpis && teraz - Date.parse(wpis.at) < WAZNOSC_TAGOW_MS;

// Gatunki podanych nazw (kolejnosc = priorytet). Pyta tylko o te, ktorych nie ma albo sa przeterminowane.
// Awaria Last.fm nie zapisuje pustych tagow. Zwraca { tags: klucz->lista, asked, failed }.
export async function syncTags(names, { lfmKey, fetchFn = fetch, signal, limit = NA_PRZEBIEG, now = Date.now(),
  pauseMs = 250, onProgress = () => {} } = {}) {
  const wszystkie = await loadTags();
  const doZapytania = [];
  const widziane = new Set();
  for (const n of names) {
    const k = nameKey(n);
    if (!k || widziane.has(k)) continue;
    widziane.add(k);
    if (!swieze(wszystkie[k], now)) doZapytania.push([k, n]);
  }
  let zapytano = 0;
  let nieudane = 0;
  if (lfmKey?.trim()) {
    for (const [k, n] of doZapytania.slice(0, limit)) {
      if (zapytano > 0) await pauza(pauseMs);
      try {
        const lista = await lastfm.artistGenres(n, { key: lfmKey.trim(), fetchFn, signal });
        wszystkie[k] = { tags: lista, at: new Date(now).toISOString() };
      } catch (e) {
        if (e?.name === 'AbortError') throw e;
        nieudane++;
        if (e?.kod === 'KLUCZ') break; // zly klucz - nie ma sensu pytac dalej
      }
      zapytano++;
      onProgress({ done: zapytano, total: Math.min(limit, doZapytania.length) });
    }
    await dbSet('tags', wszystkie);
  }
  return { tags: Object.fromEntries(Object.entries(wszystkie).map(([k, v]) => [k, v.tags])), asked: zapytano, failed: nieudane };
}

// Ulubieni artysci z konta Last.fm (top, "overall") - zapisywani w ustawieniach przez wywolujacego.
export async function importLastFm(settings, { fetchFn = fetch, signal } = {}) {
  if (!settings.lfmKey.trim() || !settings.lfmUser.trim()) throw new Error('Brak klucza Last.fm albo nazwy uzytkownika');
  return lastfm.topArtists(settings.lfmUser.trim(), settings.lfmCount, { key: settings.lfmKey.trim(), fetchFn, signal });
}

// Wykonawcy nadchodzacych wydarzen w kolejnosci waznosci: najpierw najblizsze terminy.
export function performerNames(events, today) {
  const wynik = [];
  const posortowane = [...events].filter((e) => e.date >= today).sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  for (const e of posortowane) for (const p of e.performers) wynik.push(p.name);
  return wynik;
}
