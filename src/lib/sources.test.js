import test from 'node:test';
import assert from 'node:assert/strict';
import { readEvent, artistEvents, pathName, countryCode } from './bandsintown.js';
import { mergeAll, score, sameVenue } from './merge.js';
import { googleCalendarUrl, span, ics } from './calendar.js';
import { match, buildTaste } from './match.js';
import { syncBandsintown, addCalendarFile, addCalendarUrl, refreshCalendars, loadCalendars, removeCalendar } from './sync.js';
import { dbClear } from './store.js';

const odp = (json, status = 200) => ({ ok: status < 400, status, json: async () => json, text: async () => (typeof json === 'string' ? json : JSON.stringify(json)) });
const BIT = { id: '1007', datetime: '2026-11-20T20:00:00', url: 'https://www.bandsintown.com/e/1007', title: '',
  lineup: ['Kovacs', 'Support Duo'], offers: [{ type: 'Tickets', url: 'https://tix.example/1007' }],
  venue: { name: 'Klub Progresja', city: 'Warszawa', country: 'Poland', latitude: '52.2323', longitude: '20.9364', street_address: 'Fort Wola 22' } };
const tm = (o) => ({ id: 'TM1', source: 'tm', name: 'Kovacs', date: '2026-11-20', time: '20:00', venueName: 'Progresja', cityKey: 'warsaw',
  city: 'Warszawa', lat: 52.2321, lon: 20.9361, country: 'PL', performers: [{ name: 'Kovacs' }], ticketUrl: 'https://tm/1', imageUrl: null,
  description: null, address: null, ...o });

test('Bandsintown: wydarzenie, kraj z nazwy, sklad, bilety; polnoc = brak godziny', () => {
  const e = readEvent(BIT, 'Kovacs');
  assert.equal(e.id, 'bit:1007');
  assert.equal(e.name, 'Kovacs + Support Duo');
  assert.equal(e.time, '20:00');
  assert.equal(e.country, 'PL');
  assert.equal(e.cityKey, 'warsaw');
  assert.equal(e.ticketUrl, 'https://tix.example/1007');
  assert.equal(readEvent({ ...BIT, datetime: '2026-11-20T00:00:00' }, 'K').time, null);
  assert.equal(readEvent({ id: 'x' }, 'K'), null);
  assert.equal(countryCode('Czech Republic'), 'CZ');
  assert.equal(countryCode('Germany'), 'DE');
  assert.equal(pathName('AC/DC'), 'AC%252FDC');
});

test('Bandsintown: 404 = brak koncertow, 403 = zly klucz, klucz nie wycieka', async () => {
  assert.deepEqual(await artistEvents('X', { appId: 'a', fetchFn: async () => odp({}, 404) }), []);
  assert.deepEqual(await artistEvents('X', { appId: 'a', fetchFn: async () => odp({ errorMessage: '[NotFound]' }) }), []);
  await assert.rejects(artistEvents('X', { appId: 'a', fetchFn: async () => odp({}, 403) }), (e) => e.kod === 'KLUCZ');
  await assert.rejects(artistEvents('X', { appId: 'tajny', fetchFn: async (u) => { throw new Error(u); } }), (e) => !e.message.includes('tajny'));
});

test('laczenie: ten sam koncert z Bandsintown dopisany do Ticketmastera, braki uzupelnione', () => {
  const bit = readEvent(BIT, 'Kovacs');
  assert.ok(sameVenue('Klub Progresja', 'Progresja'));
  assert.ok(!sameVenue('Klub', 'Hala'));
  assert.ok(score(tm(), bit) >= 9);
  const [jeden, ...reszta] = mergeAll([tm({ address: null })], [bit]);
  assert.equal(reszta.length, 0);
  assert.equal(jeden.id, 'TM1');
  assert.equal(jeden.address, 'Fort Wola 22');
  assert.equal(jeden.ticketUrl, 'https://tm/1', 'nie nadpisuje');
  assert.deepEqual(jeden.sources.map((s) => s.kind), ['TICKETMASTER', 'BANDSINTOWN']);
});

