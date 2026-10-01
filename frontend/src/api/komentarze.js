import client from './client';

/** Komentarze pod postami: lista, odpowiedzi, dodawanie, kasowanie i podpowiedzi do oznaczania. */

/** Komentarze nadrzedne pod postem, od najnowszych. */
export async function lista(idPosta, { strona = 0, rozmiar = 10 } = {}) {
  const { data } = await client.get(`/posts/${idPosta}/comments`, { params: { page: strona, size: rozmiar } });
  return data;
}

/** Odpowiedzi pod jednym komentarzem, od najstarszej. */
export async function odpowiedzi(idKomentarza, { strona = 0, rozmiar = 20 } = {}) {
  const { data } = await client.get(`/comments/${idKomentarza}/replies`, { params: { page: strona, size: rozmiar } });
  return data;
}

/** Jeden komentarz - np. ten, na ktory prowadzi powiadomienie. */
export async function jeden(idKomentarza) {
  const { data } = await client.get(`/comments/${idKomentarza}`);
  return data;
}

/**
 * Dodaje komentarz. Z idRodzica to odpowiedz (na komentarz albo na odpowiedz - serwer zawsze wiesza ja
 * pod komentarzem nadrzednym i zapamietuje, do kogo jest).
 */
export async function dodaj(idPosta, { tresc, idRodzica = null }) {
  const { data } = await client.post(`/posts/${idPosta}/comments`, { content: tresc, parentId: idRodzica });
  return data;
}

export async function usun(idKomentarza) {
  await client.delete(`/comments/${idKomentarza}`);
}

/** Kogo mozna oznaczyc pod tym postem: osoby z rozmowy, potem znajomi; lista { username, avatarUrl }. */
export async function doOznaczenia(idPosta, fragment) {
  const { data } = await client.get(`/posts/${idPosta}/mentionable`, { params: { q: fragment } });
  return data;
}
