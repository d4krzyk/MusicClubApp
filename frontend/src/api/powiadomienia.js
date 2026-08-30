import client from './client';

/** Co aplikacja moze poprosic serwer w sprawie powiadomien. */

/** Strona powiadomien do rozwijanej listy przy dzwonku. */
export async function lista({ strona = 0, rozmiar } = {}) {
  const { data } = await client.get('/notifications', {
    params: { page: strona, size: rozmiar },
  });
  return data;
}

/** Sama liczba nieprzeczytanych - bez koperty, w ktorej przychodzi. */
export async function licznik() {
  const { data } = await client.get('/notifications/unread-count');
  return data.count;
}

export async function oznaczPrzeczytane(id) {
  await client.post(`/notifications/${id}/read`);
}

export async function oznaczWszystkie() {
  await client.post('/notifications/read-all');
}

export async function usun(id) {
  await client.delete(`/notifications/${id}`);
}