test('laczenie: inny dzien, inna sala, inny wykonawca, godziny o > 3 h = osobne karty', () => {
  const bit = readEvent(BIT, 'Kovacs');
  assert.equal(score(tm({ date: '2026-11-21' }), bit), 0);
  assert.equal(score(tm({ venueName: 'Klub Studio', city: 'Kraków', cityKey: 'krakow', lat: 50.068, lon: 19.913 }), bit), 0);
  // ten sam wykonawca o tej samej godzinie w tym samym miescie, tylko sala nazwana inaczej - to ten sam koncert
  assert.ok(score(tm({ venueName: 'Fort Wola', lat: 52.21, lon: 21.0 }), bit) > 0);
  assert.equal(score(tm({ venueName: 'Stodoła', lat: 52.21, lon: 21.0, time: '17:30' }), bit), 0);
  assert.equal(score(tm({ name: 'Ktoś inny', performers: [{ name: 'Ktoś inny' }] }), bit), 0);
  assert.equal(score(tm({ time: '15:00' }), bit), 0);
  assert.equal(score(tm({ country: 'DE' }), bit), 0);
  assert.equal(mergeAll([tm({ date: '2026-11-21' })], [bit]).length, 2);
});

test('wydarzenie z kalendarza (Facebook) laczy sie z Ticketmasterem i podnosi je w "Dla ciebie"', () => {
  const fb = { id: 'ics:fb:1', source: 'ics', name: 'Kovacs | Warszawa', date: '2026-11-20', time: '20:00', venueName: 'Progresja',
    cityKey: 'warsaw', lat: null, lon: null, country: null, performers: [], sources: [{ kind: 'ICS', name: 'Facebook', url: null }] };
  const [e] = mergeAll([tm()], [], [fb]);
  assert.equal(e.inCalendar, true);
  const pusty = buildTaste([], {});
  assert.deepEqual(match(pusty, e), { score: 60, reasons: [{ kind: 'CALENDAR', value: 'Facebook' }] });
  assert.equal(match(pusty, tm()).score, 0);
});

test('kalendarz: link Google z UTC, calodniowy, plik .ics', () => {
  const e = tm({ startUtc: '2026-11-20T19:00:00Z', description: 'Opis', address: 'Fort Wola 22' });
  const u = new URL(googleCalendarUrl(e));
  assert.equal(u.searchParams.get('action'), 'TEMPLATE');
  assert.equal(u.searchParams.get('dates'), '20261120T190000Z/20261120T220000Z');
  assert.equal(u.searchParams.get('location'), 'Progresja, Fort Wola 22, Warszawa');
  assert.ok(u.searchParams.get('details').includes('https://tm/1'));
  assert.equal(new URL(googleCalendarUrl(tm({ time: null, startUtc: null }))).searchParams.get('dates'), '20261120/20261121');
  assert.equal(span(tm({ time: null, startUtc: null })).allDay, true);
  const plik = ics(e, new Date('2026-10-01T00:00:00Z'));
  assert.ok(plik.includes('DTSTART:20261120T190000Z') && plik.includes('GEO:52.2321;20.9361') && plik.includes('LOCATION:Progresja\\, Fort Wola 22\\, Warszawa'));
});

