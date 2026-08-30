import client from './client';

/**
 * Co aplikacja moze poprosic serwer w sprawie moderacji.
 *
 * <p>Wszystkie te operacje wymagaja roli administratora - serwer sprawdza to
 * sam i nie wierzy przegladarce. Zebranie ich w jednym pliku ma ten skutek
 * uboczny, ze widac tu na jednym ekranie <b>caly</b> zakres wladzy
 * administratora nad kontami.</p>
 */

/* ---------------------------------------------------------------- */
/*  Konta                                                            */
/* ---------------------------------------------------------------- */

/** Strona listy kont z wyszukiwaniem i sortowaniem. */
export async function konta({ fragment, strona, rozmiar, sortujPo, kolejnosc }) {
  const { data } = await client.get('/users', {
    params: {
      fragment, page: strona, size: rozmiar, sortBy: sortujPo, direction: kolejnosc,
    },
  });
  return data;
}

export async function usunKonto(id) {
  await client.delete(`/users/${id}`);
}

export async function zmienRole(id, rola) {
  await client.patch(`/users/${id}/role`, { role: rola });
}

/**
 * Nadaje albo zdejmuje zakaz.
 *
 * <p>Jeden adres na oba rodzaje kary - {@code kind} to POSTING albo
 * MESSAGING. Pusta liczba godzin w tresci znaczy "zdejmij".</p>
 */
export async function ustawZakaz(id, rodzaj, kara) {
  await client.patch(`/users/${id}/bans/${rodzaj}`, kara);
}

/* ---------------------------------------------------------------- */
/*  Powiazania sieciowe                                              */
/* ---------------------------------------------------------------- */

/** Adresy, z ktorych korzystalo konto. */
export async function adresyKonta(id) {
  const { data } = await client.get(`/users/${id}/addresses`);
  return data;
}

/** Konta dzielace adres z tym kontem - poszlaka, nie dowod. */
export async function powiazaneKonta(id) {
  const { data } = await client.get(`/users/${id}/related`);
  return data;
}

export async function zablokowaneAdresy() {
  const { data } = await client.get('/users/blocked-ips');
  return data;
}

export async function zablokujAdres(address, reason) {
  await client.post('/users/blocked-ips', { address, reason });
}

export async function odblokujAdres(id) {
  await client.delete(`/users/blocked-ips/${id}`);
}

/* ---------------------------------------------------------------- */
/*  Zgloszenia                                                       */
/* ---------------------------------------------------------------- */

/** Zglasza konto. Jedyna operacja z tego pliku dostepna dla kazdego. */
export async function zglos(login, zgloszenie) {
  await client.post(`/reports/on/${encodeURIComponent(login)}`, zgloszenie);
}

/** Strona listy zgloszen. Pusty status znaczy "wszystkie". */
export async function zgloszenia(status, rozmiar) {
  const { data } = await client.get('/reports/admin', {
    params: { status, size: rozmiar },
  });
  return data;
}

/** Sama liczba otwartych zgloszen - do znaczka w menu. */
export async function licznikOtwartych() {
  const { data } = await client.get('/reports/admin/open-count');
  return data.count;
}

/** Szczegoly zgloszenia razem z historia decyzji. */
export async function zgloszenie(id) {
  const { data } = await client.get(`/reports/admin/${id}`);
  return data;
}

/** Zamyka sprawe i - jesli decyzja tak mowi - wykonuje kare. */
export async function rozpatrz(id, decyzja) {
  await client.post(`/reports/admin/${id}/resolve`, decyzja);
}

/** Otwiera zamknieta sprawe na nowo, gdy decyzja wymaga zmiany. */
export async function otworzPonownie(id) {
  await client.post(`/reports/admin/${id}/reopen`);
}
