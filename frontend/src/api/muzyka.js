import client from './client';

/** Co aplikacja moze poprosic serwer w sprawie katalogu muzycznego. */

/**
 * Szuka w katalogu Deezera.
 *
 * @param rodzaj ARTIST albo TRACK
 * @param fraza  czego szukamy
 */
export async function szukaj(rodzaj, fraza) {
  const { data } = await client.get(`/music/search/${rodzaj}`, { params: { q: fraza } });
  return data;
}
