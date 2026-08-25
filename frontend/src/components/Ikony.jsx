/**
 * Ikony jako male, wbudowane obrazki SVG.
 *
 * <p><b>Dlaczego nie biblioteka z ikonami?</b> Potrzebujemy ich piec.
 * Dociaganie calej paczki (kilkaset ikon, kilkaset kilobajtow) po to,
 * zeby uzyc kilku, nie ma sensu - a tak nie przybywa zaleznosci
 * do wytlumaczenia na obronie.</p>
 *
 * <p>{@code currentColor} sprawia, ze ikona przyjmuje kolor tekstu obok -
 * dziala wiec poprawnie na przyciskach w kazdym kolorze i w obu motywach.</p>
 */

function Svg({ children, rozmiar = 16, ...reszta }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={rozmiar}
      height={rozmiar}
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      {...reszta}
    >
      {children}
    </svg>
  );
}

/** Plus - dodanie nowego posta albo kolejnych zdjec. */
export function IkonaPlus(props) {
  return (
    <Svg {...props}>
      <path d="M8 2a.75.75 0 0 1 .75.75v4.5h4.5a.75.75 0 0 1 0 1.5h-4.5v4.5a.75.75 0 0 1-1.5 0v-4.5h-4.5a.75.75 0 0 1 0-1.5h4.5v-4.5A.75.75 0 0 1 8 2Z" />
    </Svg>
  );
}

/** Olowek - edycja posta. */
export function IkonaOlowek(props) {
  return (
    <Svg {...props}>
      <path d="M12.146 1.146a.5.5 0 0 1 .708 0l2 2a.5.5 0 0 1 0 .708l-8.5 8.5a.5.5 0 0 1-.223.129l-3 .857a.5.5 0 0 1-.618-.618l.857-3a.5.5 0 0 1 .13-.223l8.5-8.5Zm.354 1.061L4.5 10.207V11.5h1.293l8-8-1.293-1.293Z" />
    </Svg>
  );
}

/** Kosz - usuwanie posta. */
export function IkonaKosz(props) {
  return (
    <Svg {...props}>
      <path d="M6.5 1a.5.5 0 0 0-.5.5V2H3.5a.5.5 0 0 0 0 1H4v9.5A1.5 1.5 0 0 0 5.5 14h5a1.5 1.5 0 0 0 1.5-1.5V3h.5a.5.5 0 0 0 0-1H10v-.5a.5.5 0 0 0-.5-.5h-3ZM5 3h6v9.5a.5.5 0 0 1-.5.5h-5a.5.5 0 0 1-.5-.5V3Zm2 2a.5.5 0 0 1 .5.5v5a.5.5 0 0 1-1 0v-5A.5.5 0 0 1 7 5Zm2 0a.5.5 0 0 1 .5.5v5a.5.5 0 0 1-1 0v-5A.5.5 0 0 1 9 5Z" />
    </Svg>
  );
}

/** Krzyzyk - zamkniecie formularza, usuniecie wybranego zdjecia. */
export function IkonaKrzyzyk(props) {
  return (
    <Svg {...props}>
      <path d="M3.72 3.72a.75.75 0 0 1 1.06 0L8 6.94l3.22-3.22a.75.75 0 1 1 1.06 1.06L9.06 8l3.22 3.22a.75.75 0 1 1-1.06 1.06L8 9.06l-3.22 3.22a.75.75 0 0 1-1.06-1.06L6.94 8 3.72 4.78a.75.75 0 0 1 0-1.06Z" />
    </Svg>
  );
}

/** Zdjecie - przycisk wyboru plikow. */
export function IkonaZdjecie(props) {
  return (
    <Svg {...props}>
      <path d="M2.5 2A1.5 1.5 0 0 0 1 3.5v9A1.5 1.5 0 0 0 2.5 14h11a1.5 1.5 0 0 0 1.5-1.5v-9A1.5 1.5 0 0 0 13.5 2h-11Zm11 1a.5.5 0 0 1 .5.5v6.19l-2.65-2.64a.5.5 0 0 0-.7 0L8 9.69 5.85 7.54a.5.5 0 0 0-.7 0L2 10.69V3.5a.5.5 0 0 1 .5-.5h11ZM5.5 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z" />
    </Svg>
  );
}
