import client from './client';

/** Co aplikacja moze poprosic serwer w sprawie profili - cudzych i wlasnego. */

const profil = (login) => `/profiles/${encodeURIComponent(login)}`;

/** Nagłowek profilu: dane konta, licznik znajomych, stan znajomosci. */
export async function publiczny(login) {
  const { data } = await client.get(profil(login));
  return data;
}

/** Strona listy znajomych danej osoby. */
export async function znajomi(login, strona, rozmiar) {
  const { data } = await client.get(`${profil(login)}/friends`, {
    params: { page: strona, size: rozmiar },
  });
  return data;
}

/** Zestawienie najczesciej sluchanych - do paska na profilu. */
export async function topMuzyka(login, rodzaj, ile) {
  const { data } = await client.get(`${profil(login)}/top-music`, {
    params: { kind: rodzaj, limit: ile },
  });
  return data;
}

/** Ulubieni artysci i utwory. */
export async function ulubieni(login) {
  const { data } = await client.get(`${profil(login)}/favorites`);
  return data;
}

/** Gablotka playlist. */
export async function playlisty(login) {
  const { data } = await client.get(`${profil(login)}/playlists`);
  return data;
}

/** Co Was laczy: wspolni artysci, utwory i gatunki. */
export async function coWasLaczy(login) {
  const { data } = await client.get(`${profil(login)}/common`);
  return data;
}

/* ---------------------------------------------------------------- */
/*  Wlasny profil                                                    */
/* ---------------------------------------------------------------- */

/* Kazda z tych czterech operacji odsyla nowa, pelna liste ulubionych. */

export async function dodajUlubionegoArtyste(externalId) {
  const { data } = await client.post('/profile/favorites/artists', { externalId });
  return data;
}

export async function usunUlubionegoArtyste(externalId) {
  const { data } = await client.delete(`/profile/favorites/artists/${externalId}`);
  return data;
}

export async function dodajUlubionyUtwor(externalId) {
  const { data } = await client.post('/profile/favorites/tracks', { externalId });
  return data;
}

export async function usunUlubionyUtwor(externalId) {
  const { data } = await client.delete(`/profile/favorites/tracks/${externalId}`);
  return data;
}

/** Czy import z Last.fm jest w ogole wlaczony na tym serwerze. */
export async function stanImportuLastFm() {
  const { data } = await client.get('/profile/favorites/import/lastfm');
  return data;
}

/** Importuje ulubionych z konta Last.fm. Oddaje podsumowanie: ile weszlo, ile przepadlo. */
export async function importujZLastFm(login) {
  const { data } = await client.post('/profile/favorites/import/lastfm', { username: login });
  return data;
}

export async function dodajPlayliste(url) {
  const { data } = await client.post('/profile/playlists', { url });
  return data;
}

export async function usunPlayliste(id) {
  const { data } = await client.delete(`/profile/playlists/${id}`);
  return data;
}