test('Bandsintown w synchronizacji: tylko wybrany kraj, blad wykonawcy nie przerywa, zly klucz przerywa', async () => {
  await dbClear();
  const pytania = [];
  const f = async (u) => {
    const a = decodeURIComponent(new URL(u).pathname.split('/')[2]);
    pytania.push(a);
    if (a === 'Awaria') return odp({}, 500);
    return odp([BIT, { ...BIT, id: '2', venue: { ...BIT.venue, country: 'Germany', city: 'Berlin' } }]);
  };
  const d = await syncBandsintown(['Kovacs', 'Awaria', 'Hey'], { appId: 'a', country: 'PL', fetchFn: f, pauseMs: 0 });
  assert.deepEqual(pytania, ['Kovacs', 'Awaria', 'Hey']);
  assert.deepEqual(d.events.map((e) => e.id), ['bit:1007']);
  await assert.rejects(syncBandsintown(['A', 'B'], { appId: 'x', fetchFn: async () => odp({}, 403), pauseMs: 0 }), (e) => e.kod === 'KLUCZ');
});

test('kalendarze: plik, subskrypcja, odswiezenie z bledem zostawia stare wydarzenia, usuniecie', async () => {
  await dbClear();
  const ICS = 'BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:1\r\nDTSTART:20261120T190000Z\r\nSUMMARY:Kovacs\r\nEND:VEVENT\r\nEND:VCALENDAR';
  await addCalendarFile(ICS, 'Facebook');
  await assert.rejects(addCalendarFile('nic', 'zly'));
  let lista = await addCalendarUrl('webcal://klub.example/cal.ics', 'Klub', { fetchFn: async (u) => { assert.equal(u, 'https://klub.example/cal.ics'); return odp(ICS); } });
  assert.equal(lista.length, 2);
  assert.equal(lista[1].events.length, 1);
  await assert.rejects(addCalendarUrl('https://x.example/', '', { fetchFn: async () => odp('<html>') }), /nie ma kalendarza/);
  lista = await refreshCalendars({ fetchFn: async () => odp('', 500) });
  assert.equal(lista[1].events.length, 1);
  assert.match(lista[1].error, /500/);
  lista = await removeCalendar(lista[0].id);
  assert.deepEqual((await loadCalendars()).map((k) => k.name), ['Klub']);
});

test('laczenie: punkty za godzine (do 1 h), krotka nazwa sali nie wystarcza, remis = pierwsze wydarzenie', () => {
  const bit = readEvent(BIT, 'Kovacs');
  assert.equal(score(tm(), bit), 12);
  assert.equal(score(tm({ time: '21:30' }), bit), 11);
  assert.ok(!sameVenue('Sala B90', 'B90'));
  assert.ok(sameVenue('Klub Ucho', 'Ucho Live'));
  const bezGodziny = { ...bit, time: null };
  const [a, b] = mergeAll([tm({ id: 'A' }), tm({ id: 'B', time: '21:30' })], [bezGodziny]);
  assert.equal(a.sources.length, 2, 'remis - wygrywa pierwsze');
  assert.equal(b.sources.length, 1);
});

test('kalendarz: koniec z pliku, gdy jest pozniej niz poczatek', () => {
  const s = span(tm({ startUtc: '2026-11-20T19:00:00Z', endUtc: '2026-11-20T23:30:00Z' }));
  assert.equal(new Date(s.end).toISOString(), '2026-11-20T23:30:00.000Z');
  assert.equal(span(tm({ startUtc: '2026-11-20T19:00:00Z', endUtc: '2026-11-20T18:00:00Z' })).end, Date.parse('2026-11-20T22:00:00Z'));
});

test('odswiezanie kalendarzy nie pobiera plikow (tylko subskrypcje)', async () => {
  await dbClear();
  const ICS = 'BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:1\r\nDTSTART:20261120T190000Z\r\nSUMMARY:K\r\nEND:VEVENT\r\nEND:VCALENDAR';
  await addCalendarFile(ICS, 'Plik');
  let n = 0;
  const [plik] = await refreshCalendars({ fetchFn: async () => { n++; return odp('', 500); } });
  assert.equal(n, 0);
  assert.equal(plik.error, null);
});

