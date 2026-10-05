import client from './client';
import { zmniejszJesliTrzeba } from '../utils/obrazy';

/** Moja karta profilu: zdjecia, "o mnie", "szukam" i pytania muzyczne. */

export async function moja() {
  const { data } = await client.get('/profile/card');
  return data;
}

/** Opis, "szukam" i pytania - w calosci (to, czego nie ma, znika). */
export async function zapisz({ bio, lookingFor, prompts }) {
  const { data } = await client.put('/profile/card', { bio, lookingFor, prompts });
  return data;
}

/**
 * Kto widzi karte na profilu: EVERYONE (kazdy, kto widzi profil), FRIENDS albo DISCOVER_ONLY (na profilu
 * nikt - karta jest tylko w trybie Poznawaj). Talii Poznawaj to nie zmienia.
 */
export async function ustawWidocznosc(visibility) {
  const { data } = await client.put('/profile/card/visibility', { visibility });
  return data;
}

/** Nowe zdjecie na koncu galerii. Serwer i tak wycina z niego EXIF (m.in. GPS). */
export async function dodajZdjecie(plik) {
  const formData = new FormData();
  formData.append('file', await zmniejszJesliTrzeba(plik));
  const { data } = await client.post('/profile/photos', formData);
  return data;
}

export async function usunZdjecie(id) {
  const { data } = await client.delete(`/profile/photos/${id}`);
  return data;
}

/** Nowa kolejnosc: wszystkie numery zdjec, pierwsze = okladka. */
export async function ulozZdjecia(ids) {
  const { data } = await client.put('/profile/photos/order', { ids });
  return data;
}
