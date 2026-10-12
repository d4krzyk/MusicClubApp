// Dane aplikacji leza na urzadzeniu: ustawienia w localStorage, wydarzenia i podreczne gatunki w IndexedDB.
// Gdy przegladarka nie daje dostepu (tryb prywatny) - dziala na pamieci biezacej karty.
const DB = 'musicclub-solo';
const USTAWIENIA = 'musicclub.ustawienia';

export const DOMYSLNE = {
  tmKey: '', lfmKey: '', lfmUser: '',
  city: '', radius: 0, country: 'PL', months: 6,
  lfmCount: 50, lfmArtists: [], manualArtists: [],
  lang: '', saved: {}, kindOverride: {}, powiadomienia: { nowe: false, zmiany: false },
};

const pamiec = { ust: null, idb: new Map() };

// Pola z dawnych wersji, ktorych juz nie ma - znikaja przy wczytaniu i zapisie (takze z importowanej kopii).
// bitKey: app_id Bandsintown (zrodlo usuniete - Bandsintown nie wydaje kluczy do takich aplikacji).
const DAWNE = ['bitKey'];
const bezDawnych = (s) => { const w = { ...s }; for (const k of DAWNE) delete w[k]; return w; };

export function loadSettings() {
  try {
    const zapisane = JSON.parse(localStorage.getItem(USTAWIENIA) ?? '{}');
    const s = bezDawnych({ ...DOMYSLNE, ...zapisane });
    // stary klucz nie czeka na najblizszy zapis - znika z urzadzenia od razu
    if (DAWNE.some((k) => k in zapisane)) saveSettings(s);
    return s;
  } catch {
    return bezDawnych({ ...DOMYSLNE, ...(pamiec.ust ?? {}) });
  }
}

export function saveSettings(s0) {
  const s = bezDawnych(s0);
  pamiec.ust = s;
  try { localStorage.setItem(USTAWIENIA, JSON.stringify(s)); } catch { /* zostaje pamiec karty */ }
}

let otwarta = null;
function otworz() {
  if (!otwarta) {
    otwarta = new Promise((resolve, reject) => {
      if (typeof indexedDB === 'undefined') { reject(new Error('brak IndexedDB')); return; }
      const r = indexedDB.open(DB, 1);
      r.onupgradeneeded = () => r.result.createObjectStore('kv');
      r.onsuccess = () => resolve(r.result);
      r.onerror = () => reject(r.error);
    });
  }
  return otwarta;
}

export async function dbGet(klucz) {
  try {
    const db = await otworz();
    return await new Promise((resolve, reject) => {
      const q = db.transaction('kv').objectStore('kv').get(klucz);
      q.onsuccess = () => resolve(q.result);
      q.onerror = () => reject(q.error);
    });
  } catch {
    return pamiec.idb.get(klucz);
  }
}

export async function dbSet(klucz, wartosc) {
  pamiec.idb.set(klucz, wartosc);
  try {
    const db = await otworz();
    await new Promise((resolve, reject) => {
      const t = db.transaction('kv', 'readwrite');
      t.objectStore('kv').put(wartosc, klucz);
      t.oncomplete = resolve;
      t.onerror = () => reject(t.error);
    });
  } catch { /* zostaje pamiec karty */ }
}

export async function dbDelete(klucz) {
  pamiec.idb.delete(klucz);
  try {
    const db = await otworz();
    await new Promise((resolve, reject) => {
      const t = db.transaction('kv', 'readwrite');
      t.objectStore('kv').delete(klucz);
      t.oncomplete = resolve;
      t.onerror = () => reject(t.error);
    });
  } catch { /* nic */ }
}

export async function dbClear() {
  pamiec.idb.clear();
  try {
    const db = await otworz();
    await new Promise((resolve, reject) => {
      const t = db.transaction('kv', 'readwrite');
      t.objectStore('kv').clear();
      t.oncomplete = resolve;
      t.onerror = () => reject(t.error);
    });
  } catch { /* nic */ }
}