test('kalendarz zaimportowany wczesniej: nazwa pliku z Facebooka -> "Facebook", miasto z nazwy wydarzenia', async () => {
  await dbClear();
  const { dbSet } = await import('./store.js');
  await dbSet('calendars', [{ id: 'k', name: 'u100004746730811', kind: 'file', url: null, error: null, events: [
    { id: 'ics:k:1@facebook.com', name: 'Wiec - Warszawa', sourceUrl: 'https://www.facebook.com/events/1/', city: null, cityKey: null,
      sources: [{ kind: 'ICS', name: 'u100004746730811', url: null }] }] },
  { id: 'm', name: 'u12345678', kind: 'file', url: null, error: null, events: [{ id: 'ics:m:x', name: 'Coś', sourceUrl: null, cityKey: null, sources: [] }] }]);
  const [fb, inny] = await loadCalendars();
  assert.equal(fb.name, 'Facebook');
  assert.equal(fb.events[0].sources[0].name, 'Facebook');
  assert.equal(fb.events[0].cityKey, 'warsaw');
  assert.equal(inny.name, 'u12345678', 'bez sladu Facebooka nazwa zostaje');
});

test('Ticketmaster sam ze soba: ten sam koncert dwa razy (np. pakiet VIP) - jedna karta; inny dzien serii zostaje', () => {
  const lista = mergeAll([tm({ id: 'A', description: null }), tm({ id: 'B', name: 'Kovacs | Pakiety VIP', description: 'Pakiet VIP z wejściem na soundcheck.' }),
    tm({ id: 'C', date: '2026-11-21' })]);
  assert.deepEqual(lista.map((e) => e.id), ['A', 'C']);
  assert.equal(lista[0].description, 'Pakiet VIP z wejściem na soundcheck.');
});

test('Facebook bez miasta w miejscu laczy sie z Ticketmasterem; dluzszy opis z Facebooka zastepuje krotki', () => {
  const fb = { id: 'ics:fb:1', source: 'ics', name: 'Buenos Vampiros (Argentyna), Sofia Night Club - OLD SKULL - post punk', date: '2026-11-20',
    time: '20:00', venueName: 'Syrena', address: 'ul Wilcza 30', city: null, cityKey: null, lat: null, lon: null, country: null, performers: [],
    candidates: ['Buenos Vampiros', 'Sofia Night Club', 'OLD SKULL'], description: 'Buenos Vampiros to trzy dziewczyny i chłopak z Mar del Plata. '.repeat(5),
    textGenres: ['post punk'], genreTags: ['postpunk'], sources: [{ kind: 'ICS', name: 'Facebook', url: 'https://www.facebook.com/events/1/' }] };
  const t = tm({ id: 'T', name: 'Buenos Vampiros', venueName: 'Klub Syrena', performers: [{ name: 'Buenos Vampiros' }], description: 'Koncert.', lat: null, lon: null });
  const [e, ...reszta] = mergeAll([t], [], [fb]);
  assert.equal(reszta.length, 0);
  assert.equal(e.id, 'T');
  assert.ok(e.description.startsWith('Buenos Vampiros to trzy'));
  assert.deepEqual(e.genreTags, ['postpunk']);
  assert.equal(e.address, 'ul Wilcza 30');
  assert.equal(e.inCalendar, true);
  // ta sama godzina i wykonawca, ale nieznana sala i miasto po stronie Facebooka - tez jeden koncert
  assert.equal(mergeAll([t], [], [{ ...fb, venueName: null, address: null }]).length, 1);
  // inny dzien - osobno
  assert.equal(mergeAll([t], [], [{ ...fb, date: '2026-11-21' }]).length, 2);
});

