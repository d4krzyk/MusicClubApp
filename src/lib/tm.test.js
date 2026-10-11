import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { readEvent, fetchPage, fetchCountry, windows, image, links, ApiError } from './ticketmaster.js';

const odpowiedz = JSON.parse(readFileSync(new URL('./fixtures/wydarzenia-pl.json', import.meta.url), 'utf8'));
const wszystkie = Object.fromEntries((odpowiedz._embedded.events).map((e) => [e.id, readEvent(e)]).filter(([, v]) => v));
const fetchZ = (json, status = 200) => async () => ({ ok: status < 400, status, json: async () => json });

test('czyta nazwe, termin, miejsce, sklad i opis z prawdziwej odpowiedzi', () => {
  const a = wszystkie.vvG1zZ9KSAmity;
  assert.equal(a.name, 'The Amity Affliction | Support: Silent Planet, Varials, Orthodox');
  assert.equal(a.date, '2026-09-29');
  assert.equal(a.time, '19:00');
  assert.equal(a.status, 'SCHEDULED');
  assert.equal(a.venueName, 'Progresja');
  assert.equal(a.city, 'Warszawa', 'polska nazwa zamiast Warsaw');
  assert.equal(a.cityKey, 'warsaw');
  assert.equal(a.address, 'Fort Wola 22');
  assert.equal(a.lat, 52.2321);
  assert.equal(a.lon, 20.9361);
  assert.equal(a.genre, 'Rock');
  assert.equal(a.subGenre, 'Hard Rock');
  assert.equal(a.ticketUrl, 'https://www.ticketmaster.pl/event/vvG1zZ9KSAmity');
  assert.ok(a.description.startsWith('Australijczycy wracają do Polski'));
  assert.deepEqual(a.performers.map((p) => p.name), ['The Amity Affliction', 'Silent Planet', 'Varials', 'Orthodox']);
});

test('zdjecie 16:9: duze na strone, mniejsze na liste; grafika zastepcza tylko w ostatecznosci', () => {
  assert.ok(wszystkie.vvG1zZ9KSAmity.imageUrl.endsWith('_16_9_1024x576.jpg'));
  assert.ok(wszystkie.vvG1zZ9KSAmity.thumbUrl.endsWith('_16_9_640x360.jpg'));
  assert.ok(!wszystkie.vvG1zZ9KSAmity.imageUrl.includes('fallback'));
  assert.ok(wszystkie.Z698xZbpZ17Can2.imageUrl.includes('fallback-classical'));
  assert.equal(image([], 640), null);
});

test('pomija wydarzenia testowe i bez daty; godzina do ustalenia jest pusta; miasto bez spacji', () => {
  assert.ok(!('vvG1zZ9TESTEVT' in wszystkie) && !('vvG1zZ9KSTBA' in wszystkie));
  assert.equal(Object.keys(wszystkie).length, 10);
  assert.equal(wszystkie.vvG1zZ9KSMroz.time, null);
  assert.equal(wszystkie.vvG1zZ9KSMroz.city, 'Łódź');
  assert.equal(wszystkie.vvG1zZ9KSMroz.cityKey, 'lodz');
});

test('odwolany koncert jest oznaczony; "Undefined" to nie gatunek; opis z description', () => {
  assert.equal(wszystkie.vvG1zZ9KSJack.status, 'CANCELLED');
  assert.equal(wszystkie.vvG1zZ9KSGlob.genre, null);
  assert.equal(wszystkie.vvG1zZ9KSGlob.subGenre, null);
  assert.equal(wszystkie.vvG1zZ9KSStra.description, 'Grabaż i spółka w poznańskiej Tamie.');
});

test('zapytanie: muzyka w kraju, 200 na strone, klucz w adresie; strony', async () => {
  let adres;
  const f = async (u) => { adres = u; return { ok: true, status: 200, json: async () => odpowiedz }; };
  const p = await fetchPage({ key: 'k', country: 'PL', from: '2026-09-27T22:00:00Z', to: '2026-10-31T23:00:00Z', page: 3, fetchFn: f });
  for (const s of ['countryCode=PL', 'classificationName=music', 'size=200', 'page=3', 'apikey=k', 'sort=date%2Casc',
    'startDateTime=2026-09-27T22%3A00%3A00Z']) assert.ok(adres.includes(s), s);
  assert.equal(p.events.length, 10);
  assert.equal(p.totalElements, 12);
});

test('zly klucz: komunikat bez klucza; siec: tez bez klucza', async () => {
  await assert.rejects(fetchPage({ key: 'sekret-1', country: 'PL', from: 'a', to: 'b', page: 0,
    fetchFn: fetchZ({ fault: { faultstring: 'Invalid ApiKey sekret-1' } }, 401) }),
  (e) => e instanceof ApiError && e.kod === 'KLUCZ' && e.message.includes('401') && e.message.includes('Invalid ApiKey') && !e.message.includes('sekret-1'));
  await assert.rejects(fetchPage({ key: 'sekret-1', country: 'PL', from: 'a', to: 'b', page: 0,
    fetchFn: async (u) => { throw new Error(`failed ${u}`); } }),
  (e) => e.kod === 'SIEC' && !e.message.includes('sekret-1'));
  await assert.rejects(fetchPage({ key: 'k', country: 'PL', from: 'a', to: 'b', page: 0, fetchFn: fetchZ({}, 429) }),
    (e) => e.kod === 'LIMIT');
});

