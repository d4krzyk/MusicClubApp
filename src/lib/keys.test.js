import test from 'node:test';
import assert from 'node:assert/strict';
import { cleanKey, keyShape, checkKey } from './keys.js';

const TM = 'AbCdEfGhIjKlMnOpQrStUvWxYz012345';
const LFM = '0123456789abcdef0123456789abcdef';
const odp = (json, status = 200) => async () => ({ ok: status < 400, status, json: async () => json });

test('wklejony tekst: sam klucz bez spacji, cudzyslowow i opisu', () => {
  assert.equal(cleanKey(`  ${TM}\n`), TM);
  assert.equal(cleanKey(`"${LFM}"`), LFM);
  assert.equal(cleanKey(`API key: ${LFM}`), LFM);
  assert.equal(cleanKey(`Consumer Key\t${TM}\tCreated 2026`), TM);
  assert.equal(cleanKey(''), '');
});

test('ksztalt klucza: Consumer Secret zamiast Consumer Key, zly ksztalt', () => {
  assert.equal(keyShape('tm', TM), 'ok');
  assert.equal(keyShape('tm', 'AbCdEfGhIjKlMnOp'), 'secret');
  assert.equal(keyShape('tm', 'abc'), 'dziwny');
  assert.equal(keyShape('lfm', LFM), 'ok');
  assert.equal(keyShape('lfm', TM), 'dziwny');
  assert.equal(keyShape('lfm', ''), null);
});

test('sprawdzenie: dziala, odrzucony, limit, siec', async () => {
  assert.equal(await checkKey('tm', TM, { fetchFn: odp({ _embedded: {} }) }), 'ok');
  assert.equal(await checkKey('tm', TM, { fetchFn: odp({ fault: { faultstring: 'Invalid ApiKey' } }, 401) }), 'zly');
  assert.equal(await checkKey('tm', TM, { fetchFn: odp({}, 429) }), 'limit');
  assert.equal(await checkKey('lfm', LFM, { fetchFn: odp({ error: 10, message: 'Invalid API key' }, 403) }), 'zly');
  assert.equal(await checkKey('lfm', LFM, { fetchFn: odp({ artists: {} }) }), 'ok');
  assert.equal(await checkKey('tm', TM, { fetchFn: odp({}, 403) }), 'zly');
  assert.equal(await checkKey('tm', TM, { fetchFn: async () => { throw new TypeError('Failed to fetch'); } }), 'siec');
  let adres;
  await checkKey('lfm', LFM, { fetchFn: async (u) => { adres = u; return { ok: true, status: 200, json: async () => ({}) }; } });
  assert.ok(adres.startsWith('https://ws.audioscrobbler.com/2.0/?method=chart.gettopartists') && adres.includes(`api_key=${LFM}`));
});
