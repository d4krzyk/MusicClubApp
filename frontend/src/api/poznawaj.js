import client from './client';

/** Tryb Poznawaj: karty osob z okolicy, decyzje "tak"/"nie", wzajemne "tak" = znajomi. */

/** Czy mam wlaczony tryb, zasieg, braki na karcie i podglad mojej karty. */
export async function stan() {
  const { data } = await client.get('/discover/me');
  return data;
}

/** Wlacza albo wylacza tryb i ustawia zasieg (km; 0 = caly kraj). */
export async function ustawienia(wlaczony, zasiegKm) {
  const { data } = await client.put('/discover/settings', { enabled: wlaczony, radiusKm: zasiegKm });
  return data;
}

/** Kolejne karty; `pomin` - loginy kart, ktore juz mamy, zeby nie przyszly drugi raz. */
export async function talia(pomin = [], ile = 10) {
  const params = new URLSearchParams({ limit: String(ile) });
  pomin.forEach((login) => params.append('skip', login));
  const { data } = await client.get(`/discover/deck?${params}`);
  return data;
}

/** "LIKE" (w prawo) albo "PASS" (w lewo). Oddaje { matched, username, avatarUrl, swipesLeft }. */
export async function decyzja(login, rodzaj) {
  const { data } = await client.post('/discover/swipes', { username: login, decision: rodzaj });
  return data;
}

/** Cofa ostatnia decyzje (z ostatnich 10 minut) - oddaje karte tej osoby. */
export async function cofnij() {
  const { data } = await client.post('/discover/undo');
  return data;
}
