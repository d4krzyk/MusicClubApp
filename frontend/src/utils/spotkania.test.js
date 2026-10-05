import test from 'node:test';
import assert from 'node:assert/strict';
import {
  domyslneGodziny, doPol, icsTekst, koniecPoPolnocy, minutDo, plikIcs, sprawdzSpotkanie, stanSpotkania, zPol,
} from './spotkania.js';

const S = (od, doK, extra = {}) => ({ startsAt: od, endsAt: doK, ...extra });

test('stan: przed, trwa (od wlacznie), po (do wlacznie), odwolane wygrywa', () => {
  const s = S('2026-10-10T18:00:00Z', '2026-10-10T19:00:00Z');
  assert.equal(stanSpotkania(s, new Date('2026-10-10T17:59:59Z')), 'PRZED');
  assert.equal(stanSpotkania(s, new Date('2026-10-10T18:00:00Z')), 'TRWA');
  assert.equal(stanSpotkania(s, new Date('2026-10-10T18:59:59Z')), 'TRWA');
  assert.equal(stanSpotkania(s, new Date('2026-10-10T19:00:00Z')), 'PO');
  assert.equal(stanSpotkania({ ...s, cancelled: true }, new Date('2026-10-10T18:30:00Z')), 'ODWOLANE');
});

test('minuty do poczatku - w gore; po starcie null', () => {
  const s = S('2026-10-10T18:00:00Z', '2026-10-10T19:00:00Z');
  assert.equal(minutDo(s, new Date('2026-10-10T17:30:30Z')), 30);
  assert.equal(minutDo(s, new Date('2026-10-10T17:59:01Z')), 1);
  assert.equal(minutDo(s, new Date('2026-10-10T18:00:00Z')), null);
});

test('domyslne godziny: pelna polgodzina co najmniej 30 min naprzod, godzina trwania', () => {
  for (const [teraz, oczekiwane] of [
    ['2026-10-10T10:00:00', '10:30'], ['2026-10-10T10:01:00', '11:00'], ['2026-10-10T10:29:00', '11:00'],
    ['2026-10-10T10:31:00', '11:30'], ['2026-10-10T23:45:00', '00:30'],
  ]) {
    const { od, do: doK } = domyslneGodziny(new Date(teraz));
    assert.equal(doPol(od).godzina, oczekiwane, teraz);
    assert.equal(doK.getTime() - od.getTime(), 3600000);
    assert.ok(od.getTime() - new Date(teraz).getTime() >= 30 * 60000);
  }
});

test('pola formularza w obie strony; niepelne = null', () => {
  const d = zPol('2026-10-10', '18:30');
  assert.deepEqual(doPol(d), { data: '2026-10-10', godzina: '18:30' });
  assert.equal(zPol('2026-10-10', ''), null);
  assert.equal(zPol('', '18:30'), null);
  assert.equal(zPol('xx', '18:30'), null);
});

test('przejscie przez polnoc: "do" przed "od" to nastepny dzien', () => {
  const od = zPol('2026-10-10', '23:00');
  const doK = koniecPoPolnocy(od, zPol('2026-10-10', '01:00'));
  assert.equal(doK.getTime() - od.getTime(), 2 * 3600000);
  const zwykle = zPol('2026-10-10', '23:30');
  assert.equal(koniecPoPolnocy(od, zwykle), zwykle);
});

test('walidacja: miejsce, przeszlosc (5 min tolerancji), za daleko, kolejnosc, doba', () => {
  const teraz = new Date('2026-10-10T12:00:00');
  const ok = { miejsce: 'Pod klubem', od: new Date('2026-10-10T18:00:00'), doK: new Date('2026-10-10T19:00:00') };
  assert.deepEqual(sprawdzSpotkanie(ok, teraz), {});
  assert.equal(sprawdzSpotkanie({ ...ok, miejsce: '  ' }, teraz).miejsce, 'meetings.errors.placeRequired');
  assert.equal(sprawdzSpotkanie({ ...ok, miejsce: 'x'.repeat(101) }, teraz).miejsce, 'meetings.errors.placeTooLong');
  assert.equal(sprawdzSpotkanie({ ...ok, notatka: 'x'.repeat(201) }, teraz).notatka, 'meetings.errors.noteTooLong');
  assert.equal(sprawdzSpotkanie({ ...ok, od: new Date('2026-10-10T11:56:00') }, teraz).od, undefined);
  assert.equal(sprawdzSpotkanie({ ...ok, od: new Date('2026-10-10T11:54:00'), doK: new Date('2026-10-10T13:00:00') }, teraz).od,
    'meetings.errors.startInPast');
  assert.equal(sprawdzSpotkanie({ ...ok, od: new Date('2026-12-10T18:00:00'), doK: new Date('2026-12-10T19:00:00') }, teraz).od,
    'meetings.errors.startTooFar');
  assert.equal(sprawdzSpotkanie({ ...ok, doK: ok.od }, teraz).do, 'meetings.errors.endBeforeStart');
  assert.equal(sprawdzSpotkanie({ ...ok, doK: new Date('2026-10-11T18:01:00') }, teraz).do, 'meetings.errors.tooLong');
  assert.equal(sprawdzSpotkanie({ ...ok, od: null }, teraz).od, 'meetings.errors.startRequired');
});

test('ics: znaki specjalne w tekscie', () => {
  assert.equal(icsTekst('Pod klubem; brama 2, obok\nkiosku \\ tu'), 'Pod klubem\\; brama 2\\, obok\\nkiosku \\\\ tu');
});

test('ics: czasy w UTC, alarm, GEO, odwolane, zawijanie dlugich linii', () => {
  const plik = plikIcs({
    id: 7, place: 'Pod Progresją, główne wejście', note: 'Mam bilety, czekam przy schodach. '.repeat(4),
    startsAt: '2026-10-10T16:30:00Z', endsAt: '2026-10-10T17:30:00Z', latitude: 52.2236, longitude: 20.9617,
    remindMinutes: 30, cancelled: true,
  }, { teraz: new Date('2026-10-05T10:00:00Z') });
  assert.ok(plik.startsWith('BEGIN:VCALENDAR\r\n'));
  assert.ok(plik.endsWith('END:VCALENDAR\r\n'));
  assert.match(plik, /DTSTART:20261010T163000Z\r\n/);
  assert.match(plik, /DTEND:20261010T173000Z\r\n/);
  assert.match(plik, /UID:spotkanie-7@musicclub\r\n/);
  assert.match(plik, /GEO:52.2236;20.9617\r\n/);
  assert.match(plik, /TRIGGER:-PT30M\r\n/);
  assert.match(plik, /STATUS:CANCELLED\r\n/);
  // zadna fizyczna linia nie przekracza 75 bajtow, a po zlozeniu opis jest caly
  for (const linia of plik.split('\r\n')) {
    assert.ok(new TextEncoder().encode(linia).length <= 75, linia);
  }
  const zlozony = plik.replace(/\r\n /g, '');
  assert.ok(zlozony.includes('DESCRIPTION:Mam bilety\\, czekam przy schodach. Mam'));
  // bez przypomnienia - bez alarmu
  assert.ok(!plikIcs({ id: 1, place: 'X', startsAt: '2026-10-10T16:30:00Z', endsAt: '2026-10-10T17:30:00Z', remindMinutes: 0 })
    .includes('VALARM'));
});
