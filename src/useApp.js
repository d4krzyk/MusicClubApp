import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { loadSettings, saveSettings, dbClear } from './lib/store.js';
import { loadEvents, loadTags, syncEvents, syncTags, importLastFm, performerNames, MIN_ODSWIEZANIA_MS, NA_PRZEBIEG } from './lib/sync.js';
import { buildTaste } from './lib/match.js';
import { nameKey } from './lib/nameKeys.js';
import { cityByName } from './lib/location.js';

// Caly stan aplikacji: ustawienia, pobrane koncerty, gatunki i odswiezanie w tle.
export function useApp() {
  const [settings, setSettingsState] = useState(loadSettings);
  const [data, setData] = useState({ events: [], syncedAt: null });
  const [tags, setTags] = useState({});
  const [status, setStatus] = useState({ running: false, step: null, done: 0, total: 0, error: null, message: null });
  const [ready, setReady] = useState(false);
  const abort = useRef(null);
  const settingsRef = useRef(settings);
  settingsRef.current = settings;

  const setSettings = useCallback((zmiana) => {
    setSettingsState((poprzednie) => {
      const nowe = { ...poprzednie, ...(typeof zmiana === 'function' ? zmiana(poprzednie) : zmiana) };
      saveSettings(nowe);
      return nowe;
    });
  }, []);

  useEffect(() => {
    let zyje = true;
    Promise.all([loadEvents(), loadTags()]).then(([d, t]) => {
      if (!zyje) return;
      setData(d);
      setTags(Object.fromEntries(Object.entries(t).map(([k, v]) => [k, v.tags])));
      setReady(true);
    });
    return () => { zyje = false; };
  }, []);

  const artistNames = useMemo(() => {
    const widziane = new Set();
    const wynik = [];
    for (const a of [...settings.manualArtists, ...settings.lfmArtists]) {
      const k = nameKey(a);
      if (k && !widziane.has(k)) { widziane.add(k); wynik.push(a); }
    }
    return wynik;
  }, [settings.manualArtists, settings.lfmArtists]);

  const taste = useMemo(() => buildTaste(artistNames, tags), [artistNames, tags]);
  const origin = useMemo(() => {
    const c = settings.city ? cityByName(settings.city) : null;
    return c ? { lat: c.lat, lon: c.lon } : null;
  }, [settings.city]);

  const odswiezTagi = useCallback(async (events, { signal, onProgress } = {}) => {
    const s = settingsRef.current;
    if (!s.lfmKey.trim()) return;
    const fav = [...s.manualArtists, ...s.lfmArtists];
    const dzis = new Date().toISOString().slice(0, 10);
    const r = await syncTags([...fav, ...performerNames(events, dzis)], { lfmKey: s.lfmKey, signal,
      limit: fav.length + NA_PRZEBIEG, onProgress });
    setTags(r.tags);
  }, []);

  const refresh = useCallback(async ({ silent = false } = {}) => {
    const s = settingsRef.current;
    if (!s.tmKey.trim()) { setStatus((x) => ({ ...x, error: { kod: 'KLUCZ_BRAK' } })); return; }
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      setStatus((x) => ({ ...x, running: false, error: null, message: silent ? null : 'sync.offline' }));
      return;
    }
    abort.current?.abort();
    const ctl = new AbortController();
    abort.current = ctl;
    setStatus({ running: true, step: 'events', done: 0, total: 0, error: null, message: null });
    try {
      const d = await syncEvents(s, { signal: ctl.signal, onProgress: (p) => setStatus((x) => ({ ...x, ...p })) });
      setData(d);
      setStatus({ running: true, step: 'tags', done: 0, total: 0, error: null, message: null });
      try {
        await odswiezTagi(d.events, { signal: ctl.signal, onProgress: (p) => setStatus((x) => ({ ...x, ...p })) });
      } catch (e) {
        if (e?.name === 'AbortError') throw e;
        // gatunki sa dodatkiem - koncerty i tak sa pobrane
        setStatus({ running: false, step: null, done: 0, total: 0, error: { kod: e?.kod ?? 'ERR', tekst: e?.message }, message: null });
        return;
      }
      setStatus({ running: false, step: null, done: 0, total: 0, error: null, message: 'sync.done', count: d.events.length });
    } catch (e) {
      if (e?.name === 'AbortError') { setStatus({ running: false, step: null, done: 0, total: 0, error: null, message: null }); return; }
      setStatus({ running: false, step: null, done: 0, total: 0, error: { kod: e?.kod ?? 'ERR', tekst: e?.message }, message: null });
    }
  }, [odswiezTagi]);

  const stop = useCallback(() => abort.current?.abort(), []);

  // Po starcie: jesli dane starsze niz 6 h (albo ich nie ma), a jest klucz i internet - odswiez po cichu.
  const startowe = useRef(false);
  useEffect(() => {
    if (!ready || startowe.current) return;
    startowe.current = true;
    const s = settingsRef.current;
    const stare = !data.syncedAt || Date.now() - Date.parse(data.syncedAt) > MIN_ODSWIEZANIA_MS || data.country !== s.country;
    if (s.tmKey.trim() && stare) refresh({ silent: true });
  }, [ready, data, refresh]);

  const importArtists = useCallback(async () => {
    const lista = await importLastFm(settingsRef.current);
    setSettings({ lfmArtists: lista });
    return lista.length;
  }, [setSettings]);

  // Gatunki nowych ulubionych artystow (bez ponownego pobierania koncertow).
  const refreshTags = useCallback(async () => {
    const ctl = new AbortController();
    abort.current = ctl;
    setStatus({ running: true, step: 'tags', done: 0, total: 0, error: null, message: null });
    try {
      await odswiezTagi(data.events, { signal: ctl.signal, onProgress: (p) => setStatus((x) => ({ ...x, ...p })) });
      setStatus({ running: false, step: null, done: 0, total: 0, error: null, message: null });
    } catch (e) {
      setStatus({ running: false, step: null, done: 0, total: 0, error: e?.name === 'AbortError' ? null : { kod: e?.kod ?? 'ERR', tekst: e?.message }, message: null });
    }
  }, [data.events, odswiezTagi]);

  const wipe = useCallback(async () => {
    abort.current?.abort();
    await dbClear();
    const czyste = loadSettingsDefaults();
    saveSettings(czyste);
    setSettingsState(czyste);
    setData({ events: [], syncedAt: null });
    setTags({});
    setStatus({ running: false, step: null, done: 0, total: 0, error: null, message: null });
  }, []);

  return { settings, setSettings, data, tags, taste, origin, artistNames, status, ready, refresh, stop, importArtists, refreshTags, wipe };
}

function loadSettingsDefaults() {
  try { localStorage.removeItem('musicclub.ustawienia'); } catch { /* nic */ }
  return loadSettings();
}
