import client from './client';

/** Co aplikacja moze poprosic serwer w sprawie wydarzen. */

/** Widoki listy - nazwy w adresie strony i to, co rozumie serwer. */
export const WIDOKI = {
  'dla-ciebie': 'FOR_YOU',
  najblizsze: 'UPCOMING',
  moje: 'MINE',
};

/** Strona wydarzen w wybranym widoku. Miasto to klucz z info(), np. "krakow". */
export async function lista({
  widok = 'najblizsze', miasto = '', fraza = '', strona = 0, rozmiar = 20,
} = {}) {
  const { data } = await client.get('/events', {
    params: {
      view: WIDOKI[widok] ?? 'UPCOMING', city: miasto, q: fraza, page: strona, size: rozmiar,
    },
  });
  return data;
}

/** Miasta do filtra, czy profil ma czym sie dopasowac i ile mam zapisow. */
export async function info() {
  const { data } = await client.get('/events/info');
  return data;
}

export async function jedno(id) {
  const { data } = await client.get(`/events/${id}`);
  return data;
}

/** Zainteresowany albo ide; ukryty = nie pokazuj mnie na liscie uczestnikow. */
export async function zapisz(id, status, ukryty = false) {
  const { data } = await client.put(`/events/${id}/participation`, { status, hidden: ukryty });
  return data;
}

export async function zrezygnuj(id) {
  const { data } = await client.delete(`/events/${id}/participation`);
  return data;
}

export async function uczestnicy(id, strona = 0, rozmiar = 30) {
  const { data } = await client.get(`/events/${id}/attendees`, { params: { page: strona, size: rozmiar } });
  return data;
}
