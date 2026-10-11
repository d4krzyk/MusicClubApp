import { cityKey, seriesKey } from './nameKeys.js';
import { joined, cut } from './plainText.js';
import { cityByName } from './location.js';
import { strip } from './boilerplate.js';

export const API = 'https://app.ticketmaster.com/discovery/v2';
const ROZMIAR_STRONY = 200;
const MAX_WYNIKOW = 1000; // Ticketmaster nie odda wiecej dla jednego zapytania ("size * page < 1000")

const RODZAJE_LINKOW = { homepage: 'WEBSITE', spotify: 'SPOTIFY', youtube: 'YOUTUBE', instagram: 'INSTAGRAM',
  facebook: 'FACEBOOK', tiktok: 'TIKTOK', bandcamp: 'BANDCAMP', soundcloud: 'SOUNDCLOUD', wiki: 'WIKIPEDIA',
  musicbrainz: 'MUSICBRAINZ', twitter: 'TWITTER', itunes: 'ITUNES' };

const tekst = (n, pole) => {
  const v = n?.[pole];
  return v == null || typeof v === 'object' ? null : String(v);
};
const czysc = (v, max) => {
  if (v == null) return null;
  const t = String(v).trim();
  return t ? t.slice(0, max).trim() : null;
};
const pierwszy = (a) => (Array.isArray(a) && a.length ? a[0] : undefined);

export function bezpiecznyLink(adres) {
  if (!adres || adres.length > 500 || /\s/.test(adres.trim())) return false;
  try {
    const u = new URL(adres.trim());
    return (u.protocol === 'https:' || u.protocol === 'http:') && !!u.hostname && !u.username && !u.password;
  } catch { return false; }
}

export function links(externalLinks) {
  const wynik = {};
  for (const [klucz, wartosc] of Object.entries(externalLinks ?? {})) {
    const rodzaj = RODZAJE_LINKOW[klucz.toLowerCase()];
    const adres = tekst(pierwszy(wartosc), 'url');
    if (rodzaj && !wynik[rodzaj] && bezpiecznyLink(adres)) wynik[rodzaj] = adres.trim();
  }
  return wynik;
}

// Zdjecie 16:9, najmniejsze nie mniejsze niz width; grafika zastepcza ("fallback") tylko, gdy nie ma wlasnego zdjecia.
export function image(images, width) {
  const lepsze = (a, b) => {
    if (!tekst(b, 'url')) return a;
    if (!a) return b;
    const wa = a.width ?? 0, wb = b.width ?? 0;
    const okA = wa >= width, okB = wb >= width;
    if (okA && okB) return wb < wa ? b : a;
    if (okA !== okB) return okB ? b : a;
    return wb > wa ? b : a;
  };
  for (const tylkoWlasne of [true, false]) {
    let best;
    for (const img of images ?? []) {
      if (tylkoWlasne && img.fallback) continue;
      if (img.ratio !== '16_9') continue;
      best = lepsze(best, img);
    }
    if (best) return tekst(best, 'url');
  }
  let best;
  for (const img of images ?? []) best = lepsze(best, img);
  return best ? tekst(best, 'url') : null;
}

const nazwaGatunku = (n) => {
  const nazwa = czysc(tekst(n, 'name'), 60);
  return !nazwa || ['undefined', 'other'].includes(nazwa.toLowerCase()) ? null : nazwa;
};

export function status(kod) {
  switch ((kod ?? '').toLowerCase()) {
    case 'cancelled': case 'canceled': return 'CANCELLED';
    case 'postponed': return 'POSTPONED';
    case 'rescheduled': return 'RESCHEDULED';
    default: return 'SCHEDULED';
  }
}

const cena = (v) => (typeof v === 'number' && v >= 0 && v < 1_000_000 ? v : null);

