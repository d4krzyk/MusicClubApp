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

/*
 * Czlon "music." jest OBOWIAZKOWY. Bez niego wzorzec lapalby takze zwykly
 * youtube.com/watch, bo tamten adres konczy sie tym samym tekstem.
 */
const YT_MUSIC_TRACK = /music\.youtube\.com\/watch\?(?:[^\s]*&)?v=([\w-]{11})/;

/* W YouTube Music KAZDY album jest playlista - stad ten sam wzorzec. */
const YT_MUSIC_PLAYLIST = /music\.youtube\.com\/playlist\?(?:[^\s]*&)?list=([\w-]{10,60})/;

const APPLE = /music\.apple\.com\/[a-z]{2}\/(album|playlist|artist|song)\/[^?\s]+(\?i=\d+)?/;

/**
 * @param {string} adres tekst wpisany przez uzytkownika
 * @returns {{provider: string, kind: string}|null} rozpoznany link albo null
 */
export function parseLink(url) {
  if (!url || !url.trim()) {
    return null;
  }
  const text = url.trim();

  for (const pattern of [SPOTIFY_URL, SPOTIFY_URI]) {
    const trafienie = text.match(pattern);
    if (trafienie) {
      return { provider: 'SPOTIFY', kind: trafienie[1].toUpperCase() };
    }
  }

  if (YT_MUSIC_PLAYLIST.test(text)) {
    return { provider: 'YOUTUBE', kind: 'PLAYLIST' };
  }

  if (YT_MUSIC_TRACK.test(text)) {
    // Nagranie to zawsze pojedynczy utwor
    return { provider: 'YOUTUBE', kind: 'TRACK' };
  }

  const apple = text.match(APPLE);
  if (apple) {
    // Album z parametrem ?i= to w rzeczywistosci pojedynczy utwor z albumu
    if (apple[1] === 'album' && apple[2]) {
      return { provider: 'APPLE_MUSIC', kind: 'TRACK' };
    }
    const kinds = { album: 'ALBUM', playlist: 'PLAYLIST', artist: 'ARTIST', song: 'TRACK' };
    return { provider: 'APPLE_MUSIC', kind: kinds[apple[1]] };
  }

  return null;
}

/**
 * Czy to adres ze ZWYKLEGO YouTube - a wiec taki, ktorego nie przyjmujemy?
 *
 * <p>Sluzy wylacznie do pokazania trafniejszego komunikatu. Ogolne "to nie
 * jest znany serwis" przy adresie z YouTube'a wyglada jak blad aplikacji,
 * wiec zamiast tego mowimy wprost, co zrobic.</p>
 */
export function isPlainYouTube(url) {
  if (!url) {
    return false;
  }
  const text = url.toLowerCase();

  // Kolejnosc ma znaczenie: "music.youtube.com" ZAWIERA "youtube.com"
  if (text.includes('music.youtube.com')) {
    return false;
  }
  return text.includes('youtube.com/') || text.includes('youtu.be/');
}

/**
 * Sprawdza link wzgledem WYBRANEGO rodzaju i zwraca klucz komunikatu bledu.
 *
 * @returns {string|null} klucz tlumaczenia albo null, gdy wszystko gra
 */
export function linkError(url, selectedKind) {
  if (!url || !url.trim()) {
    return null;   // pusty link to po prostu post bez muzyki
  }

  const parsed = parseLink(url);
  if (!parsed) {
    return isPlainYouTube(url)
      ? 'posts.musicErrors.youtubeNotMusic'
      : 'posts.musicErrors.invalid';
  }
  if (parsed.kind !== selectedKind) {
    // Mowimy, CO uzytkownik wkleil - to duzo bardziej pomocne niz "zly link"
    return `posts.musicErrors.mismatch.${parsed.kind}`;
  }
  return null;
}
