/**
 * Zamienia date z backendu na tekst czytelny w danym jezyku.
 *
 * <p>Backend wysyla {@code LocalDateTime} jako np. "2026-08-24T19:09:04.591146".
 * Po polsku chcemy "24 sierpnia 2026", po angielsku "August 24, 2026" -
 * robi to wbudowany {@code Intl.DateTimeFormat}, bez dodatkowej biblioteki.</p>
 *
 * @param {string} tekstDaty data w formacie ISO
 * @param {string} jezyk 'pl' albo 'en'
 */
export function sformatujDate(tekstDaty, jezyk) {
  if (!tekstDaty) {
    return '—';
  }

  const data = new Date(tekstDaty);

  // Gdyby backend przyslal cos nieoczekiwanego, pokazujemy surowa wartosc
  // zamiast napisu "Invalid Date"
  if (Number.isNaN(data.getTime())) {
    return tekstDaty;
  }

  return new Intl.DateTimeFormat(jezyk, {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(data);
}
