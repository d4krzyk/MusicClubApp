import client from './client';

/**
 * Co aplikacja moze poprosic serwer w sprawie rozmow.
 *
 * <p><b>Po co ten plik.</b> Adresy koncowek byly wczesniej sklejane w miejscu
 * uzycia - w komponentach, po kilka razy ten sam. Przy zmianie po stronie
 * serwera nie dalo sie ich znalezc po nazwie, bo w kodzie nie wystepowaly
 * jako calosc, tylko jako kawalki szablonu. Tutaj kazda operacja ma nazwe.</p>
 *
 * <p><b>Funkcje oddaja dane, a nie odpowiedz HTTP.</b> Komponent dostaje to,
 * po co przyszedl - liste rozmow albo liczbe nieprzeczytanych - i nie musi
 * wiedziec, ze pod spodem jest {@code response.data} ani jak nazywa sie pole
 * w kopercie od serwera.</p>
 */

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

/**
 * Co nowego od czasu wiadomosci o podanym numerze.
 *
 * <p>Przy pierwszym pytaniu numeru jeszcze nie ma - wtedy serwer sam
 * decyduje, od czego zaczac.</p>
 */
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