test('okna po miesiacu pokrywaja caly zakres bez dziur', () => {
  const o = windows(new Date('2026-10-11T10:00:00Z'), 3);
  assert.equal(o.length, 3);
  assert.equal(o[0].from, '2026-10-11T00:00:00Z');
  assert.equal(o[0].to, '2026-10-31T23:59:59Z');
  assert.equal(o[1].from, '2026-11-01T00:00:00Z');
  assert.equal(o[2].to, '2026-12-31T23:59:59Z'.replace('12-31', '12-31'));
});

test('fetchCountry przechodzi okna i strony, sklada bez duplikatow; blad przerywa', async () => {
  const wywolania = [];
  const f = async (u) => {
    wywolania.push(u);
    const strona = Number(new URL(u).searchParams.get('page'));
    return { ok: true, status: 200, json: async () => ({ ...odpowiedz, page: { number: strona, totalPages: 2, totalElements: 20 } }) };
  };
  const postep = [];
  const { events } = await fetchCountry({ key: 'k', country: 'PL', now: new Date('2026-10-11T00:00:00Z'), months: 2, fetchFn: f,
    pauseMs: 0, onProgress: (p) => postep.push(p.done) });
  assert.equal(wywolania.length, 4); // 2 okna x 2 strony
  assert.equal(events.length, 10);   // te same wydarzenia w kazdym oknie - bez duplikatow
  assert.deepEqual(postep, [1, 2, 3, 4]);
  await assert.rejects(fetchCountry({ key: 'k', country: 'PL', months: 2, fetchFn: fetchZ({}, 500), pauseMs: 0 }));
});

test('linki wykonawcy: tylko http(s) z hostem, bez danych logowania', () => {
  const l = links({ spotify: [{ url: 'https://open.spotify.com/artist/1' }], homepage: [{ url: 'javascript:alert(1)' }],
    facebook: [{ url: 'https://u:p@facebook.com/x' }], youtube: [{ url: 'http://youtube.com/c' }], nieznany: [{ url: 'https://x.pl' }] });
  assert.deepEqual(l, { SPOTIFY: 'https://open.spotify.com/artist/1', YOUTUBE: 'http://youtube.com/c' });
});

test('od organizatora: ceny, wiek, sprzedaz, uwagi bez formulek', () => {
  const e = readEvent({ id: 'X', name: 'N', dates: { start: { localDate: '2026-11-01' } },
    promoter: { name: 'Live Nation' },
    priceRanges: [{ type: 'standard including fees', min: 50, max: 90, currency: 'PLN' }, { type: 'standard', min: 60, max: 120, currency: 'PLN' }],
    ageRestrictions: { legalAgeEnforced: true },
    sales: { public: { startDateTime: '2026-10-15T09:00:00Z', startTBD: false } },
    pleaseNote: 'Wejście od 18:00. Service fee is included in the ticket price.' });
  assert.deepEqual(e.organizer, { pleaseNote: 'Wejście od 18:00.', promoter: 'Live Nation', priceMin: 60, priceMax: 120,
    priceCurrency: 'PLN', ageRestricted: true, salesStart: '2026-10-15T09:00:00Z', accessibility: null });
});

test('zdjecie: wlasne wygrywa z grafika zastepcza, nawet mniejsze; bez wlasnego - zastepcza', () => {
  const fb = { ratio: '16_9', width: 2048, fallback: true, url: 'https://x/fallback.jpg' };
  const wlasne = { ratio: '16_9', width: 640, url: 'https://x/wlasne.jpg' };
  assert.equal(image([fb, wlasne], 1024), 'https://x/wlasne.jpg');
  assert.equal(image([fb], 1024), 'https://x/fallback.jpg');
  assert.equal(image([{ ratio: '4_3', width: 100, url: 'https://x/inne.jpg' }], 1024), 'https://x/inne.jpg');
});

test('godzina do ustalenia (timeTBA / noSpecificTime) to brak godziny, a nie polnoc', () => {
  const baza = (start) => readEvent({ id: 'X', name: 'N', dates: { start } });
  assert.equal(baza({ localDate: '2026-11-01', localTime: '00:00:00', timeTBA: true }).time, null);
  assert.equal(baza({ localDate: '2026-11-01', localTime: '00:00:00', noSpecificTime: true }).time, null);
  assert.equal(baza({ localDate: '2026-11-01', localTime: '20:30:00' }).time, '20:30');
});
