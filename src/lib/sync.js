import { fetchCountry } from './ticketmaster.js';
import * as lastfm from './lastfm.js';
import { nameKey, cityKey } from './nameKeys.js';
import { dbGet, dbSet } from './store.js';
import { artistEvents } from './bandsintown.js';
import { parseIcs, normalizeFeedUrl, calendarName, cityFromName } from './ics.js';

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

/* ---------- Bandsintown: koncerty ulubionych wykonawcow (wlasny app_id) ---------- */

export async function loadBandsintown() {
  return (await dbGet('bit')) ?? { events: [], syncedAt: null };
}

// Pyta o kazdego ulubionego wykonawce po kolei. Blad pojedynczego wykonawcy nie przerywa; zly klucz przerywa.
// Przy awarii (nic sie nie udalo) zostaje stara kopia.
export async function syncBandsintown(artists, { appId, country, fetchFn = fetch, signal, pauseMs = 150,
  onProgress = () => {}, now = new Date() } = {}) {
  if (!appId?.trim()) return loadBandsintown();
  const wynik = new Map();
  let udane = 0;
  let i = 0;
  for (const a of artists) {
    if (i > 0) await pauza(pauseMs);
    i++;
    try {
      for (const e of await artistEvents(a, { appId: appId.trim(), fetchFn, signal })) {
        if (!country || !e.country || e.country === country) wynik.set(e.id, e);
      }
      udane++;
    } catch (e) {
      if (e?.name === 'AbortError' || e?.kod === 'KLUCZ') throw e;
    }
    onProgress({ done: i, total: artists.length });
  }
  if (artists.length && !udane) throw new Error('Bandsintown nie odpowiada');
  const dane = { events: [...wynik.values()], syncedAt: now.toISOString() };
  await dbSet('bit', dane);
  return dane;
}

/* ---------- Twoje kalendarze (ICS): pliki i subskrypcje ---------- */

export async function loadCalendars() {
  return ((await dbGet('calendars')) ?? []).map(poprawNazwe);
}

// Kalendarze zaimportowane przed rozpoznawaniem Facebooka nazywaly sie jak plik ("u100004746730811").
function poprawNazwe(k0) {
  // Miasto z nazwy wydarzenia ("... - Warszawa") dla wydarzen zaimportowanych, zanim aplikacja to umiala.
  const k = { ...k0, events: k0.events.map((e) => {
    if (e.cityKey) return e;
    const miasto = cityFromName(e.name);
    return miasto ? { ...e, city: miasto, cityKey: cityKey(miasto) } : e;
  }) };
  const zFacebooka = /^u\d{6,}$/.test(k.name) && k.events.some((e) => /facebook\.com/.test(e.sourceUrl ?? '') || /@facebook\.com/.test(e.id));
  if (!zFacebooka) return k;
  return { ...k, name: 'Facebook', events: k.events.map((e) => ({ ...e, sources: e.sources.map((z) => (z.kind === 'ICS' ? { ...z, name: 'Facebook' } : z)) })) };
}

async function zapiszKalendarze(lista) {
  await dbSet('calendars', lista);
  return lista;
}

const nowyId = () => `k${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;

export async function addCalendarFile(tekst, plik, { now = new Date() } = {}) {
  const id = nowyId();
  const nazwa = calendarName(tekst, plik);
  const events = parseIcs(tekst, { sourceId: id, sourceName: nazwa });
  if (!events.length && !/BEGIN:VCALENDAR/i.test(tekst)) throw new Error('To nie jest plik kalendarza (.ics)');
  const lista = await loadCalendars();
  return zapiszKalendarze([...lista, { id, name: nazwa, kind: 'file', url: null, events, syncedAt: now.toISOString(), error: null }]);
}

async function pobierzIcs(url, fetchFn, signal) {
  let odp;
  try { odp = await fetchFn(url, { signal }); } catch (e) {
    if (e?.name === 'AbortError') throw e;
    throw new Error(`Nie udało się pobrać kalendarza: ${e?.message ?? e}`);
  }
  if (!odp.ok) throw new Error(`Kalendarz: HTTP ${odp.status}`);
  const tekst = await odp.text();
  if (!/BEGIN:VCALENDAR/i.test(tekst)) throw new Error('Pod tym adresem nie ma kalendarza (.ics)');
  return tekst;
}

export async function addCalendarUrl(adres, nazwa, { fetchFn = fetch, signal, now = new Date() } = {}) {
  const url = normalizeFeedUrl(adres);
  if (!url) throw new Error('Nieprawidłowy adres kalendarza');
  const id = nowyId();
  const tekst = await pobierzIcs(url, fetchFn, signal);
  const lista = await loadCalendars();
  const n = nazwa || calendarName(tekst, new URL(url).hostname);
  return zapiszKalendarze([...lista, { id, name: n, kind: 'url', url,
    events: parseIcs(tekst, { sourceId: id, sourceName: n }), syncedAt: now.toISOString(), error: null }]);
}

// Odswieza subskrypcje; nieudana zostawia poprzednie wydarzenia i zapisuje blad.
export async function refreshCalendars({ fetchFn = fetch, signal, now = new Date() } = {}) {
  const lista = await loadCalendars();
  const nowa = [];
  for (const k of lista) {
    if (k.kind !== 'url') { nowa.push(k); continue; }
    try {
      const tekst = await pobierzIcs(k.url, fetchFn, signal);
      nowa.push({ ...k, events: parseIcs(tekst, { sourceId: k.id, sourceName: k.name }), syncedAt: now.toISOString(), error: null });
    } catch (e) {
      if (e?.name === 'AbortError') throw e;
      nowa.push({ ...k, error: e?.message ?? String(e) });
    }
  }
  return zapiszKalendarze(nowa);
}

export async function removeCalendar(id) {
  return zapiszKalendarze((await loadCalendars()).filter((k) => k.id !== id));
}