// Uwagi, organizator, ceny, wiek, sprzedaz i dostepnosc.
export function organizer(e) {
  const promoter = tekst(e.promoter, 'name') ?? tekst(pierwszy(e.promoters), 'name');
  let zakres = null;
  for (const p of e.priceRanges ?? []) if (!zakres || p.type === 'standard') zakres = p;
  const min = zakres ? cena(zakres.min) : null;
  let max = zakres ? cena(zakres.max) : null;
  if (min != null && max != null && max < min) max = null;
  let waluta = zakres ? tekst(zakres, 'currency') : null;
  if (waluta && !/^[A-Za-z]{3}$/.test(waluta)) waluta = null;
  let sprzedaz = null;
  const start = tekst(e.sales?.public, 'startDateTime');
  if (start && !e.sales?.public?.startTBD && !Number.isNaN(Date.parse(start))) sprzedaz = start;
  return {
    pleaseNote: cut(strip(tekst(e, 'pleaseNote')), 1000),
    promoter: czysc(promoter, 200),
    priceMin: min, priceMax: max,
    priceCurrency: (min == null && max == null) || !waluta ? null : waluta.toUpperCase(),
    ageRestricted: !!e.ageRestrictions?.legalAgeEnforced,
    salesStart: sprzedaz,
    accessibility: cut(strip(tekst(e.accessibility, 'info')), 500),
  };
}

// Jedno wydarzenie z JSON-a Ticketmastera w naszej postaci; null, gdy nie ma czego pokazac.
export function readEvent(e) {
  if (e.test) return null;
  const id = tekst(e, 'id');
  const name = czysc(tekst(e, 'name'), 300);
  const start = e.dates?.start ?? {};
  const date = /^\d{4}-\d{2}-\d{2}$/.test(tekst(start, 'localDate') ?? '') ? start.localDate : null;
  if (!id || !name || !date) return null;

  const bezGodziny = start.timeTBA || start.noSpecificTime;
  const godzina = !bezGodziny && /^\d{2}:\d{2}/.test(tekst(start, 'localTime') ?? '') ? start.localTime.slice(0, 5) : null;
  const venue = pierwszy(e._embedded?.venues) ?? {};
  const klasyfikacja = (e.classifications ?? []).find((c) => c.primary) ?? pierwszy(e.classifications) ?? {};

  const performers = [];
  for (const a of e._embedded?.attractions ?? []) {
    const nazwa = czysc(tekst(a, 'name'), 200);
    if (nazwa) performers.push({ id: czysc(tekst(a, 'id'), 64), name: nazwa, links: links(a.externalLinks) });
  }

  // Ticketmaster pisze po angielsku ("Warsaw") - pokazujemy polska nazwe, gdy miasto jest na liscie
  const miastoTm = czysc(tekst(venue.city, 'name'), 100);
  const miasto = cityByName(miastoTm)?.name ?? miastoTm;
  const sala = czysc(tekst(venue, 'name'), 200);
  const venueId = czysc(tekst(venue, 'id'), 64);
  const wsp = (pole) => {
    const v = Number(tekst(venue.location, pole));
    return tekst(venue.location, pole) != null && Number.isFinite(v) ? v : null;
  };
  const kraj = tekst(venue.country, 'countryCode');

  return {
    id: id.slice(0, 64),
    source: 'tm',
    name, date, time: godzina,
    startUtc: godzina && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(tekst(start, 'dateTime') ?? '') ? start.dateTime : null,
    timezone: czysc(tekst(e.dates, 'timezone'), 64),
    status: status(tekst(e.dates?.status, 'code')),
    description: cut(strip(joined(tekst(e, 'info'), tekst(e, 'description'), tekst(e, 'additionalInfo'))), 4000),
    ticketUrl: bezpiecznyLink(tekst(e, 'url')) ? e.url.trim() : null,
    imageUrl: image(e.images, 1024), thumbUrl: image(e.images, 640),
    venueName: sala, city: miasto, cityKey: cityKey(miasto),
    address: czysc(tekst(venue.address, 'line1'), 300),
    lat: wsp('latitude'), lon: wsp('longitude'),
    genre: nazwaGatunku(klasyfikacja.genre), subGenre: nazwaGatunku(klasyfikacja.subGenre),
    performers,
    country: kraj && kraj.length === 2 ? kraj.toUpperCase() : null,
    seriesKey: seriesKey(name, venueId, sala),
    organizer: organizer(e),
    sources: [{ kind: 'TICKETMASTER', name: 'Ticketmaster', url: bezpiecznyLink(tekst(e, 'url')) ? e.url.trim() : null }],
  };
}

