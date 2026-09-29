import client from './client';

/** Blokady i ustawienia prywatnosci. */

export async function zablokowani() {
  const { data } = await client.get('/blocks');
  return data;
}

export async function zablokuj(login) {
  await client.put(`/blocks/${encodeURIComponent(login)}`);
}

export async function odblokuj(login) {
  await client.delete(`/blocks/${encodeURIComponent(login)}`);
}

export async function ustawienia() {
  const { data } = await client.get('/profile/privacy');
  return data;
}

export async function zapiszUstawienia(dane) {
  const { data } = await client.put('/profile/privacy', dane);
  return data;
}
