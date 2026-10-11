import { cityKey, seriesKey } from './nameKeys.js';
import { cityByName, CITIES, distanceKm } from './location.js';
import { plain, cut } from './plainText.js';
import { bezpiecznyLink } from './ticketmaster.js';

// Pliki kalendarza (RFC 5545): wydarzenia z Facebooka, Google Calendar, stron klubow. Czytamy tylko VEVENT i tylko to,
// co da sie pokazac: nazwe, termin, miejsce, opis, link. Powtarzanie (RRULE) - tylko pierwszy termin.

const MAX_WYDARZEN = 3000;

// Nazwy stref z Outlooka/Windows, ktore czasem trafiaja do TZID zamiast nazw IANA.
const STREFY_WINDOWS = {
  'Central European Standard Time': 'Europe/Warsaw', 'Central Europe Standard Time': 'Europe/Budapest',
  'W. Europe Standard Time': 'Europe/Berlin', 'GMT Standard Time': 'Europe/London', 'Romance Standard Time': 'Europe/Paris',
  'E. Europe Standard Time': 'Europe/Bucharest', 'FLE Standard Time': 'Europe/Kiev', 'UTC': 'UTC',
  'Eastern Standard Time': 'America/New_York', 'Pacific Standard Time': 'America/Los_Angeles',
};

// Linie zawiniete (zaczynajace sie od spacji albo tabulatora) to ciag dalszy poprzedniej.
export function unfold(tekst) {
  return String(tekst).replace(/\r\n?/g, '\n').replace(/\n[ \t]/g, '').split('\n');
}

export function unescapeText(v) {
  return v.replace(/\\([nN,;\\])/g, (m, z) => (z === 'n' || z === 'N' ? '\n' : z));
}

// "DTSTART;TZID=Europe/Warsaw:20261120T200000" -> { name, params: {TZID}, value }
export function parseLine(linia) {
  let i = 0;
  let wCudzyslowie = false;
  for (; i < linia.length; i++) {
    const z = linia[i];
    if (z === '"') wCudzyslowie = !wCudzyslowie;
    else if (z === ':' && !wCudzyslowie) break;
  }
  if (i >= linia.length) return null;
  const [name, ...parametry] = linia.slice(0, i).split(';');
  const params = {};
  for (const p of parametry) {
    const r = p.indexOf('=');
    if (r > 0) params[p.slice(0, r).toUpperCase()] = p.slice(r + 1).replace(/^"|"$/g, '');
  }
  return { name: name.toUpperCase(), params, value: linia.slice(i + 1) };
}

const pad = (n) => String(n).padStart(2, '0');

function strefaIstnieje(tz) {
  try { new Intl.DateTimeFormat('en-US', { timeZone: tz }); return true; } catch { return false; }
}

// Czesci daty w danej strefie dla chwili (ms).
export function zoned(ms, tz) {
  const f = new Intl.DateTimeFormat('en-GB', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
  const c = Object.fromEntries(f.formatToParts(new Date(ms)).map((p) => [p.type, p.value]));
  return { y: +c.year, m: +c.month, d: +c.day, h: +c.hour, mi: +c.minute, s: +c.second };
}

// Czas "na zegarze" w strefie tz -> chwila UTC (dwa przyblizenia wystarczaja takze przy zmianie czasu).
export function wallToUtc(y, m, d, h, mi, s, tz) {
  const cel = Date.UTC(y, m - 1, d, h, mi, s);
  let ms = cel;
  for (let k = 0; k < 2; k++) {
    const z = zoned(ms, tz);
    ms += cel - Date.UTC(z.y, z.m - 1, z.d, z.h, z.mi, z.s);
  }
  return ms;
}

// Wartosc DTSTART/DTEND -> { date, time, utc } w strefie wyswietlania (displayTz) dla czasow UTC.
export function parseDate(value, params = {}, displayTz) {
  const v = value.trim();
  const m = v.match(/^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})?(Z)?)?$/);
  if (!m) return null;
  const [, y, mo, d, h, mi, s = '00', z] = m;
  if (h == null || params.VALUE === 'DATE') return { date: `${y}-${mo}-${d}`, time: null, utc: null };
  if (z) {
    const ms = Date.UTC(+y, +mo - 1, +d, +h, +mi, +s);
    const w = zoned(ms, displayTz);
    return { date: `${w.y}-${pad(w.m)}-${pad(w.d)}`, time: `${pad(w.h)}:${pad(w.mi)}`, utc: new Date(ms).toISOString() };
  }
  const tz = params.TZID ? (STREFY_WINDOWS[params.TZID] ?? params.TZID) : null;
  const utc = tz && strefaIstnieje(tz) ? new Date(wallToUtc(+y, +mo, +d, +h, +mi, +s, tz)).toISOString() : null;
  return { date: `${y}-${mo}-${d}`, time: `${h}:${mi}`, utc, tz: utc ? tz : null };
}

