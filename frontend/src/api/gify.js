import client from './client';

/** Przegladarka GIF-ow: serwer pyta dostawce (KLIPY albo GIPHY) i podpisuje wyniki. */

/** Czy GIF-y sa wlaczone na serwerze i czyim logiem je podpisac ("Powered by ..."). */
export async function status() {
  const { data } = await client.get('/gifs/status');
  return data;
}

/**
 * Szuka GIF-ow; pusta fraza to popularne. `pozycja` to znacznik z poprzedniej strony (`next`).
 * Wynik: { items: [{ id, title, url, previewUrl, width, height, token }], next }.
 */
export async function szukaj(fraza, pozycja = null, limit = 20) {
  const { data } = await client.get('/gifs/search', {
    params: { q: fraza, ...(pozycja ? { pos: pozycja } : {}), limit },
  });
  return data;
}