test('udzial: biore udzial / zainteresowany / nic; kalendarz z Facebooka; zdjecie wydarzenia z kalendarza', async () => {
  const { statusOf, setStatus, mine } = await import('./udzial.js');
  const t1 = tm({ id: 'T1' });
  const fbGoing = { id: 'ics:k:1', source: 'ics', name: 'FB', date: '2026-11-22', partstat: 'GOING', performers: [] };
  const fbInt = { id: 'ics:k:2', source: 'ics', name: 'FB2', date: '2026-11-23', partstat: null, performers: [] };
  let s = { saved: {}, udzial: {} };
  assert.equal(statusOf(t1, s), null);
  assert.equal(statusOf(fbGoing, s), 'GOING');
  assert.equal(statusOf(fbInt, s), 'INTERESTED');
  s = { ...s, ...setStatus(s, t1, 'GOING') };
  assert.equal(statusOf(t1, s), 'GOING');
  s = { ...s, ...setStatus(s, fbInt, null) };
  assert.equal(statusOf(fbInt, s), null, 'zdjete z kalendarza zostaje zdjete');
  assert.deepEqual(mine([t1, fbGoing, fbInt], s, 'GOING').map((e) => e.id), ['T1', 'ics:k:1']);
  assert.deepEqual(mine([t1, fbGoing, fbInt], s, 'INTERESTED').map((e) => e.id), []);
  // stary zapis (gwiazdka) = zainteresowany; kopia zostaje, gdy wydarzenie zniknie ze zrodla
  assert.deepEqual(mine([], { saved: { X: { id: 'X', name: 'Stare' } } }, 'INTERESTED').map((e) => e.udzial), ['INTERESTED']);
  // status ustawiony na wydarzeniu, ktore potem polaczylo sie z innym (mergedIds), nie ginie
  assert.equal(statusOf({ ...tm({ id: 'T9' }), mergedIds: ['ics:k:1'] }, { udzial: { 'ics:k:1': 'INTERESTED' } }), 'INTERESTED');
  s = { ...s, ...setStatus(s, t1, null) };
  assert.equal(statusOf(t1, s), null);
  assert.equal(s.udzial.T1, undefined);
});

test('PARTSTAT z kalendarza: ACCEPTED = biore udzial, TENTATIVE = zainteresowany; eksport wielu wydarzen', async () => {
  const { parseIcs } = await import('./ics.js');
  const { icsMany } = await import('./calendar.js');
  const [a, b, c] = parseIcs(['BEGIN:VEVENT', 'UID:1', 'DTSTART:20261201T200000Z', 'SUMMARY:A', 'PARTSTAT:ACCEPTED', 'END:VEVENT',
    'BEGIN:VEVENT', 'UID:2', 'DTSTART:20261202T200000Z', 'SUMMARY:B', 'ATTENDEE;PARTSTAT=TENTATIVE;CN=Ja:mailto:x@y', 'END:VEVENT',
    'BEGIN:VEVENT', 'UID:3', 'DTSTART:20261203T200000Z', 'SUMMARY:C', 'END:VEVENT'].join('\n'), { displayTz: 'UTC' });
  assert.deepEqual([a.partstat, b.partstat, c.partstat], ['GOING', 'INTERESTED', null]);
  const plik = icsMany([{ ...tm({ startUtc: '2026-11-20T19:00:00Z' }), udzial: 'GOING' }, { ...tm({ id: 'X', status: 'CANCELLED' }), udzial: 'INTERESTED' }], { name: 'Biorę udział' });
  assert.equal((plik.match(/BEGIN:VEVENT/g) ?? []).length, 2);
  assert.ok(plik.includes('X-WR-CALNAME:Biorę udział') && plik.includes('STATUS:CONFIRMED') && plik.includes('STATUS:CANCELLED'));
  assert.ok(!plik.includes('STATUS:TENTATIVE'), 'odwolane ma tylko STATUS:CANCELLED');
});

test('udzial: stary zapis gwiazdka na wydarzeniu, ktore wciaz jest w zrodle = zainteresowany', async () => {
  const { statusOf } = await import('./udzial.js');
  assert.equal(statusOf(tm({ id: 'T1' }), { saved: { T1: {} } }), 'INTERESTED');
});
