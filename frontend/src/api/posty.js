import { zmniejszWszystkie } from '../utils/obrazy';
import client from './client';

/** Co aplikacja moze poprosic serwer w sprawie postow i reakcji. */

/** Strona tablicy. */
export async function tablica({ strona, rozmiar, zakres, autor, kolejnosc }) {
  const { data } = await client.get('/posts', {
    params: {
      page: strona,
      size: rozmiar,
      scope: zakres,
      author: autor,
      direction: kolejnosc,
    },
  });
  return data;
}

export async function jeden(id) {
  const { data } = await client.get(`/posts/${id}`);
  return data;
}

/** Dodaje post razem ze zdjeciami. */
export async function dodaj(tresc, pliki) {
  /* Za duze zdjecia zmniejszamy tutaj, a nie w formularzu - dzieki temu
     obejmuje to kazde miejsce, z ktorego powstaje post. */
  const gotowe = await zmniejszWszystkie(pliki);

  const formData = new FormData();
  formData.append('post', new Blob([JSON.stringify(tresc)], { type: 'application/json' }));
  gotowe.forEach((plik) => formData.append('images', plik));

  const { data } = await client.post('/posts', formData);
  return data;
}

export async function zmien(id, tresc) {
  const { data } = await client.put(`/posts/${id}`, tresc);
  return data;
}

export async function usun(id) {
  await client.delete(`/posts/${id}`);
}

/** Ustawia reakcje. Serwer odsyla caly post z przeliczonymi licznikami. */
export async function ustawReakcje(idPosta, rodzaj) {
  const { data } = await client.put(`/posts/${idPosta}/reaction`, { type: rodzaj });
  return data;
}

/** Cofa wlasna reakcje. Odsyla caly post z przeliczonymi licznikami. */
export async function cofnijReakcje(idPosta) {
  const { data } = await client.delete(`/posts/${idPosta}/reaction`);
  return data;
}

/** Kto zareagowal pod danym postem. */
export async function autorzyReakcji(idPosta) {
  const { data } = await client.get(`/posts/${idPosta}/reactions`);
  return data;
}

/** Liczniki reakcji dla wielu postow naraz - jedno zapytanie na cala widoczna liste. */
export async function licznikiReakcji(numery) {
  const { data } = await client.get('/posts/reactions', {
    params: { ids: numery.join(',') },
  });
  return data;
}
