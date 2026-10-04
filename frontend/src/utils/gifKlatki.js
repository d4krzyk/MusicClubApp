/*
 * Liczenie klatek GIF-a - bez dekodowania obrazu, samo przejscie po blokach pliku.
 *
 * Budowa: naglowek "GIF87a"/"GIF89a" (6 bajtow), opis ekranu (7), opcjonalna
 * paleta globalna, a potem bloki: 0x21 rozszerzenie, 0x2C obraz (klatka),
 * 0x3B koniec. Rozszerzenia i dane obrazu to ciagi "podblokow": bajt dlugosci
 * i tyle bajtow danych, az do bajtu 0.
 */

/** Zwraca liczbe klatek (najwyzej `limit` - dalej nie liczymy). Rzuca przy uszkodzonym pliku. */
export function liczKlatkiGif(bajty, limit = 2) {
  if (bajty.length < 13 || bajty[0] !== 0x47 || bajty[1] !== 0x49 || bajty[2] !== 0x46) {
    throw new Error('To nie jest GIF');
  }
  let i = 13;
  if (bajty[10] & 0x80) {
    i += 3 * 2 ** ((bajty[10] & 0x07) + 1);
  }

  function pominPodbloki() {
    while (i < bajty.length) {
      const dlugosc = bajty[i];
      i += 1;
      if (dlugosc === 0) {
        return;
      }
      i += dlugosc;
    }
    throw new Error('Urwany GIF');
  }

  let klatki = 0;
  while (i < bajty.length) {
    const blok = bajty[i];
    i += 1;
    if (blok === 0x3B) {
      return klatki;
    }
    if (blok === 0x21) {
      i += 1; // rodzaj rozszerzenia
      pominPodbloki();
    } else if (blok === 0x2C) {
      klatki += 1;
      if (klatki >= limit) {
        return klatki;
      }
      if (i + 9 > bajty.length) {
        throw new Error('Urwany GIF');
      }
      const flagi = bajty[i + 8];
      i += 9;
      if (flagi & 0x80) {
        i += 3 * 2 ** ((flagi & 0x07) + 1);
      }
      i += 1; // najmniejszy rozmiar kodu LZW
      pominPodbloki();
    } else {
      throw new Error('Nieznany blok GIF');
    }
  }
  // Bez znacznika konca - przegladarki i tak takie pokazuja, liczymy, co bylo
  return klatki;
}
