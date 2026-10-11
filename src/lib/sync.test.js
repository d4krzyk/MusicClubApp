import test from 'node:test';
import assert from 'node:assert/strict';
import { syncTags, syncEvents, loadEvents, importLastFm, performerNames } from './sync.js';
import { dbSet, dbClear } from './store.js';

const odp = (json, status = 200) => ({ ok: status < 400, status, json: async () => json });

test('gatunki: pyta tylko o nowych, zapamietuje, awaria nie zapisuje pustych tagow', async () => {
  await dbClear();
  const pytania = [];
  const f = async (u) => {
    const a = new URL(u).searchParams.get('artist');
    pytania.push(a);
    if (a === 'Awaria') return odp({ error: 8, message: 'Operation failed' });
    return odp({ toptags: { tag: [{ name: 'rock', count: 100 }] } });
  };
  const r1 = await syncTags(['Kult', 'Awaria', 'kult', 'Hey'], { lfmKey: 'k', fetchFn: f, pauseMs: 0 });
  assert.deepEqual(pytania, ['Kult', 'Awaria', 'Hey']);
  assert.equal(r1.failed, 1);
  assert.deepEqual(r1.tags.kult, ['rock']);
  assert.ok(!('awaria' in r1.tags), 'awaria Last.fm nie moze zostawic pustych tagow na 60 dni');
  pytania.length = 0;
  await syncTags(['Kult', 'Awaria', 'Hey'], { lfmKey: 'k', fetchFn: f, pauseMs: 0 });
  assert.deepEqual(pytania, ['Awaria']); // reszta jest w pamieci
  // po 61 dniach pytamy znowu
  pytania.length = 0;
  await syncTags(['Kult'], { lfmKey: 'k', fetchFn: f, pauseMs: 0, now: Date.now() + 61 * 24 * 3600 * 1000 });
  assert.deepEqual(pytania, ['Kult']);
});

test('gatunki: limit na przebieg, brak klucza = brak zapytan, zly klucz konczy odpytywanie', async () => {
  await dbClear();
  let n = 0;
  const f = async () => { n++; return odp({ toptags: { tag: [] } }); };
  await syncTags(['A1', 'A2', 'A3', 'A4'], { lfmKey: 'k', fetchFn: f, pauseMs: 0, limit: 2 });
  assert.equal(n, 2);
  n = 0;
  await syncTags(['B1'], { lfmKey: '', fetchFn: f, pauseMs: 0 });
  assert.equal(n, 0);
  n = 0;
  const zly = async () => { n++; return odp({ error: 10, message: 'Invalid API key' }, 403); };
  const r = await syncTags(['C1', 'C2', 'C3'], { lfmKey: 'x', fetchFn: zly, pauseMs: 0 });
  assert.equal(n, 1);
  assert.equal(r.failed, 1);
});

test('wydarzenia: zapis kopii; blad zostawia stara kopie', async () => {
  await dbClear();
  const ok = async () => odp({ _embedded: { events: [{ id: 'E1', name: 'Kult', dates: { start: { localDate: '2026-11-01' } } }] }, page: { number: 0, totalPages: 1 } });
  const ust = { tmKey: 'k', country: 'PL', months: 1 };
  const w = await syncEvents(ust, { fetchFn: ok, pauseMs: 0, now: new Date('2026-10-11T10:00:00Z') });
  assert.equal(w.events.length, 1);
  assert.equal((await loadEvents()).events[0].id, 'E1');
  await assert.rejects(syncEvents(ust, { fetchFn: async () => odp({}, 500), pauseMs: 0 }));
  assert.equal((await loadEvents()).events.length, 1);
  await assert.rejects(syncEvents({ ...ust, tmKey: ' ' }, {}));
});

test('import z Last.fm wymaga klucza i nazwy; wykonawcy w kolejnosci terminow', async () => {
  await assert.rejects(importLastFm({ lfmKey: '', lfmUser: 'ala' }));
  const a = await importLastFm({ lfmKey: 'k', lfmUser: 'ala', lfmCount: 10 }, { fetchFn: async () => odp({ topartists: { artist: [{ name: 'Kult' }] } }) });
  assert.deepEqual(a, ['Kult']);
  const e = (d, ...p) => ({ date: d, performers: p.map((name) => ({ name })) });
  assert.deepEqual(performerNames([e('2026-12-01', 'B'), e('2026-11-01', 'A'), e('2026-01-01', 'Stary')], '2026-10-11'), ['A', 'B']);
  await dbSet('x', 1);
});