// Miejsce z LOCATION: "Progresja, Fort Wola 22, 01-258 Warszawa, Polska" -> sala, adres, miasto (z listy miast).
export function splitLocation(loc) {
  if (!loc) return { venueName: null, address: null, city: null };
  const czesci = loc.split(',').map((c) => c.trim()).filter(Boolean);
  let city = null;
  let indeks = -1;
  for (let i = czesci.length - 1; i >= 0 && !city; i--) {
    const bezKodu = czesci[i].replace(/^\d{2}-\d{3}\s+/, '').replace(/\s+\d{2}-\d{3}$/, '');
    const c = cityByName(bezKodu);
    if (c) { city = c.name; indeks = i; }
  }
  const venueName = czesci.length > 1 || !city ? czesci[0] : null;
  const address = czesci.slice(1, indeks >= 0 ? indeks : undefined).join(', ') || null;
  return { venueName: venueName?.slice(0, 200) ?? null, address: address?.slice(0, 300) ?? null, city };
}

// Miasto z nazwy wydarzenia, gdy nie ma go w miejscu: "Koncert / Pod Minoga, Poznan", "Wiec - Warszawa". Tylko cale
// czesci oddzielone przecinkiem, ukosnikiem, kreska albo "|" - zeby "Sopot" w srodku zdania nie udawal miasta.
export function cityFromName(nazwa) {
  if (!nazwa) return null;
  const czesci = nazwa.split(/\s*[,/|]\s*|\s+[-–—]\s+/).map((c) => c.trim()).filter(Boolean);
  for (let i = czesci.length - 1; i >= 0; i--) {
    const c = cityByName(czesci[i]);
    if (c) return c.name;
  }
  return null;
}

// Najblizsze miasto z listy do punktu (GEO), gdy blizej niz 25 km.
export function cityNear(lat, lon) {
  let best = null;
  let km = 25;
  for (const c of CITIES) {
    const d = distanceKm(lat, lon, c.lat, c.lon);
    if (d < km) { km = d; best = c.name; }
  }
  return best;
}

// Nazwa kalendarza do pokazania: X-WR-CALNAME, a eksport z Facebooka (plik nazwany numerem konta "u1000...") - "Facebook".
export function calendarName(tekst, zapasowa) {
  const linie = unfold(tekst);
  const nazwa = linie.map(parseLine).find((p) => p?.name === 'X-WR-CALNAME')?.value;
  if (nazwa && unescapeText(nazwa).trim()) return unescapeText(nazwa).trim().slice(0, 60);
  if (/^PRODID:.*facebook/im.test(linie.join('\n')) || /^UID:[^\n]*@facebook\.com/im.test(linie.join('\n'))) return 'Facebook';
  return zapasowa;
}

