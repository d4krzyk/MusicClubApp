import { test } from 'node:test';
import assert from 'node:assert/strict';
import { liczKlatkiGif } from './gifKlatki.js';

/* Najmniejszy poprawny GIF 1x1 z paleta 2 kolorow; klatki doklejamy po kolei. */
const NAGLOWEK = [0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 1, 0, 1, 0, 0x80, 0, 0, 0, 0, 0, 255, 255, 255];
const PETLA = [0x21, 0xFF, 11, ...'NETSCAPE2.0'.split('').map((z) => z.charCodeAt(0)), 3, 1, 0, 0, 0];
const OPOZNIENIE = [0x21, 0xF9, 4, 0, 10, 0, 0, 0];
const KLATKA = [0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 0x01, 0];
/* Klatka z wlasna paleta 4 kolorow (12 bajtow) - parser musi ja przeskoczyc. */
const KLATKA_Z_PALETA = [0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0x81, 0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 2, 2, 0x44, 0x01, 0];
const KONIEC = [0x3B];

const gif = (...czesci) => new Uint8Array(czesci.flat());

test('jedna klatka to nie animacja', () => {
  assert.equal(liczKlatkiGif(gif(NAGLOWEK, KLATKA, KONIEC)), 1);
  assert.equal(liczKlatkiGif(gif(NAGLOWEK, OPOZNIENIE, KLATKA, KONIEC)), 1);
});

test('dwie klatki z petla i opoznieniami to animacja', () => {
  assert.equal(liczKlatkiGif(gif(NAGLOWEK, PETLA, OPOZNIENIE, KLATKA, OPOZNIENIE, KLATKA_Z_PALETA, KONIEC)), 2);
  assert.equal(liczKlatkiGif(gif(NAGLOWEK, KLATKA_Z_PALETA, KLATKA, KLATKA, KONIEC), 10), 3);
});

test('uszkodzony albo obcy plik rzuca', () => {
  assert.throws(() => liczKlatkiGif(new Uint8Array([0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0])));
  assert.throws(() => liczKlatkiGif(gif(NAGLOWEK, [0x2C, 0, 0])));
  assert.throws(() => liczKlatkiGif(gif(NAGLOWEK, [0x21, 0xF9, 4, 0, 10])));
  assert.throws(() => liczKlatkiGif(gif(NAGLOWEK, [0x99])));
});

test('bez znacznika konca liczy to, co bylo', () => {
  assert.equal(liczKlatkiGif(gif(NAGLOWEK, KLATKA)), 1);
});
