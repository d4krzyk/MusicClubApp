import test from 'node:test';
import assert from 'node:assert/strict';
import { diff, applyEdit } from './edycja.js';

const fb = { id: 'ics:k:1', source: 'ics', name: 'Buenos Vampiros - OLD SKULL', date: '2026-10-13', time: '19:00',
  startUtc: '2026-10-13T17:00:00.000Z', endUtc: '2026-10-13T21:00:00.000Z', timezone: null, venueName: 'Syrena', address: 'ul Wilcza 30',
  city: null, cityKey: null, description: 'Opis', ticketUrl: null, performers: [] };

test('poprawka: tylko zmienione pola; puste pole to tez poprawka', () => {
  assert.deepEqual(diff(fb, { ...fb, time: '20:00', city: 'Warszawa', description: '' }), { time: '20:00', city: 'Warszawa', description: null });
  assert.deepEqual(diff(fb, { ...fb }), {});
});

test('zmiana godziny przelicza start (czas polski) i przesuwa koniec; miasto z listy dostaje klucz', () => {
  const e = applyEdit(fb, { time: '20:30', city: 'warszawa' });
  assert.equal(e.time, '20:30');
  assert.equal(e.startUtc, '2026-10-13T18:30:00.000Z');
  assert.equal(e.endUtc, '2026-10-13T22:30:00.000Z');
  assert.equal(e.city, 'Warszawa');
  assert.equal(e.cityKey, 'warsaw');
  assert.equal(e.edited, true);
  const bezGodziny = applyEdit(fb, { time: null });
  assert.equal(bezGodziny.time, null);
  assert.equal(bezGodziny.startUtc, null);
});

test('zle dane nie psuja wydarzenia: pusta nazwa, zla data, zly link', () => {
  const e = applyEdit(fb, { name: null, date: '13.10.2026', ticketUrl: 'javascript:alert(1)' });
  assert.equal(e.name, fb.name);
  assert.equal(e.date, fb.date);
  assert.equal(e.ticketUrl, null);
  assert.equal(applyEdit(fb, {}), fb);
  assert.equal(applyEdit(fb, { ticketUrl: 'https://bilety.pl/x' }).ticketUrl, 'https://bilety.pl/x');
});

test('wspolrzedne z podpowiedzi adresu: liczby, zle albo pojedyncze = brak', () => {
  assert.deepEqual(diff(fb, { ...fb, lat: 52.2, lon: 21.01 }), { lat: '52.2', lon: '21.01' });
  const e = applyEdit(fb, { lat: '52.2', lon: '21.01' });
  assert.deepEqual([e.lat, e.lon], [52.2, 21.01]);
  const zle = applyEdit(fb, { lat: 'abc', lon: '21' });
  assert.deepEqual([zle.lat, zle.lon], [null, null]);
  assert.deepEqual([applyEdit(fb, { lat: '95', lon: '21' }).lat], [null]);
  // wpisany recznie adres kasuje stare wspolrzedne z kalendarza
  const zGeo = { ...fb, lat: 50, lon: 19 };
  assert.deepEqual(diff(zGeo, { ...zGeo, address: 'Inna 1', lat: '', lon: '' }), { address: 'Inna 1', lat: null, lon: null });
  const bez = applyEdit(zGeo, { address: 'Inna 1', lat: null, lon: null });
  assert.deepEqual([bez.lat, bez.lon], [null, null]);
});
