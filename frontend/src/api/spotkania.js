import client from './client';

/** Spotkania wysylane w rozmowach i na czacie klanu (miejsce, czas, przypomnienie). */

/** "Bede" (GOING), "Nie dam rady" (NOT_GOING) albo cofniecie odpowiedzi (null). Oddaje spotkanie po zmianie. */
export async function odpowiedz(id, status) {
  const { data } = await client.put(`/meetings/${id}/rsvp`, { status });
  return data;
}

/** Odwolanie - tylko zakladajacy; potwierdzeni dostaja powiadomienie. */
export async function odwolaj(id) {
  const { data } = await client.post(`/meetings/${id}/cancel`);
  return data;
}
