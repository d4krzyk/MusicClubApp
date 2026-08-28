/**
 * Rok, od ktorego termin uznajemy za <b>bezterminowy</b>.
 *
 * <p>Serwer zapisuje zakaz "na zawsze" jako 31.12.9999 (stala
 * {@code User.FOREVER}) - dzieki temu bezterminowy zakaz jest zwyklym
 * zakazem z terminem i nie wymaga osobnej kolumny ani osobnej sciezki
 * w kodzie. Jedynym miejscem, ktore musi go rozpoznac, jest interfejs:
 * "do 31.12.9999" wygladaloby jak usterka, a nie jak decyzja.</p>
 */
const FOREVER_YEAR = 9999;

/**
 * Czy ten termin oznacza zakaz bezterminowy.
 *
 * <p>Porownujemy sam rok, a nie cala date co do sekundy. Data wraca
 * z serwera jako tekst i przechodzi przez strefy czasowe - przy porownaniu
 * na rownosc wystarczylaby godzina roznicy, zeby "na zawsze" zaczelo sie
 * wyswietlac jako konkretny termin.</p>
 */
export function isForever(dateText) {
  return !!dateText && new Date(dateText).getFullYear() >= FOREVER_YEAR;
}

/**
 * Zamienia date z backendu na tekst czytelny w danym jezyku.
 *
 * <p>Backend wysyla {@code LocalDateTime} jako np. "2026-08-24T19:09:04.591146".
 * Po polsku chcemy "24 sierpnia 2026", po angielsku "August 24, 2026" -
 * robi to wbudowany {@code Intl.DateTimeFormat}, bez dodatkowej biblioteki.</p>
 *
 * @param {string} dateText date w formacie ISO
 * @param {string} language 'pl' albo 'en'
 */
export function formatDate(dateText, language) {
  if (!dateText) {
    return '—';
  }

  const date = new Date(dateText);

  // Gdyby backend przyslal cos nieoczekiwanego, pokazujemy surowa wartosc
  // zamiast napisu "Invalid Date"
  if (Number.isNaN(date.getTime())) {
    return dateText;
  }

  return new Intl.DateTimeFormat(language, {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(date);
}

/**
 * Data RAZEM Z GODZINA, w strefie czasowej uzytkownika.
 *
 * <p>Osobna funkcja od {@link formatDate}, bo tamta podaje sam dzien
 * („28 sierpnia 2026"). Przy karze trwajacej godzine sam dzien nie mowi nic -
 * trzeba wiedziec, o ktorej sie konczy.</p>
 *
 * <p><b>Godzine sklada przegladarka, a nie serwer</b> - i to jest sedno.
 * Serwer liczy czas w UTC i nie wie, w jakiej strefie siedzi uzytkownik;
 * gdy sam wklejal gotowy napis, zakaz nalozony o 16:55 pokazywal sie jako
 * „do 15:55". {@code Intl.DateTimeFormat} bierze strefe z systemu, wiec
 * wynik zgadza sie z zegarem na ekranie.</p>
 */
export function formatDateTime(dateText, language) {
  if (!dateText) {
    return '—';
  }

  const date = new Date(dateText);
  if (Number.isNaN(date.getTime())) {
    return dateText;
  }

  return new Intl.DateTimeFormat(language, {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

/**
 * "3 minuty temu", "wczoraj", "2 tygodnie temu".
 *
 * <p>Przy powiadomieniach data bezwzgledna ("27 sierpnia 2026") jest
 * bezuzyteczna - liczy sie, czy cos wydarzylo sie przed chwila, czy tydzien
 * temu. Robi to wbudowany {@code Intl.RelativeTimeFormat}, wiec odmiana
 * ("minute" / "minuty" / "minut") jest poprawna w obu jezykach bez zadnej
 * biblioteki.</p>
 */
export function timeAgo(dateText, language) {
  if (!dateText) {
    return '';
  }

  const date = new Date(dateText);
  if (Number.isNaN(date.getTime())) {
    return '';
  }

  const seconds = Math.round((date.getTime() - Date.now()) / 1000);
  const formatter = new Intl.RelativeTimeFormat(language, { numeric: 'auto' });

  /*
   * Od najmniejszej jednostki do najwiekszej: bierzemy pierwsza, w ktorej
   * roznica jest sensowna. Kolejnosc ma znaczenie - przy odwrotnej wszystko
   * byloby "0 lat temu".
   */
  const units = [
    ['second', 60],
    ['minute', 60],
    ['hour', 24],
    ['day', 7],
    ['week', 4.35],
    ['month', 12],
  ];

  let value = seconds;
  for (const [unit, perNext] of units) {
    if (Math.abs(value) < perNext) {
      return formatter.format(Math.round(value), unit);
    }
    value /= perNext;
  }

  return formatter.format(Math.round(value), 'year');
}
