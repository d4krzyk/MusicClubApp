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
  widok = 'najblizsze', miasto = '', fraza = '', zasieg = 0, strona = 0, rozmiar = 20,
} = {}) {
  /* zasieg to promien w km od mojego miasta; 0 = caly kraj */
  const { data } = await client.get('/events', {
    params: {
      view: WIDOKI[widok] ?? 'UPCOMING', city: miasto, q: fraza, radius: zasieg || undefined,
      page: strona, size: rozmiar,
    },
  });
  return data;
}

/** Miasta do filtra, czy profil ma czym sie dopasowac i ile mam zapisow. */
export async function info() {
  const { data } = await client.get('/events/info');
  return data;
}

/** Kraj, z ktorego chce widziec wydarzenia. Zwraca nowe info() - z miastami tego kraju. */
export async function zmienKraj(kraj) {
  const { data } = await client.put('/events/country', { country: kraj });
  return data;
}

export async function jedno(id) {
  const { data } = await client.get(`/events/${id}`);
  return data;
}

/**
 * Zainteresowany albo ide; ukryty = nie pokazuj mnie na liscie uczestnikow.
 * Bez "ukryty" serwer bierze domyslne z ustawien prywatnosci (nowy zapis)
 * albo zostawia jak bylo (zmiana).
 */
export async function zapisz(id, status, ukryty) {
  const { data } = await client.put(`/events/${id}/participation`, {
    status, ...(ukryty === undefined ? {} : { hidden: ukryty }),
  });
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
