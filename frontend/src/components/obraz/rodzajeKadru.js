/*
 * Gdzie zalacza sie obraz i w jakich proporcjach aplikacja go potem pokazuje.
 *
 * Proporcje sa wtedy stale, kiedy obraz zawsze jest wyswietlany w jednym ksztalcie
 * (awatar w kolku, zdjecia karty 3:4, ikona klanu w kwadracie) - kadr pokazuje dokladnie to,
 * co zobacza inni. Gdzie ksztalt sie zmienia (zdjecia postow, zdjecie klanu na roznych
 * szerokosciach ekranu), proporcje sa do wyboru, a pierwsza z listy jest na start.
 */

export const DOWOLNE = { klucz: 'free', wartosc: null };
const p = (w, h) => ({ klucz: `${w}:${h}`, wartosc: w / h });

export const RODZAJE_KADRU = {
  post: { proporcje: [DOWOLNE, p(1, 1), p(4, 5), p(4, 3), p(16, 9)] },
  awatar: { proporcje: [p(1, 1)], ksztalt: 'kolo' },
  karta: { proporcje: [p(3, 4)] },
  ikonaKlanu: { proporcje: [p(1, 1)], ksztalt: 'zaokraglony' },
  zdjecieKlanu: { proporcje: [p(3, 1), p(16, 9), DOWOLNE] },
};

/** Typ pliku wyglada na obraz? Pusty typ tez przepuszczamy - niektore menedzery plikow na Androidzie go nie podaja. */
export function czyObraz(plik) {
  return plik instanceof Blob && (!plik.type || plik.type.startsWith('image/'));
}
