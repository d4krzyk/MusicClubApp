/**
 * Rozpoznawanie linkow muzycznych po stronie przegladarki.
 *
 * <p><b>To jest KOPIA regul z backendu</b> (`MusicLinkParser`) - i celowo.
 * Serwer i tak sprawdza wszystko od nowa, bo zapytanie da sie wyslac
 * z pominieciem przegladarki. Ta kopia sluzy wylacznie temu, zeby uzytkownik
 * zobaczyl blad OD RAZU przy wpisywaniu, zamiast dowiedziec sie o nim
 * dopiero po kliknieciu "Opublikuj".</p>
 *
 * <p>Gdy dojdzie trzeci serwis, trzeba pamietac o obu miejscach. Sygnalizuje
 * to komentarz w obu plikach - przy tej skali projektu to prostsze niz
 * generowanie regul z jednego zrodla.</p>
 */

const SPOTIFY_URL = /open\.spotify\.com\/(?:intl-[\w-]+\/)?(track|album|artist)\/([A-Za-z0-9]{22})/;
const SPOTIFY_URI = /spotify:(track|album|artist):([A-Za-z0-9]{22})/;

const YOUTUBE = [
  /youtube\.com\/watch\?(?:[^\s]*&)?v=([\w-]{11})/,
  /youtu\.be\/([\w-]{11})/,
  /youtube\.com\/embed\/([\w-]{11})/,
];

/**
 * @param {string} adres tekst wpisany przez uzytkownika
 * @returns {{provider: string, kind: string}|null} rozpoznany link albo null
 */
export function rozpoznajLink(adres) {
  if (!adres || !adres.trim()) {
    return null;
  }
  const tekst = adres.trim();

  for (const wzorzec of [SPOTIFY_URL, SPOTIFY_URI]) {
    const trafienie = tekst.match(wzorzec);
    if (trafienie) {
      return { provider: 'SPOTIFY', kind: trafienie[1].toUpperCase() };
    }
  }

  for (const wzorzec of YOUTUBE) {
    if (wzorzec.test(tekst)) {
      // Film na YouTube to zawsze pojedyncze nagranie
      return { provider: 'YOUTUBE', kind: 'TRACK' };
    }
  }

  return null;
}

/**
 * Sprawdza link wzgledem WYBRANEGO rodzaju i zwraca klucz komunikatu bledu.
 *
 * @returns {string|null} klucz tlumaczenia albo null, gdy wszystko gra
 */
export function bladLinku(adres, wybranyRodzaj) {
  if (!adres || !adres.trim()) {
    return null;   // pusty link to po prostu post bez muzyki
  }

  const rozpoznany = rozpoznajLink(adres);
  if (!rozpoznany) {
    return 'posts.musicErrors.invalid';
  }
  if (rozpoznany.kind !== wybranyRodzaj) {
    // Mowimy, CO uzytkownik wkleil - to duzo bardziej pomocne niz "zly link"
    return `posts.musicErrors.mismatch.${rozpoznany.kind}`;
  }
  return null;
}
