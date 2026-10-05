import test from 'node:test';
import assert from 'node:assert/strict';
import { linkiWTekscie, skrocAdres } from './linki.js';

test('tekst bez linkow to jedna czesc', () => {
  assert.deepEqual(linkiWTekscie('o 19 pod klubem'), [{ tekst: 'o 19 pod klubem' }]);
});

test('link w srodku zdania, kropka na koncu zostaje w tekscie', () => {
  assert.deepEqual(linkiWTekscie('zobacz https://youtu.be/dQw4w9WgXcQ. super'), [
    { tekst: 'zobacz ' }, { link: 'https://youtu.be/dQw4w9WgXcQ' }, { tekst: '. super' },
  ]);
});

test('sam link i dwa linki obok siebie', () => {
  assert.deepEqual(linkiWTekscie('https://a.pl/x'), [{ link: 'https://a.pl/x' }]);
  assert.deepEqual(linkiWTekscie('https://a.pl, http://b.pl!'), [
    { link: 'https://a.pl' }, { tekst: ', ' }, { link: 'http://b.pl' }, { tekst: '!' },
  ]);
});

test('nawias: zamykajacy po adresie odpada, a w adresie z otwierajacym zostaje', () => {
  assert.deepEqual(linkiWTekscie('(https://a.pl/x)'), [{ tekst: '(' }, { link: 'https://a.pl/x' }, { tekst: ')' }]);
  assert.deepEqual(linkiWTekscie('https://pl.wikipedia.org/wiki/Kult_(zespół)'),
    [{ link: 'https://pl.wikipedia.org/wiki/Kult_(zespół)' }]);
});

test('tylko http(s) - javascript: i zwykle slowa nie sa linkami', () => {
  assert.deepEqual(linkiWTekscie('javascript:alert(1) www.a.pl'), [{ tekst: 'javascript:alert(1) www.a.pl' }]);
});

test('skracanie: bez protokolu i www, najwyzej 42 znaki z wielokropkiem', () => {
  assert.equal(skrocAdres('https://www.youtube.com/watch?v=dQw4w9WgXcQ'), 'youtube.com/watch?v=dQw4w9WgXcQ');
  const dlugi = skrocAdres(`https://example.com/${'a'.repeat(80)}`);
  assert.equal(dlugi.length, 42);
  assert.ok(dlugi.endsWith('…'));
});

test('dwa wywolania pod rzad daja to samo (wyrazenie z flaga g nie pamieta stanu)', () => {
  const t = 'https://a.pl i https://b.pl';
  assert.deepEqual(linkiWTekscie(t), linkiWTekscie(t));
});
