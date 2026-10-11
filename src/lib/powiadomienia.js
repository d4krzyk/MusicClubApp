import { buildTaste, rank } from './match.js';

// Co zglosic w powiadomieniu - czyste funkcje, uzywane w tle na Androidzie (src/runner/background.js) i testowane w Node.
// Stan od aplikacji ("snapshot"): klucz, kraj, miasto, gust, gatunki wykonawcow, znane serie z "Dla ciebie" i koncerty,
// na ktore idziesz.

// Nowe koncerty "dla ciebie": seria, ktorej nie bylo w "Dla ciebie" przy ostatnim otwarciu aplikacji. Zeby nie zasypac
// telefonu: zawsze, gdy gra ulubiony wykonawca; za sam gatunek - tylko w okolicy (zasieg albo 100 km), gdy znamy miasto.
export function newForYou(events, snap, today) {
  const taste = buildTaste((snap.artists ?? []).map((name) => ({ name })), snap.tags ?? {});
  const znane = new Set(snap.seen ?? []);
  const zasieg = snap.radius > 0 ? snap.radius : 100;
  return rank(events, taste, snap.tags ?? {}, { view: 'FOR_YOU', today, origin: snap.origin ?? null, radiusKm: 0 })
    .filter((r) => !znane.has(r.event.seriesKey))
    .filter((r) => r.match.reasons.some((p) => p.kind === 'ARTIST') || (r.km != null && r.km <= zasieg))
    .filter((r) => r.event.status !== 'CANCELLED');
}

// Zmiany w koncertach z "Biore udzial": odwolany, przelozony, nowa data albo godzina.
export function goingChanges(events, going) {
  const wynik = [];
  const poId = new Map(events.map((e) => [e.id, e]));
  for (const [id, stare] of Object.entries(going ?? {})) {
    const e = poId.get(id);
    if (!e) continue;
    let rodzaj = null;
    if (e.status !== stare.status && ['CANCELLED', 'POSTPONED', 'RESCHEDULED'].includes(e.status)) rodzaj = e.status;
    else if (e.date !== stare.date) rodzaj = 'DATE';
    else if ((e.time ?? null) !== (stare.time ?? null)) rodzaj = 'TIME';
    if (rodzaj) wynik.push({ event: e, rodzaj, stare });
  }
  return wynik;
}

const T = {
  pl: {
    nowy: 'Nowy koncert dla ciebie', nowe: (n) => `${n} nowe koncerty dla ciebie`, nowych: (n) => `${n} nowych koncertów dla ciebie`,
    lubisz: (a) => `Lubisz: ${a}`, i: (n) => ` i ${n} więcej`,
    CANCELLED: 'Koncert odwołany', POSTPONED: 'Koncert przełożony', RESCHEDULED: 'Zmiana terminu koncertu',
    DATE: 'Nowa data koncertu', TIME: 'Nowa godzina koncertu', test: 'Powiadomienia działają', testBody: 'Tak będą wyglądać wiadomości o nowych koncertach.',
  },
  en: {
    nowy: 'New concert for you', nowe: (n) => `${n} new concerts for you`, nowych: (n) => `${n} new concerts for you`,
    lubisz: (a) => `You like: ${a}`, i: (n) => ` and ${n} more`,
    CANCELLED: 'Concert cancelled', POSTPONED: 'Concert postponed', RESCHEDULED: 'Concert rescheduled',
    DATE: 'New concert date', TIME: 'New concert time', test: 'Notifications work', testBody: 'This is how new-concert alerts will look.',
  },
};

const krotkaData = (iso) => { const [, m, d] = iso.split('-'); return `${Number(d)}.${m}`; };
const opis = (e) => [krotkaData(e.date) + (e.time ? ` ${e.time}` : ''), e.venueName, e.city].filter(Boolean).join(' · ');
// polska liczba mnoga: 2-4 "nowe", 5+ "nowych" (ale 12-14 "nowych")
const mnoga = (t, n) => (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14) ? t.nowe(n) : t.nowych(n));

// Tresc powiadomien (najwyzej jedno zbiorcze o nowych + jedno na kazda zmiane). id stale dla rodzaju, zeby kolejne
// sprawdzenie podmienialo powiadomienie, a nie dokladalo nastepne.
export function messages(nowe, zmiany, lang = 'pl') {
  const t = T[lang === 'en' ? 'en' : 'pl'];
  const wynik = [];
  if (nowe.length === 1) {
    const { event: e, match: m } = nowe[0];
    const artysta = m.reasons.find((p) => p.kind === 'ARTIST');
    wynik.push({ id: 7001, title: t.nowy, body: `${e.name}\n${opis(e)}${artysta ? `\n${t.lubisz(artysta.value)}` : ''}`, extra: { id: e.id } });
  } else if (nowe.length > 1) {
    const linie = nowe.slice(0, 4).map(({ event: e }) => `${e.name} — ${opis(e)}`);
    const reszta = nowe.length > 4 ? t.i(nowe.length - 4) : '';
    wynik.push({ id: 7001, title: mnoga(t, nowe.length), body: `${nowe[0].event.name}${reszta || (nowe.length > 1 ? t.i(nowe.length - 1) : '')}`,
      largeBody: linie.join('\n') + reszta, extra: {} });
  }
  zmiany.forEach((z, i) => {
    const e = z.event;
    wynik.push({ id: 7100 + i, title: t[z.rodzaj], body: `${e.name}\n${opis(e)}`, extra: { id: e.id } });
  });
  return wynik;
}

export function testMessage(lang = 'pl') {
  const t = T[lang === 'en' ? 'en' : 'pl'];
  return { id: 7999, title: t.test, body: t.testBody, extra: {} };
}

// Stan dla pracy w tle (wysylany z aplikacji). "seen" = serie, ktore juz sa w "Dla ciebie" (widziales je w aplikacji) -
// o nich nie powiadamiamy. "going" = koncerty z "Biore udzial" (tylko z Ticketmastera - tylko je sprawdza praca w tle).
export function stanDoTla({ settings, data, artistNames, tags, origin, today, lang }) {
  const p = settings.powiadomienia ?? {};
  const snap = { tmKey: settings.tmKey, country: settings.country, months: settings.months, lang,
    nowe: !!p.nowe, zmiany: !!p.zmiany, artists: artistNames, origin, radius: settings.radius ?? 0, tags };
  snap.seen = newForYou(data.events, { ...snap, seen: [] }, today).map((r) => r.event.seriesKey);
  snap.going = {};
  const poId = new Map(data.events.map((e) => [e.id, e]));
  for (const [id, st] of Object.entries(settings.udzial ?? {})) {
    const e = poId.get(id);
    if (st === 'GOING' && e) snap.going[id] = { date: e.date, time: e.time ?? null, status: e.status };
  }
  return snap;
}
