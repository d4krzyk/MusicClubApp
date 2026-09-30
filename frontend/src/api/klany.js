import { zmniejszWszystkie } from '../utils/obrazy';
import client from './client';

/** Klany: zakladanie, zaproszenia, czlonkowie, kolor z glosowania i czat. */

/** Moj klan (albo null) i zaproszenia, ktore na mnie czekaja. */
export async function moj() {
  const { data } = await client.get('/clans/mine');
  return data;
}

export async function jeden(id) {
  const { data } = await client.get(`/clans/${id}`);
  return data;
}

export async function zaloz(dane) {
  const { data } = await client.post('/clans', dane);
  return data;
}

export async function zmien(id, dane) {
  const { data } = await client.put(`/clans/${id}`, dane);
  return data;
}

export async function rozwiaz(id) {
  await client.delete(`/clans/${id}`);
}

/** Ikona albo zdjecie klanu; rodzaj: 'icon' | 'photo'. Za duze obrazy zmniejszamy tak jak przy postach. */
export async function wgrajObraz(id, rodzaj, plik) {
  const [gotowy] = await zmniejszWszystkie([plik]);
  const formData = new FormData();
  formData.append('file', gotowy);
  const { data } = await client.post(`/clans/${id}/${rodzaj}`, formData);
  return data;
}

export async function usunObraz(id, rodzaj) {
  const { data } = await client.delete(`/clans/${id}/${rodzaj}`);
  return data;
}

export async function zapros(id, login) {
  const { data } = await client.post(`/clans/${id}/invitations`, { username: login });
  return data;
}

export async function cofnijZaproszenie(id, idZaproszenia) {
  const { data } = await client.delete(`/clans/${id}/invitations/${idZaproszenia}`);
  return data;
}

export async function przyjmij(idZaproszenia) {
  const { data } = await client.post(`/clans/invitations/${idZaproszenia}/accept`);
  return data;
}

export async function odrzuc(idZaproszenia) {
  const { data } = await client.post(`/clans/invitations/${idZaproszenia}/decline`);
  return data;
}

export async function odejdz(id) {
  const { data } = await client.delete(`/clans/${id}/members/me`);
  return data;
}

export async function wyrzuc(id, login) {
  const { data } = await client.delete(`/clans/${id}/members/${encodeURIComponent(login)}`);
  return data;
}

export async function ustawRole(id, login, rola) {
  const { data } = await client.put(`/clans/${id}/members/${encodeURIComponent(login)}/role`, { role: rola });
  return data;
}

export async function przekaz(id, login) {
  const { data } = await client.post(`/clans/${id}/transfer`, { username: login });
  return data;
}

/** Moj glos na kolor; bez koloru - cofa glos. */
export async function glosuj(id, kolor) {
  const { data } = kolor
    ? await client.put(`/clans/${id}/color`, { color: kolor })
    : await client.delete(`/clans/${id}/color`);
  return data;
}

/** Czat: ostatnie wiadomosci, nowsze od `po` albo starsze od `przed` (od najstarszej do najnowszej). */
export async function czat(id, { po, przed, limit } = {}) {
  const { data } = await client.get(`/clans/${id}/chat`, { params: { after: po, before: przed, limit } });
  return data;
}

export async function napisz(id, tresc) {
  const { data } = await client.post(`/clans/${id}/chat`, { content: tresc });
  return data;
}

export async function usunWiadomosc(id, idWiadomosci) {
  await client.delete(`/clans/${id}/chat/${idWiadomosci}`);
}
