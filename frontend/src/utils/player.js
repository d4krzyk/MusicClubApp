/**
 * Ile miejsca na ekranie potrzebuje odtwarzacz danego serwisu.
 *
 * <p><b>To jedyna rzecz z muzyki, ktorej serwer nie moze wiedziec.</b> Adres
 * osadzenia sklada backend ({@code MusicEmbed}), bo kazdy serwis robi to
 * inaczej - ale o tym, ile pikseli wysokosci zajmie ramka, decyduje wyglad
 * strony, a nie baza.</p>
 *
 * <p><b>YouTube dostaje PROPORCJE, nie wysokosc.</b> W tamtym odtwarzaczu
 * leci obraz - teledysk, koncert, wizualizacja - a przy sztywnych 152 px film
 * robil sie paskiem wysokosci wiersza tekstu. Sztywna wysokosc pasuje do
 * Spotify i Apple, bo tam odtwarzacz to okladka plus pasek postepu.</p>
 *
 * <p>Wartosci dla Apple sa te, ktore Apple podaje we wlasnym generatorze kodu
 * do osadzania (175 px dla utworu, 450 px dla albumu i playlisty).</p>
 *
 * @returns wysokosc w pikselach albo {@code null}, gdy ma byc proporcja 16:9
 */
export function playerHeight(provider, kind) {
  if (provider === 'YOUTUBE') {
    return null;                       // proporcja 16:9
  }
  if (provider === 'APPLE_MUSIC') {
    return kind === 'TRACK' ? 175 : 450;
  }
  return kind === 'TRACK' ? 152 : 352; // Spotify
}