export class ApiError extends Error {
  constructor(kod, wiadomosc) { super(wiadomosc); this.kod = kod; }
}

const bezKlucza = (t, klucz) => (klucz ? String(t).replaceAll(klucz, '***') : String(t));

// Jedna strona wydarzen muzycznych w kraju miedzy from a to (ISO bez ulamkow sekund).
export async function fetchPage({ key, country, from, to, page, fetchFn = fetch, signal }) {
  const q = new URLSearchParams({ countryCode: country, classificationName: 'music', startDateTime: from,
    endDateTime: to, sort: 'date,asc', size: String(ROZMIAR_STRONY), page: String(page), apikey: key });
  let odp;
  try {
    odp = await fetchFn(`${API}/events.json?${q}`, { signal });
  } catch (e) {
    if (e?.name === 'AbortError') throw e;
    throw new ApiError('SIEC', bezKlucza(`Ticketmaster nie odpowiada: ${e?.message ?? e}`, key));
  }
  let json = null;
  try { json = await odp.json(); } catch { /* tresc bez JSON-a */ }
  if (!odp.ok || json?.fault) {
    const powod = json?.fault?.faultstring ?? '';
    throw new ApiError(odp.status === 401 || odp.status === 403 || json?.fault ? 'KLUCZ' : odp.status === 429 ? 'LIMIT' : 'HTTP',
      bezKlucza(`Ticketmaster odmówił: HTTP ${odp.status} ${powod}`.trim(), key));
  }
  const events = (json?._embedded?.events ?? []).map(readEvent).filter(Boolean);
  const p = json?.page ?? {};
  return { events, number: p.number ?? page, totalPages: p.totalPages ?? 0, totalElements: p.totalElements ?? 0 };
}

const iso = (d) => `${d.toISOString().slice(0, 19)}Z`;

// Okna czasu po miesiacu: zapytanie nie oddaje wiecej niz 1000 wynikow.
export function windows(now, months) {
  const wynik = [];
  let od = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  for (let i = 0; i < months; i++) {
    const kolejny = new Date(Date.UTC(od.getUTCFullYear(), od.getUTCMonth() + 1, 1));
    wynik.push({ from: iso(od), to: iso(new Date(kolejny.getTime() - 1000)) });
    od = kolejny;
  }
  return wynik;
}

export const MAX_STRON = MAX_WYNIKOW / ROZMIAR_STRONY;

// Pobiera wydarzenia kraju w kolejnych oknach i stronach. onProgress({done,total}) po kazdym zapytaniu.
// Zwraca { events, complete }: complete=false, gdy cos nie doszlo (wtedy nic nie wolno usuwac z kopii).
export async function fetchCountry({ key, country, now = new Date(), months = 6, fetchFn = fetch, signal,
  pauseMs = 250, onProgress = () => {} }) {
  const okna = windows(now, months);
  const wszystkie = new Map();
  let zapytan = 0;
  const razem = okna.length;
  for (const okno of okna) {
    for (let strona = 0; strona < MAX_STRON; strona++) {
      if (strona > 0 || zapytan > 0) await new Promise((r) => setTimeout(r, pauseMs));
      const wynik = await fetchPage({ key, country, from: okno.from, to: okno.to, page: strona, fetchFn, signal });
      zapytan++;
      for (const e of wynik.events) wszystkie.set(e.id, e);
      onProgress({ done: zapytan, total: Math.max(razem, zapytan) });
      if (strona + 1 >= wynik.totalPages) break;
    }
  }
  return { events: [...wszystkie.values()] };
}
