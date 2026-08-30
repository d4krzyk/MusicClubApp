import client from './client';

/** Co aplikacja moze poprosic serwer w sprawie postow i reakcji. */

/**
 * Strona tablicy.
 *
 * <p>Ten sam adres obsluguje trzy widoki: tablice ogolna, tablice znajomych
 * ({@code scope}) i posty jednej osoby ({@code author}). Rozne sa tylko
 * parametry - dlatego jedna funkcja z opcjami, a nie trzy blizniacze.</p>
 */
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

/**
 * Dodaje post razem ze zdjeciami.
 *
 * <p>Tresc i zdjecia ida jednym zapytaniem wieloczesciowym: tresc jako JSON
 * pod nazwa {@code post}, pliki pod {@code images}. Sklejanie tej koperty
 * siedzi tutaj, zeby formularz nie musial znac nazw pol.</p>
 */
export async function dodaj(tresc, pliki) {
  const formData = new FormData();
  formData.append('post', new Blob([JSON.stringify(tresc)], { type: 'application/json' }));
  pliki.forEach((plik) => formData.append('images', plik));

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

/** Cofa wlasna reakcje. Tak samo jak wyzej - odsyla caly post. */
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
