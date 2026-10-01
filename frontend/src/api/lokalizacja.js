import client from './client';

/** Miasto z profilu i podpowiedzi do pola "Miasto". */

/** Ustawia miasto (pusty tekst je usuwa). Oddaje konto - z nowym miastem. */
export async function ustawMiasto(tekst) {
  const { data } = await client.put('/profile/location', { city: tekst });
  return data;
}

/** Podpowiedzi miast po poczatku nazwy - lista { name }. */
export async function podpowiedziMiast(tekst) {
  const { data } = await client.get('/cities', { params: { q: tekst } });
  return data;
}
