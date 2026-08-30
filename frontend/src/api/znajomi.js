import client from './client';

/** Co aplikacja moze poprosic serwer w sprawie znajomosci i zaproszen. */

/** Zaproszenia w obie strony: otrzymane i wyslane. */
export async function zaproszenia() {
  const { data } = await client.get('/friends/requests');
  return data;
}

/** Sama liczba oczekujacych zaproszen - do znaczka w menu. */
export async function licznikZaproszen() {
  const { data } = await client.get('/friends/requests/count');
  return data.count;
}

/** Zaprasza do znajomych. Oddaje zapisane zaproszenie. */
export async function zapros(login) {
  const { data } = await client.post('/friends/requests', { username: login });
  return data;
}

export async function przyjmij(idZaproszenia) {
  await client.post(`/friends/requests/${idZaproszenia}/accept`);
}

/** Odrzuca otrzymane zaproszenie albo wycofuje wlasne - z drugiej strony to ta sama operacja. */
export async function odrzuc(idZaproszenia) {
  await client.delete(`/friends/requests/${idZaproszenia}`);
}

export async function usunZnajomego(login) {
  await client.delete(`/friends/${encodeURIComponent(login)}`);
}

/** Propozycje znajomych - osoby o podobnym gusce. */
export async function propozycje(ile) {
  const { data } = await client.get('/friends/suggestions', { params: { limit: ile } });
  return data;
}
