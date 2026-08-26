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

const SPOTIFY_URL = /open\.spotify\.com\/(?:intl-[\w-]+\/)?(track|album|artist|playlist)\/([A-Za-z0-9]{22})/;
const SPOTIFY_URI = /spotify:(track|album|artist|playlist):([A-Za-z0-9]{22})/;

const YOUTUBE_UTWOR = [
  /youtube\.com\/watch\?(?:[^\s]*&)?v=([\w-]{11})/,
  /youtu\.be\/([\w-]{11})/,
  /youtube\.com\/embed\/([\w-]{11})/,
  /youtube\.com\/shorts\/([\w-]{11})/,
];

/* W YouTube Music KAZDY album jest playlista - stad ten sam wzorzec. */
const YOUTUBE_PLAYLISTA = /youtube\.com\/playlist\?(?:[^\s]*&)?list=([\w-]{10,60})/;

const APPLE = /music\.apple\.com\/[a-z]{2}\/(album|playlist|artist|song)\/[^?\s]+(\?i=\d+)?/;

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

  if (YOUTUBE_PLAYLISTA.test(tekst)) {
    return { provider: 'YOUTUBE', kind: 'PLAYLIST' };
  }

  for (const wzorzec of YOUTUBE_UTWOR) {
    if (wzorzec.test(tekst)) {
      // Film to zawsze pojedyncze nagranie
      return { provider: 'YOUTUBE', kind: 'TRACK' };
    }
  }

  const apple = tekst.match(APPLE);
  if (apple) {
    // Album z parametrem ?i= to w rzeczywistosci pojedynczy utwor z albumu
    if (apple[1] === 'album' && apple[2]) {
      return { provider: 'APPLE_MUSIC', kind: 'TRACK' };
    }
    const rodzaje = { album: 'ALBUM', playlist: 'PLAYLIST', artist: 'ARTIST', song: 'TRACK' };
    return { provider: 'APPLE_MUSIC', kind: rodzaje[apple[1]] };
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
