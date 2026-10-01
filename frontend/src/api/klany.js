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

export async function napisz(id, tresc, odpowiedzNa = null) {
  const { data } = await client.post(`/clans/${id}/chat`, { content: tresc, replyTo: odpowiedzNa });
  return data;
}

export async function usunWiadomosc(id, idWiadomosci) {
  await client.delete(`/clans/${id}/chat/${idWiadomosci}`);
}

/** Ile nieprzeczytanych wiadomosci czeka na czacie mojego klanu ({ clanId, unread }). */
export async function nieprzeczytane() {
  const { data } = await client.get('/clans/mine/unread');
  return data;
}

/** "Przeczytalem do tej wiadomosci wlacznie". */
export async function oznaczPrzeczytane(id, doNumeru) {
  await client.post(`/clans/${id}/chat/read`, { upTo: doNumeru });
}

/** Wyciszenie (albo wlaczenie) powiadomien na telefon z czatu klanu. */
export async function wycisz(id, wyciszony) {
  await client.put(`/clans/${id}/chat/mute`, { muted: wyciszony });
}

/** Reakcje pod wiadomosciami od podanej wzwyz - lista { messageId, reactions }. */
export async function reakcjeOd(id, od) {
  const { data } = await client.get(`/clans/${id}/chat/reactions`, { params: { since: od } });
  return data;
}

export async function reaguj(id, idWiadomosci, emoji) {
  const { data } = await client.put(`/clans/${id}/chat/${idWiadomosci}/reaction`, { emoji });
  return data;
}

export async function cofnijReakcje(id, idWiadomosci) {
  const { data } = await client.delete(`/clans/${id}/chat/${idWiadomosci}/reaction`);
  return data;
}

/** Gust klanu: wykonawcy i gatunki wspolne dla kilku czlonkow. */
export async function gust(id) {
  const { data } = await client.get(`/clans/${id}/taste`);
  return data;
}

/** Utwor tygodnia: propozycje z biezacego tygodnia i zwyciezcy poprzednich. */
export async function utwory(id) {
  const { data } = await client.get(`/clans/${id}/tracks`);
  return data;
}

export async function zaproponujUtwor(id, url, notatka) {
  const { data } = await client.post(`/clans/${id}/tracks`, { url, note: notatka || null });
  return data;
}

export async function glosujNaUtwor(id, idUtworu, tak) {
  if (tak) {
    await client.put(`/clans/${id}/tracks/${idUtworu}/vote`);
  } else {
    await client.delete(`/clans/${id}/tracks/${idUtworu}/vote`);
  }
}

export async function usunUtwor(id, idUtworu) {
  await client.delete(`/clans/${id}/tracks/${idUtworu}`);
}

/** Nadchodzace wydarzenia, na ktore zapisali sie czlonkowie klanu. */
export async function koncerty(id) {
  const { data } = await client.get(`/clans/${id}/events`);
  return data;
}

/* ------------------------------------------------------------------------ */
/*  Przegladarka klanow, prosby o dolaczenie                                */
/* ------------------------------------------------------------------------ */

/**
 * Lista klanow z przegladarki. Filtry: q (nazwa, skrot, haslo, miasto), genre, city,
 * joinable (tylko takie, do ktorych mozna poprosic o dolaczenie); sort: MATCH, MEMBERS,
 * NEWEST, OLDEST, ACTIVE, NAME.
 */
export async function przegladarka({
  q, genre, city, joinable, radius, sort, page, size,
} = {}) {
  const { data } = await client.get('/clans/directory', {
    params: {
      q: q || undefined,
      genre: genre || undefined,
      city: city || undefined,
      joinable: joinable || undefined,
      radius: radius || undefined,
      sort,
      page,
      size,
    },
  });
  return data;
}

/** Prosba o dolaczenie do klanu, ktory przyjmuje prosby; zwraca strone klanu. */
export async function poprosODolaczenie(id, wiadomosc) {
  const { data } = await client.post(`/clans/${id}/requests`, { message: wiadomosc || null });
  return data;
}

export async function cofnijProsbe(id) {
  const { data } = await client.delete(`/clans/${id}/requests/mine`);
  return data;
}

export async function przyjmijProsbe(id, idProsby) {
  const { data } = await client.post(`/clans/${id}/requests/${idProsby}/accept`);
  return data;
}

export async function odrzucProsbe(id, idProsby) {
  const { data } = await client.post(`/clans/${id}/requests/${idProsby}/decline`);
  return data;
}

/* ------------------------------------------------------------------------ */
/*  Tytuly                                                                  */
/* ------------------------------------------------------------------------ */

export async function dodajTytul(id, dane) {
  const { data } = await client.post(`/clans/${id}/titles`, dane);
  return data;
}

export async function zmienTytul(id, idTytulu, dane) {
  const { data } = await client.put(`/clans/${id}/titles/${idTytulu}`, dane);
  return data;
}

export async function usunTytul(id, idTytulu) {
  const { data } = await client.delete(`/clans/${id}/titles/${idTytulu}`);
  return data;
}

export async function nadajTytul(id, login, idTytulu) {
  const { data } = await client.put(`/clans/${id}/members/${encodeURIComponent(login)}/titles/${idTytulu}`);
  return data;
}

export async function zdejmijTytul(id, login, idTytulu) {
  const { data } = await client.delete(`/clans/${id}/members/${encodeURIComponent(login)}/titles/${idTytulu}`);
  return data;
}

/** Bierze sobie tytul, ktory klan zostawil do wziecia samemu. */
export async function wezTytul(id, idTytulu) {
  const { data } = await client.put(`/clans/${id}/titles/${idTytulu}/claim`);
  return data;
}

export async function oddajTytul(id, idTytulu) {
  const { data } = await client.delete(`/clans/${id}/titles/${idTytulu}/claim`);
  return data;
}

/* ------------------------------------------------------------------------ */
/*  Ankiety i ranking                                                       */
/* ------------------------------------------------------------------------ */

export async function ankiety(id) {
  const { data } = await client.get(`/clans/${id}/polls`);
  return data;
}

export async function zalozAnkiete(id, dane) {
  const { data } = await client.post(`/clans/${id}/polls`, dane);
  return data;
}

export async function glosujWAnkiecie(id, idAnkiety, idOdpowiedzi) {
  await client.put(`/clans/${id}/polls/${idAnkiety}/vote`, { optionId: idOdpowiedzi });
}

export async function cofnijGlosAnkiety(id, idAnkiety) {
  await client.delete(`/clans/${id}/polls/${idAnkiety}/vote`);
}

export async function zamknijAnkiete(id, idAnkiety) {
  await client.post(`/clans/${id}/polls/${idAnkiety}/close`);
}

export async function usunAnkiete(id, idAnkiety) {
  await client.delete(`/clans/${id}/polls/${idAnkiety}`);
}

/** Ranking aktywnosci: okres 'WEEK' (ostatnie 7 dni) albo 'ALL'. */
export async function aktywnosc(id, okres = 'WEEK') {
  const { data } = await client.get(`/clans/${id}/activity`, { params: { period: okres } });
  return data;
}
