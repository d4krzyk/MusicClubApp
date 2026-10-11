import { plain, cut, unescapeHtml } from './plainText.js';

export const API = 'https://ws.audioscrobbler.com/2.0/';
const BRAK_ARTYSTY = 6; // kod bledu Last.fm "nie ma takiego artysty" - to odpowiedz, a nie awaria
const PROG_TAGU = 20;
const MAX_GATUNKOW = 8;
const NIE_GATUNKI = new Set(['seen live', 'favorites', 'favourites', 'favorite', 'love', 'awesome', 'beautiful',
  'albums i own', 'under 2000 listeners', 'spotify', 'all', 'check out']);

export class LastFmError extends Error {
  constructor(kod, wiadomosc) { super(wiadomosc); this.kod = kod; }
}

const bezKlucza = (t, klucz) => (klucz ? String(t).replaceAll(klucz, '***') : String(t));

async function ask(method, params, { key, fetchFn = fetch, signal }) {
  const q = new URLSearchParams({ method, api_key: key, format: 'json', ...params });
  let odp;
  try {
    odp = await fetchFn(`${API}?${q}`, { signal });
  } catch (e) {
    if (e?.name === 'AbortError') throw e;
    throw new LastFmError('SIEC', bezKlucza(`Last.fm nie odpowiada: ${e?.message ?? e}`, key));
  }
  let json = null;
  try { json = await odp.json(); } catch { /* tresc bez JSON-a */ }
  if (json?.error) throw new LastFmError(json.error === BRAK_ARTYSTY ? 'BRAK' : json.error === 10 ? 'KLUCZ' : json.error === 6 ? 'BRAK' : 'BLAD',
    bezKlucza(`Last.fm: ${json.message ?? json.error}`, key));
  if (!odp.ok || !json) throw new LastFmError(odp.status === 403 ? 'KLUCZ' : 'HTTP', `Last.fm: HTTP ${odp.status}`);
  return json;
}

const lista = (v) => (Array.isArray(v) ? v : v ? [v] : []);

// Najczesciej sluchani artysci uzytkownika Last.fm.
export async function topArtists(user, limit, opts) {
  const json = await ask('user.gettopartists', { user, period: 'overall', limit: String(Math.max(1, Math.min(limit, 200))) }, opts);
  return lista(json.topartists?.artist).map((a) => String(a.name ?? '').trim()).filter(Boolean);
}

// Gatunki artysty z tagow Last.fm; null, gdy Last.fm nie odpowiedzial (to nie to samo co "brak tagow").
export async function artistGenres(name, opts) {
  if (!name || !String(name).trim()) return [];
  let json;
  try {
    json = await ask('artist.gettoptags', { artist: name, autocorrect: '1' }, opts);
  } catch (e) {
    if (e instanceof LastFmError && e.kod === 'BRAK') return [];
    throw e;
  }
  const wynik = [];
  for (const t of lista(json.toptags?.tag)) {
    if (wynik.length >= MAX_GATUNKOW) break;
    const nazwa = String(t.name ?? '').toLowerCase().trim();
    if (!nazwa || Number(t.count ?? 0) < PROG_TAGU || NIE_GATUNKI.has(nazwa) || nazwa.length > 60) continue;
    wynik.push(nazwa);
  }
  return wynik;
}

// Streszczenie jako zwykly tekst: bez odnosnika "Read more on Last.fm", do 1200 znakow.
export function bioText(html) {
  if (!html) return null;
  const bezLinku = String(html).replace(/<a\b[^>]*>[\s\S]*?<\/a>/gi, '');
  const t = plain(unescapeHtml(bezLinku.replace(/<\/?[A-Za-z][^>]*>/g, '')));
  return t ? cut(t, 1200) : null;
}

export async function artistInfo(name, lang, opts) {
  const json = await ask('artist.getinfo', { artist: name, autocorrect: '1', lang: lang ?? 'en' }, opts);
  const a = json.artist ?? {};
  const url = typeof a.url === 'string' && a.url.startsWith('https://www.last.fm/') ? a.url : null;
  return {
    name: a.name ?? name,
    bio: bioText(a.bio?.summary),
    listeners: Number(a.stats?.listeners) || null,
    similar: lista(a.similar?.artist).map((s) => String(s.name ?? '').trim()).filter(Boolean).slice(0, 5),
    url,
  };
}
