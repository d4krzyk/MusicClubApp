import client from './client';

/** Ekipy na koncert: lista pod wydarzeniem, zakladanie, dolaczanie, zarzadzanie i czat ekipy. */

export async function ekipyWydarzenia(idWydarzenia) {
  const { data } = await client.get(`/events/${idWydarzenia}/crews`);
  return data;
}

/** Nowa ekipa: { title, description, capacity, joinPolicy, departureCity } - oddaje strone ekipy. */
export async function zaloz(idWydarzenia, formularz) {
  const { data } = await client.post(`/events/${idWydarzenia}/crews`, formularz);
  return data;
}

export async function moje() {
  const { data } = await client.get('/crews/mine');
  return data;
}

export async function ekipa(id) {
  const { data } = await client.get(`/crews/${id}`);
  return data;
}

export async function zmien(id, formularz) {
  const { data } = await client.put(`/crews/${id}`, formularz);
  return data;
}

export async function zamknijNabor(id, zamkniety) {
  const { data } = await client.put(`/crews/${id}/closed`, { closed: zamkniety });
  return data;
}

/** "Dolacz" albo "Popros" (z wiadomoscia do zakladajacego) - oddaje karte ekipy z nowym stanem. */
export async function dolacz(id, wiadomosc = null) {
  const { data } = await client.post(`/crews/${id}/join`, { message: wiadomosc });
  return data;
}

export async function cofnijProsbe(id) {
  const { data } = await client.delete(`/crews/${id}/request`);
  return data;
}

export async function przyjmij(id, idProsby) {
  const { data } = await client.post(`/crews/${id}/requests/${idProsby}/accept`);
  return data;
}

export async function odrzuc(id, idProsby) {
  const { data } = await client.post(`/crews/${id}/requests/${idProsby}/decline`);
  return data;
}

export async function odejdz(id) {
  await client.delete(`/crews/${id}/members/me`);
}

export async function wyrzuc(id, login) {
  const { data } = await client.delete(`/crews/${id}/members/${encodeURIComponent(login)}`);
  return data;
}

/* --- czat ekipy --- */

export async function czat(id, { po = null, przed = null, limit = 40 } = {}) {
  const { data } = await client.get(`/crews/${id}/chat`, { params: { after: po ?? undefined, before: przed ?? undefined, limit } });
  return data;
}

export async function napisz(id, tresc) {
  const { data } = await client.post(`/crews/${id}/chat`, { content: tresc });
  return data;
}

export async function wyslijSpotkanie(id, spotkanie) {
  const { data } = await client.post(`/crews/${id}/chat/meeting`, spotkanie);
  return data;
}

export async function usunWiadomosc(id, idWiadomosci) {
  await client.delete(`/crews/${id}/chat/${idWiadomosci}`);
}

/** Usuniete i zmienione spotkania od podanego czasu serwera ({ deletedIds, serverTime, meetings }). */
export async function zmiany(id, od = null) {
  const { data } = await client.get(`/crews/${id}/chat/changes`, { params: od ? { since: od } : {} });
  return data;
}

export async function oznaczPrzeczytane(id, doNumeru) {
  await client.post(`/crews/${id}/chat/read`, { upTo: doNumeru });
}