// Caly plik -> nasze wydarzenia. sourceId rozroznia kalendarze (ten sam UID w dwoch plikach to dwa wpisy).
export function parseIcs(tekst, { sourceId, sourceName, displayTz } = {}) {
  const strefa = displayTz ?? Intl.DateTimeFormat().resolvedOptions().timeZone ?? 'UTC';
  const wynik = [];
  let biezace = null;
  let zagniezdzenie = 0;
  for (const linia of unfold(tekst)) {
    if (!linia) continue;
    const p = parseLine(linia);
    if (!p) continue;
    if (p.name === 'BEGIN' && p.value.toUpperCase() === 'VEVENT') { biezace = {}; zagniezdzenie = 0; continue; }
    if (!biezace) continue;
    if (p.name === 'BEGIN') { zagniezdzenie++; continue; } // VALARM w srodku wydarzenia
    if (p.name === 'END' && zagniezdzenie > 0) { zagniezdzenie--; continue; }
    if (p.name === 'END' && p.value.toUpperCase() === 'VEVENT') {
      const e = zbuduj(biezace, sourceId, sourceName, strefa);
      if (e) wynik.push(e);
      biezace = null;
      if (wynik.length >= MAX_WYDARZEN) break;
      continue;
    }
    if (zagniezdzenie === 0 && !(p.name in biezace)) biezace[p.name] = p;
  }
  return wynik;
}

function zbuduj(w, sourceId, sourceName, strefa) {
  const start = w.DTSTART ? parseDate(w.DTSTART.value, w.DTSTART.params, strefa) : null;
  const nazwa = w.SUMMARY ? unescapeText(w.SUMMARY.value).trim().slice(0, 300) : '';
  if (!start || !nazwa) return null;
  const koniec = w.DTEND ? parseDate(w.DTEND.value, w.DTEND.params, strefa) : null;
  const uid = w.UID ? w.UID.value.trim() : `${start.date}|${nazwa}`;
  const miejsce = splitLocation(w.LOCATION ? unescapeText(w.LOCATION.value) : null);
  let lat = null;
  let lon = null;
  if (w.GEO) {
    const [a, b] = w.GEO.value.split(/[;,]/).map(Number);
    if (Number.isFinite(a) && Number.isFinite(b) && Math.abs(a) <= 90 && Math.abs(b) <= 180) { lat = a; lon = b; }
  }
  if (!miejsce.city) miejsce.city = (lat != null ? cityNear(lat, lon) : null) ?? cityFromName(nazwa);
  const opis = w.DESCRIPTION ? cut(plain(unescapeText(w.DESCRIPTION.value)), 4000) : null;
  const url = w.URL ? w.URL.value.trim() : null;
  return {
    id: `ics:${sourceId ?? 'plik'}:${uid}`.slice(0, 300),
    source: 'ics',
    name: nazwa,
    date: start.date,
    time: start.time,
    startUtc: start.utc,
    endUtc: koniec?.utc ?? null,
    timezone: start.tz ?? null,
    status: w.STATUS?.value.trim().toUpperCase() === 'CANCELLED' ? 'CANCELLED' : 'SCHEDULED',
    description: opis,
    ticketUrl: null,
    sourceUrl: url && bezpiecznyLink(url) ? url : null,
    imageUrl: null, thumbUrl: null,
    venueName: miejsce.venueName, address: miejsce.address, city: miejsce.city, cityKey: cityKey(miejsce.city),
    lat, lon,
    genre: null, subGenre: null,
    performers: [],
    country: null,
    seriesKey: `ics|${uid}`,
    organizer: null,
    sources: [{ kind: 'ICS', name: sourceName ?? 'Kalendarz', url: url && bezpiecznyLink(url) ? url : null }],
  };
}

// webcal:// to zwykle https://
export function normalizeFeedUrl(adres) {
  const a = String(adres ?? '').trim().replace(/^webcals?:\/\//i, 'https://');
  return bezpiecznyLink(a) ? a : null;
}
