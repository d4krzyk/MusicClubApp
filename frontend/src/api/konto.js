import client from './client';

/** Co aplikacja moze poprosic serwer w sprawie wlasnego konta i sesji. */

/** Kto jest zalogowany. Blad 401 znaczy "nikt" - i jest normalna odpowiedzia. */
export async function ktoJestem() {
  const { data } = await client.get('/auth/me');
  return data;
}

export async function zaloguj(username, password, rememberMe) {
  const { data } = await client.post('/auth/login', { username, password, rememberMe });
  return data;
}

/** Rejestracja NIE loguje automatycznie - serwer tylko zaklada konto. */
export async function zarejestruj(dane) {
  const { data } = await client.post('/auth/register', dane);
  return data;
}

export async function wyloguj() {
  await client.post('/auth/logout');
}

/** Zmiana loginu i e-maila. Oddaje konto po zmianie. */
export async function zmienDane(dane) {
  const { data } = await client.put('/profile', dane);
  return data;
}

/** Zmiana hasla. Nic nie oddaje - serwer odsyla 204 bez tresci. */
export async function zmienHaslo(dane) {
  await client.put('/profile/password', dane);
}

/** Wgrywa zdjecie profilowe. */
export async function ustawAwatar(plik) {
  const formData = new FormData();
  formData.append('file', plik);

  const { data } = await client.put('/profile/avatar', formData);
  return data;
}

export async function usunAwatar() {
  const { data } = await client.delete('/profile/avatar');
  return data;
}
