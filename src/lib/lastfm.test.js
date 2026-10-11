import test from 'node:test';
import assert from 'node:assert/strict';
import { topArtists, artistGenres, bioText, artistInfo, LastFmError } from './lastfm.js';

const fetchZ = (json, status = 200) => async () => ({ ok: status < 400, status, json: async () => json });

test('top artysci uzytkownika: nazwy, pusta lista, pojedynczy wynik jako obiekt', async () => {
  const a = await topArtists('ala', 5, { key: 'k', fetchFn: fetchZ({ topartists: { artist: [{ name: 'Kult' }, { name: ' ' }, { name: 'Hey' }] } }) });
  assert.deepEqual(a, ['Kult', 'Hey']);
  assert.deepEqual(await topArtists('ala', 5, { key: 'k', fetchFn: fetchZ({ topartists: { artist: { name: 'Kult' } } }) }), ['Kult']);
  assert.deepEqual(await topArtists('ala', 5, { key: 'k', fetchFn: fetchZ({ topartists: {} }) }), []);
});

test('bledy Last.fm: zly uzytkownik, zly klucz, klucz nie wycieka', async () => {
  await assert.rejects(topArtists('nikt', 5, { key: 'sekret-2', fetchFn: fetchZ({ error: 6, message: 'User not found sekret-2' }) }),
    (e) => e instanceof LastFmError && e.kod === 'BRAK' && !e.message.includes('sekret-2'));
  await assert.rejects(topArtists('ala', 5, { key: 'x', fetchFn: fetchZ({ error: 10, message: 'Invalid API key' }, 403) }),
    (e) => e.kod === 'KLUCZ');
  await assert.rejects(topArtists('ala', 5, { key: 'sekret-2', fetchFn: async (u) => { throw new Error(u); } }),
    (e) => e.kod === 'SIEC' && !e.message.includes('sekret-2'));
});

test('gatunki: prog popularnosci, odsiew smieci, limit; brak artysty to pusta lista, awaria to wyjatek', async () => {
  const tagi = [{ name: 'Rock', count: 100 }, { name: 'seen live', count: 90 }, { name: 'indie', count: 5 },
    ...Array.from({ length: 10 }, (_, i) => ({ name: `genre${i}`, count: 50 }))];
  const g = await artistGenres('X', { key: 'k', fetchFn: fetchZ({ toptags: { tag: tagi } }) });
  assert.equal(g.length, 8);
  assert.deepEqual(g.slice(0, 2), ['rock', 'genre0']);
  assert.deepEqual(await artistGenres('Nikt', { key: 'k', fetchFn: fetchZ({ error: 6, message: 'The artist you supplied could not be found' }) }), []);
  await assert.rejects(artistGenres('X', { key: 'k', fetchFn: fetchZ({ error: 8, message: 'Operation failed' }) }));
});

test('opis artysty: bez linku Last.fm, bez znacznikow, adres tylko z last.fm', async () => {
  assert.equal(bioText('Kovacs to holenderska piosenkarka. <a href="https://www.last.fm/music/Kovacs">Czytaj więcej na Last.fm</a>'),
    'Kovacs to holenderska piosenkarka.');
  const i = await artistInfo('Kovacs', 'pl', { key: 'k', fetchFn: fetchZ({ artist: { name: 'Kovacs', url: 'https://www.last.fm/music/Kovacs',
    stats: { listeners: '201234' }, similar: { artist: [{ name: 'Amy Winehouse' }] }, bio: { summary: 'Opis <b>x</b>.' } } }) });
  assert.deepEqual(i, { name: 'Kovacs', bio: 'Opis x.', listeners: 201234, similar: ['Amy Winehouse'], url: 'https://www.last.fm/music/Kovacs' });
  const obcy = await artistInfo('X', 'en', { key: 'k', fetchFn: fetchZ({ artist: { url: 'https://evil.example/x' } }) });
  assert.equal(obcy.url, null);
});
