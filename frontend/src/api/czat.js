import client from './client';

/** Co aplikacja moze poprosic serwer w sprawie rozmow. */

/** Login w adresie MUSI byc zakodowany - inaczej kropka albo spacja psuje sciezke. */
const rozmowaZ = (login) => `/messages/with/${encodeURIComponent(login)}`;

/** Lista rozmow do panelu czatu - po jednej na osobe, z ostatnia wiadomoscia. */
export async function rozmowy() {
  const { data } = await client.get('/messages/conversations');
  return data;
}

/** Strona historii rozmowy. Serwer oddaje OD NAJNOWSZEJ. */
export async function historia(login, strona, rozmiar) {
  const { data } = await client.get(rozmowaZ(login), {
    params: { page: strona, size: rozmiar },
  });
  return data;
}

/** Co nowego od czasu wiadomosci o podanym numerze. */
export async function nowsze(login, poNumerze) {
  const { data } = await client.get(`${rozmowaZ(login)}/sync`, {
    params: poNumerze ? { after: poNumerze } : {},
  });
  return data;
}

/** Wysyla wiadomosc i oddaje ja tak, jak zapisal ja serwer. */
export async function wyslij(login, wiadomosc) {
  const { data } = await client.post(rozmowaZ(login), wiadomosc);
  return data;
}

/** Sygnal "pisze" dla drugiej strony. */
export async function pisze(login) {
  await client.post(`${rozmowaZ(login)}/typing`);
}

/** Oznacza rozmowe jako przeczytana. */
export async function oznaczPrzeczytane(login) {
  await client.post(`${rozmowaZ(login)}/read`);
}

/** Sama liczba nieprzeczytanych - bez koperty, w ktorej przychodzi. */
export async function licznikNieprzeczytanych() {
  const { data } = await client.get('/messages/unread-count');
  return data.count;
}

/** Usuwa rozmowe TYLKO u zalogowanego - druga strona zachowuje swoja kopie. */
export async function usunRozmowe(login) {
  await client.delete(rozmowaZ(login));
}
