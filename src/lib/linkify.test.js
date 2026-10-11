import test from 'node:test';
import assert from 'node:assert/strict';
import { linkify, shortUrl } from './linkify.js';
import { calendarName, cityFromName, cityNear, parseIcs } from './ics.js';

test('adresy w opisie: odnosniki, interpunkcja zostaje tekstem, nawias w adresie zostaje', () => {
  assert.deepEqual(linkify('Słuchaj: https://www.youtube.com/watch?v=29P7hxDy8gc&list=RD29P7hxDy8gc. Bilety (https://bilety.pl/x).'), [
    { text: 'Słuchaj: ' },
    { url: 'https://www.youtube.com/watch?v=29P7hxDy8gc&list=RD29P7hxDy8gc', label: 'youtube.com/watch?v=29P7hxDy8gc&list=…' },
    { text: '. Bilety (' }, { url: 'https://bilety.pl/x', label: 'bilety.pl/x' }, { text: ').' }]);
  assert.equal(linkify('https://pl.wikipedia.org/wiki/Hey_(zespół)')[0].url, 'https://pl.wikipedia.org/wiki/Hey_(zespół)');
  assert.deepEqual(linkify('bez adresow'), [{ text: 'bez adresow' }]);
  assert.deepEqual(linkify('javascript:alert(1) ftp://x'), [{ text: 'javascript:alert(1) ftp://x' }]);
  assert.deepEqual(linkify(null), []);
  assert.equal(shortUrl('https://www.facebook.com/buenosvampiros/'), 'facebook.com/buenosvampiros');
});

test('nazwa kalendarza: X-WR-CALNAME, eksport z Facebooka, inaczej nazwa pliku', () => {
  assert.equal(calendarName('BEGIN:VCALENDAR\nX-WR-CALNAME:Klub Pod Minogą\nEND:VCALENDAR', 'plik'), 'Klub Pod Minogą');
  assert.equal(calendarName('BEGIN:VCALENDAR\nPRODID:-//Facebook//NONSGML Facebook Events V1.0//EN\nEND:VCALENDAR', 'u100004746730811'), 'Facebook');
  assert.equal(calendarName('BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:e123@facebook.com\nEND:VEVENT\nEND:VCALENDAR', 'u1'), 'Facebook');
  assert.equal(calendarName('BEGIN:VCALENDAR\nEND:VCALENDAR', 'moj'), 'moj');
});

test('miasto: z nazwy wydarzenia (cale czesci), z punktu GEO; "Sopot" w zdaniu to nie miasto', () => {
  assert.equal(cityFromName('Czerwona Kartka za Aborcję - Warszawa'), 'Warszawa');
  assert.equal(cityFromName('KONTRAVOID + Spike Hellis / 13.12.26 / Pod Minogą, Poznań'), 'Poznań');
  assert.equal(cityFromName('Ścieżka Duchów BYDGOSZCZ 16 - 31.10.'), null);
  assert.equal(cityFromName('Wieczór w Sopot klubie'), null);
  assert.equal(cityNear(52.2297, 21.0122), 'Warszawa');
  assert.equal(cityNear(0, 0), null);
  const [e] = parseIcs('BEGIN:VEVENT\nUID:1@facebook.com\nDTSTART:20261013T170000Z\nSUMMARY:Buenos Vampiros - OLD SKULL - Warszawa\nLOCATION:Syrena\\, ul Wilcza 30\nEND:VEVENT', { displayTz: 'Europe/Warsaw' });
  assert.equal(e.city, 'Warszawa');
  assert.equal(e.cityKey, 'warsaw');
  assert.equal(e.venueName, 'Syrena');
  assert.equal(e.address, 'ul Wilcza 30');
});
