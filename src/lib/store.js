// Dane aplikacji leza na urzadzeniu: ustawienia w localStorage, wydarzenia i podreczne gatunki w IndexedDB.
// Gdy przegladarka nie daje dostepu (tryb prywatny) - dziala na pamieci biezacej karty.
const DB = 'musicclub-solo';
const USTAWIENIA = 'musicclub.ustawienia';

export const DOMYSLNE = {
  tmKey: '', lfmKey: '', lfmUser: '', bitKey: '',
  city: '', radius: 0, country: 'PL', months: 6,
  lfmCount: 50, lfmArtists: [], manualArtists: [],
  lang: '', saved: {},
};

const pamiec = { ust: null, idb: new Map() };

export function loadSettings() {
  try {
    return { ...DOMYSLNE, ...JSON.parse(localStorage.getItem(USTAWIENIA) ?? '{}') };
  } catch {
    return { ...DOMYSLNE, ...(pamiec.ust ?? {}) };
  }
}

export function saveSettings(s) {
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
