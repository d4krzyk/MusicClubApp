import test from 'node:test';
import assert from 'node:assert/strict';
import { buildTaste, match, rank, series } from './match.js';
import { level, distanceKm, cityByName, CITIES, eventDistance } from './location.js';
import { cityKey } from './nameKeys.js';

const ev = (o) => ({ id: 'e', name: 'Koncert', date: '2026-11-01', time: '20:00', cityKey: 'warsaw', lat: null, lon: null,
  genre: null, subGenre: null, performers: [], seriesKey: 'k', country: 'PL', venueName: 'Klub', ...o });
const gust = buildTaste([{ name: 'Kult' }, { name: 'Hey' }, { name: 'Mrozu' }], { kult: ['rock', 'polish'], hey: ['rock', 'alternative'], mrozu: ['pop'] });

test('gust: artysci po kluczu, gatunki z licznikiem, rodziny', () => {
  assert.equal(gust.artists.size, 3);
  assert.equal(gust.genres.get('rock').count, 2);
  assert.ok(!gust.genres.has('polish'));
  assert.ok(gust.families.has('rock') && gust.families.has('pop') && gust.families.has('indie'));
});

test('ulubiony artysta w skladzie 100, w nazwie tez; najwyzej dwoch', () => {
  assert.equal(match(gust, ev({ performers: [{ name: 'KULT' }] })).score, 100);
  assert.equal(match(gust, ev({ name: 'Mrozu - trasa 2026' })).score, 100);
  const m = match(gust, ev({ performers: [{ name: 'Kult' }, { name: 'Hey' }, { name: 'Mrozu' }] }));
  assert.equal(m.score, 200);
  assert.equal(m.reasons.filter((r) => r.kind === 'ARTIST').length, 2);
  assert.equal(match(gust, ev({ performers: [{ name: 'Hey Jude tribute' }] })).score, 0); // krotka nazwa tylko jako calosc
});

test('gatunek: 15 za dokladny, 8 za sama rodzine; tagi wykonawcow Last.fm', () => {
  assert.equal(match(gust, ev({ genre: 'Rock' })).score, 15);
  assert.equal(match(gust, ev({ genre: 'Hip-Hop/Rap' })).score, 0);
  assert.equal(match(gust, ev({ genre: 'Hard Rock' })).score, 8);       // "hardrock" nie jest tagiem, ale rodzina rock tak
  assert.equal(match(gust, ev({ performers: [{ name: 'Nieznany' }] }), { nieznany: ['pop', 'indie'] }).score, 15 + 8);
  const m = match(gust, ev({ genre: 'Rock' }));
  assert.deepEqual(m.reasons, [{ kind: 'GENRE', value: 'rock' }]);
});

test('blisko tylko przesuwa kolejnosc, nie wciaga na liste "Dla ciebie"', () => {
  const warszawa = cityByName('Warszawa');
  const origin = { lat: warszawa.lat, lon: warszawa.lon };
  const lista = [
    ev({ id: 'a', seriesKey: 'a', name: 'Daleko', cityKey: 'gdansk', performers: [{ name: 'Kult' }] }),
    ev({ id: 'b', seriesKey: 'b', name: 'Blisko', cityKey: 'warsaw', performers: [{ name: 'Kult' }] }),
    ev({ id: 'c', seriesKey: 'c', name: 'Nic', cityKey: 'warsaw' }),
  ];
  const r = rank(lista, gust, {}, { view: 'FOR_YOU', today: '2026-10-11', origin });
  assert.deepEqual(r.map((x) => x.event.id), ['b', 'a']);
  const najblizsze = rank(lista, gust, {}, { view: 'UPCOMING', today: '2026-10-11', origin });
  assert.equal(najblizsze.length, 3);
});

test('promien: bez miasta nic nie robi; z miastem odpadaja dalsze i bez polozenia', () => {
  const w = cityByName('Warszawa');
  const lista = [ev({ id: 'a', seriesKey: 'a', cityKey: 'warsaw' }), ev({ id: 'b', seriesKey: 'b', cityKey: 'gdansk' }),
    ev({ id: 'c', seriesKey: 'c', cityKey: 'nieznane-miasto' })];
  const opcje = { view: 'UPCOMING', today: '2026-10-11', radiusKm: 100 };
  assert.equal(rank(lista, gust, {}, { ...opcje, origin: null }).length, 3);
  assert.deepEqual(rank(lista, gust, {}, { ...opcje, origin: { lat: w.lat, lon: w.lon } }).map((x) => x.event.id), ['a']);
});

test('serie: terminy zwiniete, minione pominiete, filtr miasta i frazy', () => {
  const lista = [ev({ id: '1', date: '2026-11-05' }), ev({ id: '2', date: '2026-11-03' }), ev({ id: '3', date: '2026-10-01' }),
    ev({ id: '4', seriesKey: 'x', name: 'Inny', cityKey: 'krakow', performers: [{ name: 'Hey' }] })];
  const s = series(lista, { today: '2026-10-11' });
  assert.equal(s.length, 2);
  assert.deepEqual(s[0].map((e) => e.id), ['2', '1']);
  assert.equal(series(lista, { today: '2026-10-11', cityKey: 'krakow' }).length, 1);
  assert.equal(series(lista, { today: '2026-10-11', phrase: 'hey' }).length, 1);
});

test('odleglosci i poziomy; lista miast', () => {
  const p = cityByName('Poznań'), wr = cityByName('Wrocław');
  const km = distanceKm(p.lat, p.lon, wr.lat, wr.lon);
  assert.ok(km > 130 && km < 160, `Poznan-Wroclaw ${km}`);
  assert.deepEqual([null, 0.5, 20, 50, 100, 200, 400].map(level), [0, 5, 4, 3, 2, 1, 0]);
  assert.ok(CITIES.length > 200);
  assert.equal(cityByName('Warsaw').name, 'Warszawa');
  assert.equal(eventDistance(null, ev({})), null);
  assert.equal(cityKey('Kraków'), 'krakow');
  assert.ok(eventDistance({ lat: p.lat, lon: p.lon }, ev({ lat: 52.4, lon: 16.9 })) < 20);
});
