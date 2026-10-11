import test from 'node:test';
import assert from 'node:assert/strict';
import { plain, joined, cut } from './plainText.js';
import { strip, sentences } from './boilerplate.js';
import { nameKey, mentions, cityKey, seriesKey } from './nameKeys.js';
import { tags, families, normalizeTag } from './genres.js';

const STODOLA = 'Service fee depends on the ticket prices and the conditions agreed with the Organiser. '
  + 'Additional fees may be added to the ticket price if applicable.';
const LIVE_NATION = 'Service fee - is included in the ticket price. It depends on the ticket prices and the '
  + 'conditions agreed with the Organizer. Additional fees may be added to the ticket price if applicable. Seating '
  + 'chart reflects general layout of the venue. To buy accessible tickets please contact us. The concert is '
  + 'organised by: Live Nation Sp. z o.o., with its registered office in Warsaw, at Wyględowska 10/23, recorded in '
  + 'the commercial register maintained by the District Court for the capital city of Warsaw, XIII Division of the '
  + 'Commercial Register, under the number 0000216013, EU VAT No.: PL5213300869.';

test('prawdziwe formulki z Ticketmastera: nic nie zostaje', () => {
  assert.equal(strip(STODOLA), null);
  assert.equal(strip(LIVE_NATION), null);
});

test('prawdziwy opis zostaje, formulki wokol znikaja, akapity zostaja', () => {
  assert.equal(strip(`Eagle-Eye Cherry wraca z trasą na 30-lecie albumu Desireless. ${STODOLA}`),
    'Eagle-Eye Cherry wraca z trasą na 30-lecie albumu Desireless.');
  assert.equal(strip(`Wieczór pełen przebojów!\n\n${LIVE_NATION}\n\nSupport: Yot Club.`), 'Wieczór pełen przebojów!\n\nSupport: Yot Club.');
  const zwykly = 'The band will play their debut album in full. Doors open at 7 PM. Expect a few surprises!';
  assert.equal(strip(zwykly), zwykly);
  assert.equal(strip('Sing along with us. Service fee is included in the ticket price.'), 'Sing along with us.');
});

test('polskie formulki', () => {
  assert.equal(strip('Opłata serwisowa jest wliczona w cenę biletu. Do ceny biletu mogą zostać doliczone opłaty dodatkowe. '
    + 'Plan sali ma charakter poglądowy. Organizatorem koncertu jest Live Nation Sp. z o.o. z siedzibą w Warszawie, '
    + 'ul. Wyględowska 10/23, KRS 0000216013, NIP 521-330-08-69. Limit biletów: 6 na osobę. Bilety nie podlegają zwrotowi. '
    + 'Koncert promuje nową płytę zespołu.'), 'Koncert promuje nową płytę zespołu.');
  assert.equal(strip('Bilety dla osób z niepełnosprawnościami do kupienia przez infolinię.'), null);
});

test('skroty i inicjaly nie rozcinaja danych spolki', () => {
  assert.equal(strip('Gramy cały album. Organizator: Live Nation, ul. Wyględowska 10/23, KRS 0000216013.'), 'Gramy cały album.');
  assert.equal(strip('Gramy cały album. Organizator: P. Nowak z siedzibą w Gdańsku.'), 'Gramy cały album.');
  assert.equal(strip('Gramy cały album. Organizator: Agencja ABC, ul. Długa 5 lok. 3, 00-001 Warszawa, KRS 0000123456.'), 'Gramy cały album.');
  assert.deepEqual(sentences('Gra J. Cole. Potem St. Vincent! A na koniec Hey.'), ['Gra J. Cole.', 'Potem St. Vincent!', 'A na koniec Hey.']);
});

test('puste i brzegowe', () => {
  assert.equal(strip(null), null);
  assert.equal(strip('   '), null);
  assert.equal(strip('The concert is organised by Live Nation.'), 'The concert is organised by Live Nation.');
});

test('HTML: akapity zostaja, znaczniki w zdaniu znikaja bez sladu, encje po zdjeciu znacznikow', () => {
  assert.equal(plain('<p>Kovacs to <b>holenderska</b> wokalistka.</p><p>Trasa &amp; nowa płyta<br>w 2026&nbsp;roku</p>'),
    'Kovacs to holenderska wokalistka.\n\nTrasa & nowa płyta\nw 2026 roku');
  assert.equal(plain('Kocham was <3 i &lt;b&gt; zostaje'), 'Kocham was <3 i <b> zostaje');
  assert.equal(plain('  linia   1  \n\n\n\n  linia 2 '), 'linia 1\n\nlinia 2');
  assert.equal(plain('<p> </p><br>'), null);
  assert.equal(plain('Zesp<i>o</i>ł gra w <b>Warszawie</b>.'), 'Zespoł gra w Warszawie.');
  assert.equal(plain('Pierwszy akapit.<p>Drugi akapit.'), 'Pierwszy akapit.\n\nDrugi akapit.');
  assert.equal(plain('<script>alert(1)</script>Tekst'), 'Tekst');
});

test('joined: bez powtorzen, cut: na granicy slowa', () => {
  assert.equal(joined('Kovacs wraca.', 'Kovacs  wraca. Support: Yot Club.', 'Bilety w kasie.'),
    'Kovacs  wraca. Support: Yot Club.'.replace('  ', ' ') + '\n\nBilety w kasie.');
  assert.equal(joined('To samo.', '  to   SAMO. '), 'To samo.');
  assert.equal(joined(null, ''), null);
  const c = cut('slowo '.repeat(600), 100);
  assert.ok(c.length <= 100 && c.endsWith('slowo…'));
});

test('klucze nazw i miast', () => {
  assert.equal(nameKey('MROZU'), nameKey('Mrozu'));
  assert.equal(nameKey('Podsiadło'), 'podsiadlo');
  assert.equal(nameKey('The Weeknd'), 'weeknd');
  assert.equal(nameKey('Łona i Webber'), 'lona i webber');
  assert.equal(nameKey('Simon & Garfunkel'), 'simon and garfunkel');
  assert.equal(mentions(nameKey('Candlelight: Coldplay w Warszawie'), 'coldplay'), true);
  assert.equal(mentions(nameKey('Hey Jude - tribute'), 'hey'), false);
  assert.equal(cityKey('Łódź '), 'lodz');
  assert.equal(cityKey('Warszawa'), cityKey('Warsaw'));
  assert.equal(seriesKey('Koncert  Przy Świecach', 'V1', 'X'), 'koncert przy świecach|V1');
});

test('gatunki: etykiety, rodziny, odsiew tagow niebedacych gatunkiem', () => {
  assert.deepEqual([...tags('Hip-Hop/Rap')], ['hiphop', 'rap']);
  assert.equal(normalizeTag('R&B'), 'rnb');
  assert.deepEqual([...tags('polish')], []);
  assert.ok(families('polishhiphop').has('hip-hop'));
  assert.ok(!families('dancehall').has('electronic'));
  assert.ok(!families('dubstep').has('reggae'));
});
