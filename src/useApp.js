import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { loadSettings, saveSettings, dbClear } from './lib/store.js';
import { loadEvents, loadTags, syncEvents, syncTags, importLastFm, performerNames, MIN_ODSWIEZANIA_MS, NA_PRZEBIEG,
  loadBandsintown, syncBandsintown, loadCalendars, refreshCalendars, addCalendarFile, addCalendarUrl, removeCalendar } from './lib/sync.js';
import { mergeAll } from './lib/merge.js';
import { enrich, performerCandidates } from './lib/eventKind.js';
import { buildTaste } from './lib/match.js';
import { nameKey } from './lib/nameKeys.js';
import { cityByName } from './lib/location.js';

// Caly stan aplikacji: ustawienia, pobrane koncerty, gatunki i odswiezanie w tle.
export function useApp() {
  const [settings, setSettingsState] = useState(loadSettings);
  const [data, setData] = useState({ events: [], syncedAt: null });
  const [tags, setTags] = useState({});
  const [bit, setBit] = useState({ events: [], syncedAt: null });
  const [calendars, setCalendars] = useState([]);
  const [status, setStatus] = useState({ running: false, step: null, done: 0, total: 0, error: null, message: null });
  const [ready, setReady] = useState(false);
  const abort = useRef(null);
  const settingsRef = useRef(settings);
  settingsRef.current = settings;
  const calendarsRef = useRef([]);
  const data0 = useRef(null);

  const setSettings = useCallback((zmiana) => {
    setSettingsState((poprzednie) => {
      const nowe = { ...poprzednie, ...(typeof zmiana === 'function' ? zmiana(poprzednie) : zmiana) };
      saveSettings(nowe);
      return nowe;
    });
  }, []);

  useEffect(() => {
    let zyje = true;
    Promise.all([loadEvents(), loadTags(), loadBandsintown(), loadCalendars()]).then(([d, t, b, k]) => {
      if (!zyje) return;
      setData(d);
      setBit(b);
      setCalendars(k);
      setTags(Object.fromEntries(Object.entries(t).map(([k, v]) => [k, v.tags])));
      setReady(true);
    });
    return () => { zyje = false; };
  }, []);

  calendarsRef.current = calendars;
  data0.current = data;

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
  // Jedna lista z trzech zrodel: Ticketmaster (glowne), Bandsintown, twoje kalendarze - ten sam koncert raz.
  // Wydarzenia z kalendarzy dostaja ocene "koncert czy nie", wykonawcow z tytulu i gatunki z tekstu - przed laczeniem,
  // zeby wykonawcy pomogli rozpoznac ten sam koncert z Ticketmastera.
  const events = useMemo(() => mergeAll(data.events, bit.events,
    calendars.flatMap((k) => k.events).map((e) => enrich(e, tags, settings.kindOverride?.[e.id]))),
  [data.events, bit.events, calendars, tags, settings.kindOverride]);
  const origin = useMemo(() => {
    const c = settings.city ? cityByName(settings.city) : null;
    return c ? { lat: c.lat, lon: c.lon } : null;
  }, [settings.city]);

  const odswiezTagi = useCallback(async (events, { signal, onProgress } = {}) => {
    const s = settingsRef.current;
    if (!s.lfmKey.trim()) return;
    const fav = [...s.manualArtists, ...s.lfmArtists];
    const dzis = new Date().toISOString().slice(0, 10);
    // kandydaci na wykonawcow z tytulow wydarzen z kalendarzy - Last.fm potwierdza, ze to artysta, i podaje gatunek
    const zKalendarzy = calendarsRef.current.flatMap((k) => k.events).filter((e) => e.date >= dzis).flatMap(performerCandidates);
    const r = await syncTags([...fav, ...zKalendarzy, ...performerNames(events, dzis)], { lfmKey: s.lfmKey, signal,
      limit: fav.length + zKalendarzy.length + NA_PRZEBIEG, onProgress });
    setTags(r.tags);
  }, []);

  const refresh = useCallback(async ({ silent = false } = {}) => {
    const s = settingsRef.current;
    if (!s.tmKey.trim() && !calendarsRef.current.some((k) => k.kind === 'url')) { setStatus((x) => ({ ...x, error: { kod: 'KLUCZ_BRAK' } })); return; }
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      setStatus((x) => ({ ...x, running: false, error: null, message: silent ? null : 'sync.offline' }));
      return;
    }
    abort.current?.abort();
    const ctl = new AbortController();
    abort.current = ctl;
    setStatus({ running: true, step: 'events', done: 0, total: 0, error: null, message: null });
    try {
      let d = data0.current;
      if (s.tmKey.trim()) {
        d = await syncEvents(s, { signal: ctl.signal, onProgress: (p) => setStatus((x) => ({ ...x, ...p })) });
        setData(d);
      }
      let bladPoboczny = null;
      if (s.bitKey?.trim()) {
        setStatus({ running: true, step: 'bit', done: 0, total: 0, error: null, message: null });
        try {
          const fav = [...s.manualArtists, ...s.lfmArtists].slice(0, 200);
          setBit(await syncBandsintown(fav, { appId: s.bitKey, country: s.country, signal: ctl.signal,
            onProgress: (p) => setStatus((x) => ({ ...x, ...p })) }));
        } catch (e) {
          if (e?.name === 'AbortError') throw e;
          bladPoboczny = { kod: e?.kod ?? 'ERR', tekst: e?.message };
        }
      }
      if (calendarsRef.current.some((k) => k.kind === 'url')) {
        setStatus({ running: true, step: 'calendars', done: 0, total: 0, error: null, message: null });
        setCalendars(await refreshCalendars({ signal: ctl.signal }));
      }
      setStatus({ running: true, step: 'tags', done: 0, total: 0, error: null, message: null });
      try {
        await odswiezTagi(d.events, { signal: ctl.signal, onProgress: (p) => setStatus((x) => ({ ...x, ...p })) });
      } catch (e) {
        if (e?.name === 'AbortError') throw e;
        // gatunki sa dodatkiem - koncerty i tak sa pobrane
        setStatus({ running: false, step: null, done: 0, total: 0, error: { kod: e?.kod ?? 'ERR', tekst: e?.message }, message: null });
        return;
      }
      setStatus({ running: false, step: null, done: 0, total: 0, error: bladPoboczny, message: bladPoboczny ? null : 'sync.done', count: d.events.length });
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
    if ((s.tmKey.trim() || calendars.some((k) => k.kind === 'url')) && stare) refresh({ silent: true });
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
      await odswiezTagi(events, { signal: ctl.signal, onProgress: (p) => setStatus((x) => ({ ...x, ...p })) });
      setStatus({ running: false, step: null, done: 0, total: 0, error: null, message: null });
    } catch (e) {
      setStatus({ running: false, step: null, done: 0, total: 0, error: e?.name === 'AbortError' ? null : { kod: e?.kod ?? 'ERR', tekst: e?.message }, message: null });
    }
  }, [events, odswiezTagi]);

  const wipe = useCallback(async () => {
    abort.current?.abort();
    await dbClear();
    const czyste = loadSettingsDefaults();
    saveSettings(czyste);
    setSettingsState(czyste);
    setData({ events: [], syncedAt: null });
    setBit({ events: [], syncedAt: null });
    setCalendars([]);
    setTags({});
    setStatus({ running: false, step: null, done: 0, total: 0, error: null, message: null });
  }, []);

  const kalendarze = useMemo(() => ({
    dodajPlik: async (tekst, nazwa) => { const l = await addCalendarFile(tekst, nazwa); setCalendars(l); calendarsRef.current = l; po(); return l[l.length - 1]; },
    dodajAdres: async (url, nazwa) => { const l = await addCalendarUrl(url, nazwa); setCalendars(l); calendarsRef.current = l; po(); return l[l.length - 1]; },
    usun: async (id) => setCalendars(await removeCalendar(id)),
  }), []); // eslint-disable-line react-hooks/exhaustive-deps
  // po dodaniu kalendarza: gatunki nowych wykonawcow z Last.fm (w tle)
  const poRef = useRef(null);
  poRef.current = refreshTags;
  function po() { setTimeout(() => poRef.current?.(), 0); }

  return { settings, setSettings, data, events, tags, taste, origin, artistNames, status, ready, refresh, stop, importArtists,
    refreshTags, wipe, calendars, kalendarze };
}

function loadSettingsDefaults() {
  try { localStorage.removeItem('musicclub.ustawienia'); } catch { /* nic */ }
  return loadSettings();
}
