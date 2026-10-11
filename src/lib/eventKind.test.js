import test from 'node:test';
import assert from 'node:assert/strict';
import { classify, performerCandidates, textGenres, enrich } from './eventKind.js';

// Prawdziwe tytuly z eksportu Facebooka uzytkownika (wydarzenia "zainteresowany").
const ev = (name, description = null, o = {}) => ({ source: 'ics', id: 'ics:k:1', name, description, venueName: null, performers: [], ...o });
const BUENOS = ev('Buenos Vampiros (Argentyna), Sofia Night Club - OLD SKULL - post punk, goth, deathrock, swamp punk',
  'Buenos Vampiros to trzy dziewczyny i chłopak z Mar del Plata.');
const KONTRAVOID = ev('KONTRAVOID + Spike Hellis / 13.12.26 / Pod Minogą, Poznań',
  "Jak brzmi pustka? Kontravoid przeprowadzi Was przez nią tanecznym, darkwave'owym krokiem.", { venueName: 'Klub pod Minogą' });
const SCIEZKA = ev('Ścieżka Duchów BYDGOSZCZ 16 - 31.10.', null, { startUtc: '2026-10-16T16:00:00Z', endUtc: '2026-10-29T22:30:00Z' });
const PROTEST = ev('Czerwona Kartka za Aborcję - Warszawa');

test('koncerty z Facebooka sa koncertami, protest i wielodniowa atrakcja - nie', () => {
  assert.equal(classify(BUENOS).music, true);
  assert.equal(classify(KONTRAVOID).music, true);
  assert.equal(classify(SCIEZKA).music, false);
  assert.equal(classify(PROTEST).music, false);
  assert.equal(classify(ev('Hey')).music, false, 'sama nazwa bez sladu muzyki - dopoki Last.fm nie potwierdzi');
  assert.equal(classify(ev('Hey'), { hey: ['rock'] }).music, true, 'Last.fm potwierdza wykonawce');
  assert.equal(classify(ev('Koncert charytatywny - zbiórka na schronisko')).music, false, 'zbiorka przewaza nad slowem "koncert"');
  assert.equal(classify(ev('Festiwal Tauron Nowa Muzyka', 'Techno, ambient i house przez trzy dni.', { startUtc: '2026-08-01T12:00:00Z', endUtc: '2026-08-03T23:00:00Z' })).music, true,
    'festiwal moze trwac kilka dni');
  assert.equal(classify(ev('Jarmark', 'Kiermasz ozdób.', { startUtc: '2026-12-01T10:00:00Z', endUtc: '2026-12-01T13:00:00Z' })).music, false);
});

test('wykonawcy z tytulu: bez dopiskow w nawiasach, dat, miasta, klubu i listy gatunkow', () => {
  assert.deepEqual(performerCandidates(BUENOS), ['Buenos Vampiros', 'Sofia Night Club', 'OLD SKULL']);
  assert.deepEqual(performerCandidates(KONTRAVOID), ['KONTRAVOID', 'Spike Hellis']);
  assert.deepEqual(performerCandidates(SCIEZKA), ['Ścieżka Duchów']);
  assert.deepEqual(performerCandidates(ev('Kult [PL] x Hey feat. Kasia Nosowska - live')), ['Kult', 'Hey', 'Kasia Nosowska']);
});

test('gatunki z tekstu: szczegolowy zamiast ogolnego, polskie odmiany, bez falszywych trafien', () => {
  assert.deepEqual(textGenres(BUENOS.name).map((g) => g.name), ['post punk', 'deathrock', 'goth', 'swamp punk']);
  assert.deepEqual(textGenres("tanecznym, darkwave'owym krokiem").map((g) => g.name), ['darkwave']);
  assert.deepEqual(textGenres('Wieczór rockowy i punk').map((g) => g.name), ['punk', 'rock']);
  assert.deepEqual(textGenres('popołudnie z popularnym rockefellerem').map((g) => g.name), []);
  assert.deepEqual(textGenres('Gotham Club').map((g) => g.name), []);
});

test('ocena z reki wygrywa z ocena aplikacji; Ticketmaster bez zmian', () => {
  assert.equal(enrich(PROTEST, {}, 'music').music, true);
  assert.equal(enrich(BUENOS, {}, 'other').music, false);
  assert.equal(enrich(BUENOS, {}).musicGuess, true);
  const tm = { source: 'tm', name: 'X', performers: [] };
  assert.equal(enrich(tm, {}), tm);
  assert.deepEqual(enrich(KONTRAVOID, { kontravoid: ['darkwave'] }).performers.map((p) => p.name), ['KONTRAVOID']);
});

test('wielodniowe bez slowa "festiwal" traci punkty (atrakcja z gotyckim klimatem to nie koncert)', () => {
  const opis = 'Gotycki klimat i mroczna scenografia.';
  assert.equal(classify(ev('Ścieżka Duchów', opis)).music, true, 'jednego wieczoru "gotycki" wystarcza');
  assert.equal(classify(ev('Ścieżka Duchów', opis, { startUtc: '2026-10-16T16:00:00Z', endUtc: '2026-10-29T22:30:00Z' })).music, false);
});
