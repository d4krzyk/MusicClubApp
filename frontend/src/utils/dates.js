/** Rok, od ktorego termin uznajemy za bezterminowy. */
const FOREVER_YEAR = 9999;

/** Czy ten termin oznacza zakaz bezterminowy. */
export function isForever(dateText) {
  return !!dateText && new Date(dateText).getFullYear() >= FOREVER_YEAR;
}

/** Zamienia date z backendu na tekst czytelny w danym jezyku. */
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

/** Data RAZEM Z GODZINA, w strefie czasowej uzytkownika. */
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

/** "3 minuty temu", "wczoraj", "2 tygodnie temu". */
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
   * Od najmniejszej jednostki do najwiekszej: bierzemy pierwsza, w ktorej roznica jest sensowna.
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
