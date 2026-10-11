import test from 'node:test';
import assert from 'node:assert/strict';
import { newForYou, goingChanges, messages, stanDoTla } from './powiadomienia.js';
import { cityByName } from './location.js';

const W = cityByName('Warszawa');
const ev = (o) => ({ id: 'e', name: 'Koncert', date: '2026-11-20', time: '20:00', venueName: 'Progresja', city: 'Warszawa', cityKey: 'warsaw',
  lat: null, lon: null, genre: null, subGenre: null, performers: [], seriesKey: o.id, country: 'PL', status: 'SCHEDULED', ...o });
const snap = { artists: ['Kult'], tags: { kult: ['rock'] }, origin: { lat: W.lat, lon: W.lon }, radius: 0, seen: ['stary'] };
const lista = [
  ev({ id: 'stary', name: 'Kult', performers: [{ name: 'Kult' }] }),
  ev({ id: 'kult-gdansk', name: 'Kult', performers: [{ name: 'Kult' }], cityKey: 'gdansk', city: 'Gdańsk' }),
  ev({ id: 'rock-wawa', name: 'Zespół rockowy', genre: 'Rock' }),
  ev({ id: 'rock-krakow', name: 'Inny rock', genre: 'Rock', cityKey: 'krakow', city: 'Kraków' }),
  ev({ id: 'odwolany', name: 'Kult', performers: [{ name: 'Kult' }], status: 'CANCELLED', date: '2026-11-25' }),
];

test('nowe "dla ciebie": ulubiony wykonawca wszedzie, sam gatunek tylko w okolicy; znane i odwolane pomijane', () => {
  assert.deepEqual(newForYou(lista, snap, '2026-10-01').map((r) => r.event.id), ['kult-gdansk', 'rock-wawa']);
  assert.deepEqual(newForYou(lista, { ...snap, origin: null }, '2026-10-01').map((r) => r.event.id), ['kult-gdansk'], 'bez miasta - tylko wykonawcy');
  assert.deepEqual(newForYou(lista, { ...snap, radius: 300 }, '2026-10-01').map((r) => r.event.id).sort(), ['kult-gdansk', 'rock-krakow', 'rock-wawa']);
  assert.deepEqual(newForYou(lista, { ...snap, seen: lista.map((e) => e.seriesKey) }, '2026-10-01'), []);
});

test('zmiany w "Biorę udział": odwolanie, przelozenie, data, godzina; bez zmian - nic', () => {
  const going = { a: { date: '2026-11-20', time: '20:00', status: 'SCHEDULED' }, b: { date: '2026-11-20', time: '20:00', status: 'SCHEDULED' },
    c: { date: '2026-11-20', time: '20:00', status: 'SCHEDULED' }, d: { date: '2026-11-20', time: '20:00', status: 'SCHEDULED' },
    e: { date: '2026-11-20', time: '20:00', status: 'SCHEDULED' }, brak: { date: '2026-11-20', time: '20:00', status: 'SCHEDULED' } };
  const z = goingChanges([ev({ id: 'a', status: 'CANCELLED' }), ev({ id: 'b', status: 'POSTPONED' }), ev({ id: 'c', date: '2026-11-27' }),
    ev({ id: 'd', time: '21:00' }), ev({ id: 'e' })], going);
  assert.deepEqual(z.map((x) => [x.event.id, x.rodzaj]), [['a', 'CANCELLED'], ['b', 'POSTPONED'], ['c', 'DATE'], ['d', 'TIME']]);
});

test('tresc: jedno nowe z wykonawca, kilka zbiorczo z polska liczba mnoga, zmiany osobno', () => {
  const [jeden] = messages(newForYou(lista, snap, '2026-10-01').slice(0, 1), []);
  assert.equal(jeden.title, 'Nowy koncert dla ciebie');
  assert.equal(jeden.body, 'Kult\n20.11 20:00 · Progresja · Gdańsk\nLubisz: Kult');
  const r = (n) => Array.from({ length: n }, (_, i) => ({ event: ev({ id: `x${i}`, name: `K${i}` }), match: { reasons: [] } }));
  assert.equal(messages(r(3), [])[0].title, '3 nowe koncerty dla ciebie');
  assert.equal(messages(r(5), [])[0].title, '5 nowych koncertów dla ciebie');
  assert.equal(messages(r(12), [])[0].title, '12 nowych koncertów dla ciebie');
  assert.equal(messages(r(22), [])[0].title, '22 nowe koncerty dla ciebie');
  assert.ok(messages(r(6), [])[0].largeBody.endsWith(' i 2 więcej'));
  const zm = messages([], [{ event: ev({ id: 'a', name: 'Kult' }), rodzaj: 'CANCELLED' }]);
  assert.deepEqual(zm.map((m) => [m.id, m.title]), [[7100, 'Koncert odwołany']]);
  assert.equal(messages(r(2), [], 'en')[0].title, '2 new concerts for you');
  assert.deepEqual(messages([], []), []);
});

test('stan dla pracy w tle: znane serie z "Dla ciebie", tylko "Biorę udział" z Ticketmastera, wlaczniki', () => {
  const s = stanDoTla({ settings: { tmKey: 'K', country: 'PL', months: 6, radius: 0, powiadomienia: { nowe: true },
    udzial: { 'kult-gdansk': 'GOING', 'rock-wawa': 'INTERESTED', 'ics:x': 'GOING' } },
  data: { events: lista }, artistNames: ['Kult'], tags: { kult: ['rock'] }, origin: snap.origin, today: '2026-10-01', lang: 'pl' });
  assert.deepEqual(s.seen.sort(), ['kult-gdansk', 'rock-wawa', 'stary']);
  assert.deepEqual(Object.keys(s.going), ['kult-gdansk']);
  assert.deepEqual(s.going['kult-gdansk'], { date: '2026-11-20', time: '20:00', status: 'SCHEDULED' });
  assert.equal(s.nowe, true);
  assert.equal(s.zmiany, false);
  // praca w tle po takim stanie nic nie zglasza - wszystko juz widziales w aplikacji
  assert.deepEqual(newForYou(lista, s, '2026-10-01'), []);
});
