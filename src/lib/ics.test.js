import test from 'node:test';
import assert from 'node:assert/strict';
import { parseIcs, parseDate, unfold, parseLine, splitLocation, normalizeFeedUrl, wallToUtc } from './ics.js';

// Ksztalt eksportu wydarzenia z Facebooka ("Dodaj do kalendarza") - czasy w UTC, URL wydarzenia, zawinieta linia.
const FACEBOOK = [
  'BEGIN:VCALENDAR', 'PRODID:-//Facebook//NONSGML Facebook Events V1.0//EN', 'VERSION:2.0', 'METHOD:PUBLISH',
  'BEGIN:VEVENT', 'DTSTAMP:20261001T120000Z', 'DTSTART:20261120T190000Z', 'DTEND:20261120T220000Z',
  'UID:e123456@facebook.com', 'SUMMARY:Kovacs w Warszawie\\, Progresja', 'LOCATION:Progresja\\, Fort Wola 22\\, 01-258 Warszawa',
  'URL:https://www.facebook.com/events/123456/', 'DESCRIPTION:Kovacs wraca do Polski z nową płytą.\\n\\nBilety w kasie klu',
  ' bu.', 'ORGANIZER;CN="Progresja: klub":MAILTO:noreply@facebookmail.com', 'STATUS:CONFIRMED', 'END:VEVENT',
  'BEGIN:VEVENT', 'DTSTART;VALUE=DATE:20261201', 'SUMMARY:Festiwal zimowy', 'UID:e2@facebook.com', 'STATUS:CANCELLED',
  'BEGIN:VALARM', 'TRIGGER:-PT15M', 'DESCRIPTION:Przypomnienie z alarmu', 'END:VALARM', 'END:VEVENT',
  'BEGIN:VEVENT', 'DTSTART;TZID=Europe/Warsaw:20260329T023000', 'SUMMARY:Noc zmiany czasu', 'UID:e3', 'GEO:52.2321;20.9361', 'END:VEVENT',
  'BEGIN:VEVENT', 'SUMMARY:Bez daty', 'UID:e4', 'END:VEVENT',
  'END:VCALENDAR'].join('\r\n');

test('Facebook: czas UTC jako godzina w Polsce, miejsce rozbite, opis z zawinietej linii, link', () => {
  const [kovacs, fest, noc] = parseIcs(FACEBOOK, { sourceId: 'fb', sourceName: 'Facebook', displayTz: 'Europe/Warsaw' });
  assert.equal(kovacs.name, 'Kovacs w Warszawie, Progresja');
  assert.equal(kovacs.date, '2026-11-20');
  assert.equal(kovacs.time, '20:00');
  assert.equal(kovacs.startUtc, '2026-11-20T19:00:00.000Z');
  assert.equal(kovacs.endUtc, '2026-11-20T22:00:00.000Z');
  assert.equal(kovacs.venueName, 'Progresja');
  assert.equal(kovacs.address, 'Fort Wola 22');
  assert.equal(kovacs.city, 'Warszawa');
  assert.equal(kovacs.cityKey, 'warsaw');
  assert.equal(kovacs.description, 'Kovacs wraca do Polski z nową płytą.\n\nBilety w kasie klubu.');
  assert.equal(kovacs.sourceUrl, 'https://www.facebook.com/events/123456/');
  assert.equal(kovacs.id, 'ics:fb:e123456@facebook.com');
  assert.deepEqual(kovacs.sources, [{ kind: 'ICS', name: 'Facebook', url: 'https://www.facebook.com/events/123456/' }]);
  assert.equal(fest.time, null);
  assert.equal(fest.date, '2026-12-01');
  assert.equal(fest.status, 'CANCELLED');
  assert.equal(fest.description, null, 'opis alarmu to nie opis wydarzenia');
  assert.equal(noc.lat, 52.2321);
  assert.equal(noc.timezone, 'Europe/Warsaw');
});

test('bez daty i bez nazwy - pomijane; smieci - pusta lista', () => {
  assert.equal(parseIcs(FACEBOOK, { displayTz: 'UTC' }).length, 3);
  assert.deepEqual(parseIcs('to nie jest kalendarz'), []);
});

test('daty: calodniowa, UTC, strefa, Windows, bez strefy; zmiana czasu', () => {
  assert.deepEqual(parseDate('20261201', { VALUE: 'DATE' }, 'UTC'), { date: '2026-12-01', time: null, utc: null });
  assert.equal(parseDate('20261231T233000Z', {}, 'Europe/Warsaw').date, '2027-01-01'); // po polnocy w Polsce
  assert.equal(parseDate('20260715T200000', { TZID: 'Europe/Warsaw' }, 'UTC').utc, '2026-07-15T18:00:00.000Z'); // lato +2
  assert.equal(parseDate('20260115T200000', { TZID: 'Central European Standard Time' }, 'UTC').utc, '2026-01-15T19:00:00.000Z');
  assert.deepEqual(parseDate('20260115T200000', {}, 'UTC'), { date: '2026-01-15', time: '20:00', utc: null, tz: null });
  assert.equal(parseDate('20260115T200000', { TZID: 'Nie/Ma' }, 'UTC').utc, null);
  assert.equal(parseDate('2026-01-15', {}, 'UTC'), null);
  assert.equal(new Date(wallToUtc(2026, 10, 25, 2, 30, 0, 'Europe/Warsaw')).toISOString().slice(11, 16), '01:30');
});

test('linie: zawijanie, cudzyslow z dwukropkiem w parametrze', () => {
  assert.deepEqual(unfold('A:1\r\n 2\r\nB:3'), ['A:12', 'B:3']);
  assert.deepEqual(parseLine('ORGANIZER;CN="A: B":MAILTO:x@y'), { name: 'ORGANIZER', params: { CN: 'A: B' }, value: 'MAILTO:x@y' });
  assert.equal(parseLine('bez dwukropka'), null);
});

test('miejsce: miasto z kodem pocztowym, samo miasto, nieznane miasto', () => {
  assert.deepEqual(splitLocation('Klub Studio, ul. Budryka 4, 30-072 Kraków, Polska'), { venueName: 'Klub Studio', address: 'ul. Budryka 4', city: 'Kraków' });
  assert.deepEqual(splitLocation('Gdańsk'), { venueName: null, address: null, city: 'Gdańsk' });
  assert.deepEqual(splitLocation('Jakiś bar'), { venueName: 'Jakiś bar', address: null, city: null });
  assert.deepEqual(splitLocation(null), { venueName: null, address: null, city: null });
});

test('adres subskrypcji: webcal na https, tylko http(s)', () => {
  assert.equal(normalizeFeedUrl(' webcal://example.com/cal.ics '), 'https://example.com/cal.ics');
  assert.equal(normalizeFeedUrl('javascript:alert(1)'), null);
  assert.equal(normalizeFeedUrl(''), null);
});

test('strefa: czas tuz przed zmiana na letni liczony dwoma przyblizeniami; zla wspolrzedna GEO; DATE z godzina', () => {
  assert.equal(new Date(wallToUtc(2026, 3, 29, 1, 30, 0, 'Europe/Warsaw')).toISOString(), '2026-03-29T00:30:00.000Z');
  const [e] = parseIcs('BEGIN:VEVENT\nUID:g\nDTSTART:20261201T200000Z\nSUMMARY:X\nGEO:95;20\nEND:VEVENT', { displayTz: 'UTC' });
  assert.equal(e.lat, null);
  assert.equal(parseDate('20261201T000000', { VALUE: 'DATE' }, 'UTC').time, null);
});
