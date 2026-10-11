import test from 'node:test';
import assert from 'node:assert/strict';
import { photonUrl, parsePhoton, suggestPlaces } from './adresy.js';

// Ksztalt odpowiedzi Photona (GeoJSON) - wg dokumentacji projektu; publiczny serwis jest stad niedostepny.
const PHOTON = { type: 'FeatureCollection', features: [
  { geometry: { type: 'Point', coordinates: [20.983, 52.191] }, properties: { osm_key: 'amenity', osm_value: 'nightclub', name: 'Klub Proxima',
    street: 'Żwirki i Wigury', housenumber: '99a', postcode: '02-089', city: 'Warszawa', countrycode: 'PL' } },
  { geometry: { type: 'Point', coordinates: [20.99, 52.2] }, properties: { osm_key: 'building', street: 'Żwirki i Wigury', housenumber: '99a',
    city: 'Warszawa', countrycode: 'PL' } },
  { geometry: { type: 'Point', coordinates: [19.9, 50.06] }, properties: { osm_key: 'highway', name: 'Żwirki i Wigury', city: 'Kraków', countrycode: 'PL' } },
  { geometry: { type: 'Point', coordinates: [20.983, 52.191] }, properties: { osm_key: 'amenity', name: 'Klub Proxima', street: 'Żwirki i Wigury',
    housenumber: '99a', city: 'Warszawa', countrycode: 'PL' } },
  { geometry: { type: 'Point', coordinates: [0, 0] }, properties: { osm_key: 'place', osm_value: 'city' } },
] };

test('adres zapytania: miasto dopisane do frazy i jako punkt odniesienia; bez lang', () => {
  const u = new URL(photonUrl('Żwirki 99', 'Warszawa'));
  assert.equal(u.searchParams.get('q'), 'Żwirki 99, Warszawa');
  assert.ok(u.searchParams.get('lat').startsWith('52.') && u.searchParams.get('lon').startsWith('21.'));
  assert.equal(u.searchParams.get('lang'), null);
  assert.equal(new URL(photonUrl('Proxima Warszawa', 'Warszawa')).searchParams.get('q'), 'Proxima Warszawa');
  assert.equal(new URL(photonUrl('Proxima', '')).searchParams.get('lat'), null);
  // polskie litery i bez znakow: miasto juz jest we frazie
  assert.equal(new URL(photonUrl('Piotrkowska 100 Łódź', 'Łódź')).searchParams.get('q'), 'Piotrkowska 100 Łódź');
  assert.equal(new URL(photonUrl('Piotrkowska 100 lodz', 'Łódź')).searchParams.get('q'), 'Piotrkowska 100 lodz');
  // nawias w nazwie miasta nie psuje wyrazenia
  assert.equal(new URL(photonUrl('Rynek', 'Nowa (Wieś)')).searchParams.get('q'), 'Rynek, Nowa (Wieś)');
});

test('podpowiedzi: miejsce z adresem, sam adres, tylko z wybranego miasta, bez powtorzen i bez samych miast', () => {
  const p = parsePhoton(PHOTON, 'Warszawa');
  assert.deepEqual(p.map((s) => s.label), ['Klub Proxima, Żwirki i Wigury 99a, Warszawa', 'Żwirki i Wigury 99a, Warszawa']);
  assert.deepEqual([p[0].name, p[0].address, p[0].city, p[0].lat, p[0].lon], ['Klub Proxima', 'Żwirki i Wigury 99a', 'Warszawa', 52.191, 20.983]);
  assert.equal(p[1].name, null);
  // bez miasta - wszystko (poza samymi miastami)
  assert.equal(parsePhoton(PHOTON, '').length, 3);
  assert.deepEqual(parsePhoton({}, 'Warszawa'), []);
});

test('zapytanie dopiero od 3 liter; blad serwisu = brak podpowiedzi', async () => {
  let n = 0;
  const f = async () => { n++; return { ok: true, json: async () => PHOTON }; };
  assert.deepEqual(await suggestPlaces('Kl', 'Warszawa', { fetchFn: f }), []);
  assert.equal(n, 0);
  assert.equal((await suggestPlaces('Klub', 'Warszawa', { fetchFn: f })).length, 2);
  assert.deepEqual(await suggestPlaces('Klub', 'Warszawa', { fetchFn: async () => ({ ok: false, json: async () => ({}) }) }), []);
});
