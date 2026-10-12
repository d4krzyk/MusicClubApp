// Klucze API: oczyszczanie wklejonego tekstu i sprawdzenie jednym zapytaniem.

// Z wklejonego tekstu zostaje sam klucz: bez spacji, cudzyslowow i opisu ("API key: 1a2b..." -> "1a2b...").
export function cleanKey(tekst) {
  const t = String(tekst ?? '').trim().replace(/^["'`]+|["'`]+$/g, '');
  if (!/\s|:/.test(t)) return t;
  const tokeny = t.split(/[\s:;,"'`]+/).filter((s) => /^[A-Za-z0-9_-]{12,}$/.test(s));
  return tokeny.sort((a, b) => b.length - a.length)[0] ?? t.replace(/\s+/g, '');
}

// Co widac po samym ksztalcie klucza, zanim pojdzie zapytanie. Klucze obu serwisow maja 32 znaki;
// Consumer Secret Ticketmastera jest krotszy (16) - ludzie czesto kopiuja go zamiast Consumer Key.
export function keyShape(rodzaj, klucz) {
  if (!klucz) return null;
  if (rodzaj === 'tm') {
    if (/^[A-Za-z0-9]{32}$/.test(klucz)) return 'ok';
    if (/^[A-Za-z0-9]{16}$/.test(klucz)) return 'secret';
    return 'dziwny';
  }
  if (rodzaj === 'lfm') return /^[0-9a-f]{32}$/i.test(klucz) ? 'ok' : 'dziwny';
  return 'ok';
}

const ADRESY = {
  tm: (k) => `https://app.ticketmaster.com/discovery/v2/events.json?size=1&classificationName=music&apikey=${encodeURIComponent(k)}`,
  lfm: (k) => `https://ws.audioscrobbler.com/2.0/?method=chart.gettopartists&limit=1&format=json&api_key=${encodeURIComponent(k)}`,
};

// Jedno probne zapytanie: 'ok', 'zly' (serwis odrzucil klucz), 'limit', 'siec' (brak polaczenia albo przegladarka
// nie wpuszcza zapytania - CORS), 'blad' (cos innego).
export async function checkKey(rodzaj, klucz, { fetchFn = fetch, signal } = {}) {
  let odp;
  try {
    odp = await fetchFn(ADRESY[rodzaj](klucz), { signal, headers: { Accept: 'application/json' } });
  } catch (e) {
    if (e?.name === 'AbortError') throw e;
    return 'siec';
  }
  let json = null;
  try { json = await odp.json(); } catch { /* bez tresci */ }
  if (rodzaj === 'lfm' && json?.error) return json.error === 10 || json.error === 26 ? 'zly' : json.error === 29 ? 'limit' : 'blad';
  if (json?.fault) return 'zly';
  if (odp.status === 401 || odp.status === 403) return 'zly';
  if (odp.status === 429) return 'limit';
  return odp.ok ? 'ok' : 'blad';
}
