import { zmniejszJesliTrzeba } from '../utils/obrazy';
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

/** Link z wiadomosci. Oddaje { result: VERIFIED | CHANGED, username, email }. */
export async function potwierdzEmail(token) {
  const { data } = await client.post('/auth/verify-email', { token });
  return data;
}

/**
 * Link jeszcze raz - przed pierwszym zalogowaniem, wiec z loginem i haslem.
 * Z adresem, gdy trzeba poprawic literowke z rejestracji.
 */
export async function wyslijLinkPonownie(username, password, email) {
  await client.post('/auth/resend-verification', { username, password, ...(email ? { email } : {}) });
}

/** Zmiana adresu w ustawieniach: link na nowy adres jeszcze raz. Oddaje konto. */
export async function wyslijZmianeEmailaPonownie() {
  const { data } = await client.post('/profile/email/resend');
  return data;
}

/** Rezygnacja ze zmiany adresu. Oddaje konto - ze starym adresem. */
export async function anulujZmianeEmaila() {
  const { data } = await client.delete('/profile/email/pending');
  return data;
}

/** Strona zgody na zmiane adresu: { username, newEmail (zamaskowany) }. */
export async function infoZmianyAdresu(token) {
  const { data } = await client.post('/auth/email-change/info', { token });
  return data;
}

/** Zgoda ze starego adresu. Oddaje { result: CHANGED | WAITING_NEW, ... }. */
export async function zgodaNaZmianeAdresu(token) {
  const { data } = await client.post('/auth/email-change/approve', { token });
  return data;
}

/** "To nie ja" - zmiana przepada, wszystkie urzadzenia wylogowane. */
export async function odrzucZmianeAdresu(token) {
  const { data } = await client.post('/auth/email-change/deny', { token });
  return data;
}

/** "Nie pamietam hasla" - serwer zawsze odpowiada tak samo, czy konto istnieje, czy nie. */
export async function poprosONoweHaslo(email) {
  await client.post('/auth/password-reset/request', { email });
}

/** Czy link resetu jest wazny. Oddaje { username }. */
export async function sprawdzLinkHasla(token) {
  const { data } = await client.post('/auth/password-reset/check', { token });
  return data;
}

export async function ustawNoweHaslo(token, password, confirmPassword) {
  await client.post('/auth/password-reset/confirm', { token, password, confirmPassword });
}

/** Wylogowuje wszystkie inne urzadzenia; to zostaje zalogowane. */
export async function wylogujInneUrzadzenia() {
  await client.post('/profile/sessions/revoke-others');
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
  formData.append('file', await zmniejszJesliTrzeba(plik));

  const { data } = await client.put('/profile/avatar', formData);
  return data;
}

export async function usunAwatar() {
  const { data } = await client.delete('/profile/avatar');
  return data;
}

/* Obie operacje ponizej sa nieodwracalne - stad haslo w tresci zapytania. */

/** Kasuje wszystkie wlasne posty. Oddaje, ile ich znikneło. */
export async function usunWszystkiePosty(currentPassword) {
  const { data } = await client.delete('/profile/posts', { data: { currentPassword } });
  return data.deleted;
}

/** Kasuje wlasne konto razem ze wszystkim, co po nim zostalo. */
export async function usunKonto(currentPassword) {
  await client.delete('/profile', { data: { currentPassword } });
}
