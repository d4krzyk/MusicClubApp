import { cityKey, seriesKey } from './nameKeys.js';
import { bezpiecznyLink } from './ticketmaster.js';
import { cut, plain } from './plainText.js';

export const API = 'https://rest.bandsintown.com';

export class BitError extends Error {
  constructor(kod, wiadomosc) { super(wiadomosc); this.kod = kod; }
}

// Kraje po angielsku (Bandsintown podaje "Poland") -> kod ISO.
let KRAJE = null;
export function countryCode(nazwa) {
  if (!nazwa) return null;
  if (!KRAJE) {
    KRAJE = new Map();
    const dn = new Intl.DisplayNames(['en'], { type: 'region' });
    for (let a = 65; a <= 90; a++) for (let b = 65; b <= 90; b++) {
      const kod = String.fromCharCode(a, b);
      try { const n = dn.of(kod); if (n && n !== kod) KRAJE.set(n.toLowerCase(), kod); } catch { /* nieznany kod */ }
    }
    for (const [n, k] of [['united states of america', 'US'], ['usa', 'US'], ['uk', 'GB'], ['england', 'GB'],
      ['scotland', 'GB'], ['czech republic', 'CZ'], ['the netherlands', 'NL']]) KRAJE.set(n, k);
  }
  const t = String(nazwa).trim();
  if (/^[A-Za-z]{2}$/.test(t)) return t.toUpperCase();
  return KRAJE.get(t.toLowerCase()) ?? null;
}

// Nazwa w sciezce: Bandsintown chce "/", "?" i "*" zakodowanych podwojnie.
export function pathName(artist) {
  return encodeURIComponent(artist.trim()).replace(/%2F/g, '%252F').replace(/%3F/g, '%253F').replace(/\*/g, '%252A')
    .replace(/%22/g, '%27C');
}

const tekst = (n, p) => (n?.[p] == null || typeof n[p] === 'object' ? null : String(n[p]));
const czysc = (v, max) => { const t = v == null ? '' : String(v).trim(); return t ? t.slice(0, max).trim() : null; };

export function readEvent(e, artist) {
  const id = tekst(e, 'id');
  const kiedy = tekst(e, 'datetime') ?? tekst(e, 'starts_at');
  const m = kiedy?.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/);
  if (!id || !m) return null;
  const venue = e.venue ?? {};
  const sklad = (Array.isArray(e.lineup) ? e.lineup : []).map((n) => czysc(n, 200)).filter(Boolean).slice(0, 10);
  if (!sklad.length) sklad.push(artist.trim());
  const tytul = czysc(tekst(e, 'title'), 300);
  const bilety = (Array.isArray(e.offers) ? e.offers : []).map((o) => tekst(o, 'url')).find((u) => u && bezpiecznyLink(u)) ?? null;
  const strona = tekst(e, 'url');
  const lat = Number(tekst(venue, 'latitude'));
  const lon = Number(tekst(venue, 'longitude'));
  const maWsp = tekst(venue, 'latitude') != null && tekst(venue, 'longitude') != null && Number.isFinite(lat) && Number.isFinite(lon);
  const nazwa = tytul ?? sklad.slice(0, 3).join(' + ');
  const sala = czysc(tekst(venue, 'name'), 200);
  const miasto = czysc(tekst(venue, 'city'), 100);
  const url = strona && bezpiecznyLink(strona) ? strona : null;
  return {
    id: `bit:${id}`, source: 'bit', name: nazwa, date: m[1], time: m[2] === '00:00' ? null : m[2],
    startUtc: null, timezone: null, status: 'SCHEDULED',
    description: cut(plain(tekst(e, 'description')), 1000),
    ticketUrl: bilety, sourceUrl: url, imageUrl: null, thumbUrl: null,
    venueName: sala, city: miasto, cityKey: cityKey(miasto), address: czysc(tekst(venue, 'street_address'), 300),
    lat: maWsp ? lat : null, lon: maWsp ? lon : null, genre: null, subGenre: null,
    performers: sklad.map((name) => ({ id: null, name, links: {} })),
    country: countryCode(tekst(venue, 'country')),
    seriesKey: seriesKey(nazwa, null, sala),
    organizer: null,
    sources: [{ kind: 'BANDSINTOWN', name: 'Bandsintown', url }],
  };
}

// Nadchodzace koncerty jednego wykonawcy. Nieznany wykonawca = pusta lista.
export async function artistEvents(artist, { appId, fetchFn = fetch, signal }) {
  const adres = `${API}/artists/${pathName(artist)}/events?app_id=${encodeURIComponent(appId)}&date=upcoming`;
  let odp;
  try {
    odp = await fetchFn(adres, { signal, headers: { Accept: 'application/json' } });
  } catch (e) {
    if (e?.name === 'AbortError') throw e;
    throw new BitError('SIEC', `Bandsintown nie odpowiada: ${String(e?.message ?? e).replaceAll(appId, '***')}`);
  }
  if (odp.status === 404) return [];
  if (odp.status === 401 || odp.status === 403) throw new BitError('KLUCZ', `Bandsintown odmówił: HTTP ${odp.status}`);
  if (odp.status === 429) throw new BitError('LIMIT', 'Bandsintown: za dużo zapytań');
  if (!odp.ok) throw new BitError('HTTP', `Bandsintown: HTTP ${odp.status}`);
  let json = null;
  try { json = await odp.json(); } catch { return []; }
  if (!Array.isArray(json)) return [];
  return json.map((e) => readEvent(e, artist)).filter(Boolean);
}
