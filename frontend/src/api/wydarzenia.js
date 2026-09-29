import client from './client';

/** Co aplikacja moze poprosic serwer w sprawie wydarzen. */

/** Strona nadchodzacych wydarzen. Miasto to klucz z info(), np. "krakow". */
export async function lista({ miasto = '', fraza = '', strona = 0, rozmiar = 20 } = {}) {
  const { data } = await client.get('/events', {
    params: { city: miasto, q: fraza, page: strona, size: rozmiar },
  });
  return data;
}

/** Miasta do filtra i to, czy serwer w ogole ma skad brac wydarzenia. */
export async function info() {
  const { data } = await client.get('/events/info');
  return data;
}

export async function jedno(id) {
  const { data } = await client.get(`/events/${id}`);
  return data;
}
