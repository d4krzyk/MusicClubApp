import { nameKey, mentions } from './nameKeys.js';
import { tags as genreTags, families } from './genres.js';
import { level, eventDistance } from './location.js';

export const ZA_ARTYSTE = 100;
export const ZA_GATUNEK = 15;
export const ZA_RODZINE = 8;
export const ZA_BLISKOSC = 8; // za kazdy poziom bliskosci 0-5

// Gust: ulubieni artysci (klucz -> nazwa) i ich gatunki (z tagow Last.fm; mapa klucz artysty -> lista tagow).
export function buildTaste(artists, artistTags) {
  const arts = new Map();
  for (const a of artists) {
    const k = nameKey(a.name ?? a);
    if (k && !arts.has(k)) arts.set(k, a.name ?? a);
  }
  // Tag, ktory ma kilku moich artystow, jest bardziej "moj" niz taki, ktory ma jeden.
  const genres = new Map();
  for (const k of arts.keys()) {
    for (const raw of artistTags[k] ?? []) {
      for (const tag of genreTags(raw)) {
        const przed = genres.get(tag);
        genres.set(tag, { display: przed?.display ?? raw.toLowerCase(), count: (przed?.count ?? 0) + 1 });
      }
    }
  }
  return { artists: arts, genres, families: families(genres.keys()) };
}

export const emptyTaste = (t) => t.artists.size === 0 && t.genres.size === 0;

// Punkty i powody dla jednego wydarzenia. performerTags: klucz wykonawcy -> lista tagow (moze byc pusta).
export function match(taste, event, performerTags = {}) {
  let score = 0;
  const reasons = [];
  const performerKeys = new Set(event.performers.map((p) => nameKey(p.name)));
  const nazwaKey = nameKey(event.name);

  const dopasowani = [];
  for (const [k, nazwa] of taste.artists) {
    if (dopasowani.length < 2 && (performerKeys.has(k) || mentions(nazwaKey, k))) {
      dopasowani.push(k);
      score += ZA_ARTYSTE;
      reasons.push({ kind: 'ARTIST', value: nazwa });
    }
  }

  const eventTags = new Set([...genreTags(event.genre), ...genreTags(event.subGenre)]);
  for (const k of performerKeys) for (const t of performerTags[k] ?? []) for (const x of genreTags(t)) eventTags.add(x);

  const dokladne = [...taste.genres.keys()].filter((t) => eventTags.has(t))
    .sort((a, b) => taste.genres.get(b).count - taste.genres.get(a).count || (a < b ? -1 : a > b ? 1 : 0));
  score += ZA_GATUNEK * Math.min(3, dokladne.length);

  const tylkoRodzina = [...families(eventTags)].filter((f) => taste.families.has(f));
  const rodzinyDokladnych = families(new Set(dokladne));
  const reszta = tylkoRodzina.filter((f) => !rodzinyDokladnych.has(f));
  score += ZA_RODZINE * Math.min(2, reszta.length);

  if (dokladne.length) {
    reasons.push({ kind: 'GENRE', value: dokladne.slice(0, 3).map((t) => taste.genres.get(t).display).join(', ') });
  } else if (reszta.length) {
    reasons.push({ kind: 'GENRE', value: reszta.slice(0, 2).join(', ') });
  }
  return { score, reasons };
}

const poDacie = (a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0)
  || ((a.time ?? '99:99') < (b.time ?? '99:99') ? -1 : (a.time ?? '99:99') > (b.time ?? '99:99') ? 1 : 0)
  || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);

const zawiera = (t, fraza) => t != null && t.toLowerCase().includes(fraza);

// Terminy zwiniete w serie (kazda od najblizszego terminu), po filtrze miasta, kraju i szukanego tekstu.
export function series(events, { today, cityKey = '', phrase = '', country = '' }) {
  const szukane = (phrase ?? '').trim().toLowerCase();
  const grupy = new Map();
  for (const e of events) {
    if (e.date < today) continue;
    if (country && e.country && e.country !== country) continue;
    if (cityKey && cityKey !== e.cityKey) continue;
    if (szukane && !(zawiera(e.name, szukane) || zawiera(e.venueName, szukane) || e.performers.some((p) => zawiera(p.name, szukane)))) continue;
    if (!grupy.has(e.seriesKey)) grupy.set(e.seriesKey, []);
    grupy.get(e.seriesKey).push(e);
  }
  return [...grupy.values()].map((d) => d.sort(poDacie));
}

const poza = (origin, radiusKm, km) => radiusKm > 0 && origin != null && (km == null || km > radiusKm);

// widok: 'FOR_YOU' (tylko pasujace, od najlepszych), 'UPCOMING' (wszystkie od najblizszego terminu).
// Bliskosc miasta tylko przesuwa kolejnosc - o tym, czy cos "pasuje", decyduje gust.
export function rank(events, taste, performerTags, { view = 'FOR_YOU', today, origin = null, radiusKm = 0, cityKey = '',
  phrase = '', country = '' }) {
  const wynik = [];
  for (const dates of series(events, { today, cityKey, phrase, country })) {
    const first = dates[0];
    const km = eventDistance(origin, first);
    if (poza(origin, radiusKm, km)) continue;
    const m = match(taste, first, performerTags);
    if (view === 'FOR_YOU' && m.score <= 0) continue;
    wynik.push({ event: first, moreDates: dates.length - 1, match: m, km, total: m.score + ZA_BLISKOSC * level(km) });
  }
  if (view === 'FOR_YOU') wynik.sort((a, b) => b.total - a.total || poDacie(a.event, b.event));
  else wynik.sort((a, b) => poDacie(a.event, b.event));
  return wynik;
}

// Wszystkie terminy tej samej serii co dane wydarzenie (od najblizszego).
export function datesOf(events, event, today) {
  return events.filter((e) => e.seriesKey === event.seriesKey && e.date >= today).sort(poDacie);
}
