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

function Svg({ children, size = 16, ...rest }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {children}
    </svg>
  );
}

/** Plus - dodanie nowego posta albo kolejnych zdjec. */
export function IconPlus(props) {
  return (
    <Svg {...props}>
      <path d="M8 2a.75.75 0 0 1 .75.75v4.5h4.5a.75.75 0 0 1 0 1.5h-4.5v4.5a.75.75 0 0 1-1.5 0v-4.5h-4.5a.75.75 0 0 1 0-1.5h4.5v-4.5A.75.75 0 0 1 8 2Z" />
    </Svg>
  );
}

/** Olowek - edycja posta. */
export function IconPencil(props) {
  return (
    <Svg {...props}>
      <path d="M12.146 1.146a.5.5 0 0 1 .708 0l2 2a.5.5 0 0 1 0 .708l-8.5 8.5a.5.5 0 0 1-.223.129l-3 .857a.5.5 0 0 1-.618-.618l.857-3a.5.5 0 0 1 .13-.223l8.5-8.5Zm.354 1.061L4.5 10.207V11.5h1.293l8-8-1.293-1.293Z" />
    </Svg>
  );
}

/** Kosz - usuwanie posta. */
export function IconTrash(props) {
  return (
    <Svg {...props}>
      <path d="M6.5 1a.5.5 0 0 0-.5.5V2H3.5a.5.5 0 0 0 0 1H4v9.5A1.5 1.5 0 0 0 5.5 14h5a1.5 1.5 0 0 0 1.5-1.5V3h.5a.5.5 0 0 0 0-1H10v-.5a.5.5 0 0 0-.5-.5h-3ZM5 3h6v9.5a.5.5 0 0 1-.5.5h-5a.5.5 0 0 1-.5-.5V3Zm2 2a.5.5 0 0 1 .5.5v5a.5.5 0 0 1-1 0v-5A.5.5 0 0 1 7 5Zm2 0a.5.5 0 0 1 .5.5v5a.5.5 0 0 1-1 0v-5A.5.5 0 0 1 9 5Z" />
    </Svg>
  );
}

/** Krzyzyk - zamkniecie formularza, usuniecie wybranego zdjecia. */
export function IconCross(props) {
  return (
    <Svg {...props}>
      <path d="M3.72 3.72a.75.75 0 0 1 1.06 0L8 6.94l3.22-3.22a.75.75 0 1 1 1.06 1.06L9.06 8l3.22 3.22a.75.75 0 1 1-1.06 1.06L8 9.06l-3.22 3.22a.75.75 0 0 1-1.06-1.06L6.94 8 3.72 4.78a.75.75 0 0 1 0-1.06Z" />
    </Svg>
  );
}

/** Osoba z plusem - zaproszenie do znajomych. */
export function IconPersonPlus(props) {
  return (
    <Svg {...props}>
      <path d="M6 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm-5 6.5c0-2.2 2.5-3.5 5-3.5.62 0 1.24.08 1.82.24A4.48 4.48 0 0 0 7.5 13c0 .53.09 1.04.26 1.5H1.5a.5.5 0 0 1-.5-.5ZM12 9.5a.75.75 0 0 1 .75.75v1.5h1.5a.75.75 0 0 1 0 1.5h-1.5v1.5a.75.75 0 0 1-1.5 0v-1.5h-1.5a.75.75 0 0 1 0-1.5h1.5v-1.5A.75.75 0 0 1 12 9.5Z" />
    </Svg>
  );
}

/** Osoba z ptaszkiem - juz jestescie znajomymi. */
export function IconPersonCheck(props) {
  return (
    <Svg {...props}>
      <path d="M6 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm-5 6.5c0-2.2 2.5-3.5 5-3.5.62 0 1.24.08 1.82.24A4.48 4.48 0 0 0 7.5 13c0 .53.09 1.04.26 1.5H1.5a.5.5 0 0 1-.5-.5Zm14.28-3.53a.75.75 0 0 1 0 1.06l-3 3a.75.75 0 0 1-1.06 0l-1.5-1.5a.75.75 0 1 1 1.06-1.06l.97.97 2.47-2.47a.75.75 0 0 1 1.06 0Z" />
    </Svg>
  );
}

/** Slonce - przelaczenie na motyw jasny. */
export function IconSun(props) {
  return (
    <Svg {...props}>
      <path d="M8 1a.75.75 0 0 1 .75.75v1a.75.75 0 0 1-1.5 0v-1A.75.75 0 0 1 8 1Zm0 10.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm0 1.25a.75.75 0 0 1 .75.75v1a.75.75 0 0 1-1.5 0v-1a.75.75 0 0 1 .75-.75ZM15 8a.75.75 0 0 1-.75.75h-1a.75.75 0 0 1 0-1.5h1A.75.75 0 0 1 15 8ZM2.75 8.75h-1a.75.75 0 0 1 0-1.5h1a.75.75 0 0 1 0 1.5Zm9.9-5.4a.75.75 0 0 1 0 1.06l-.7.71a.75.75 0 1 1-1.07-1.06l.71-.71a.75.75 0 0 1 1.06 0ZM4.12 11.88a.75.75 0 0 1 0 1.06l-.71.71a.75.75 0 0 1-1.06-1.06l.71-.71a.75.75 0 0 1 1.06 0Zm8.53 1.77a.75.75 0 0 1-1.06 0l-.71-.71a.75.75 0 1 1 1.06-1.06l.71.71a.75.75 0 0 1 0 1.06ZM3.35 3.35a.75.75 0 0 1 1.06 0l.71.71a.75.75 0 0 1-1.06 1.06l-.71-.71a.75.75 0 0 1 0-1.06Z" />
    </Svg>
  );
}

/** Ksiezyc - przelaczenie na motyw ciemny. */
export function IconMoon(props) {
  return (
    <Svg {...props}>
      <path d="M6.2 1.4a.75.75 0 0 1 .2.83A5.5 5.5 0 0 0 13.77 9.6a.75.75 0 0 1 1.03.95A6.5 6.5 0 1 1 5.37 1.2a.75.75 0 0 1 .83.2Z" />
    </Svg>
  );
}

/** Zdjecie - przycisk wyboru plikow. */
export function IconImage(props) {
  return (
    <Svg {...props}>
      <path d="M2.5 2A1.5 1.5 0 0 0 1 3.5v9A1.5 1.5 0 0 0 2.5 14h11a1.5 1.5 0 0 0 1.5-1.5v-9A1.5 1.5 0 0 0 13.5 2h-11Zm11 1a.5.5 0 0 1 .5.5v6.19l-2.65-2.64a.5.5 0 0 0-.7 0L8 9.69 5.85 7.54a.5.5 0 0 0-.7 0L2 10.69V3.5a.5.5 0 0 1 .5-.5h11ZM5.5 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z" />
    </Svg>
  );
}

/** Strzalka w lewo - przewijanie poziomych list. */
export function IconArrowLeft(props) {
  return (
    <Svg {...props}>
      <path d="M10.354 3.646a.5.5 0 0 1 0 .708L6.707 8l3.647 3.646a.5.5 0 0 1-.708.708l-4-4a.5.5 0 0 1 0-.708l4-4a.5.5 0 0 1 .708 0Z" />
    </Svg>
  );
}

/** Strzalka w prawo - przewijanie poziomych list. */
export function IconArrowRight(props) {
  return (
    <Svg {...props}>
      <path d="M5.646 3.646a.5.5 0 0 1 .708 0l4 4a.5.5 0 0 1 0 .708l-4 4a.5.5 0 0 1-.708-.708L9.293 8 5.646 4.354a.5.5 0 0 1 0-.708Z" />
    </Svg>
  );
}

/** Osoba - "moj profil" w rozwijanym menu. */
export function IconPerson(props) {
  return (
    <Svg {...props}>
      <path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm0 1c-2.67 0-6 1.34-6 3v1.25c0 .41.34.75.75.75h10.5c.41 0 .75-.34.75-.75V12c0-1.66-3.33-3-6-3Z" />
    </Svg>
  );
}

/** Zebatka - ustawienia konta w rozwijanym menu. */
export function IconGear(props) {
  return (
    <Svg {...props}>
      <path d="M8 4.754a3.246 3.246 0 1 0 0 6.492 3.246 3.246 0 0 0 0-6.492ZM5.754 8a2.246 2.246 0 1 1 4.492 0 2.246 2.246 0 0 1-4.492 0Z" />
      <path d="M9.796 1.343c-.527-1.79-3.065-1.79-3.592 0l-.094.319a.873.873 0 0 1-1.255.52l-.292-.16c-1.64-.892-3.433.902-2.54 2.541l.159.292a.873.873 0 0 1-.52 1.255l-.319.094c-1.79.527-1.79 3.065 0 3.592l.319.094a.873.873 0 0 1 .52 1.255l-.16.292c-.892 1.64.901 3.434 2.541 2.54l.292-.159a.873.873 0 0 1 1.255.52l.094.319c.527 1.79 3.065 1.79 3.592 0l.094-.319a.873.873 0 0 1 1.255-.52l.292.16c1.64.893 3.434-.902 2.54-2.541l-.159-.292a.873.873 0 0 1 .52-1.255l.319-.094c1.79-.527 1.79-3.065 0-3.592l-.319-.094a.873.873 0 0 1-.52-1.255l.16-.292c.893-1.64-.902-3.433-2.541-2.54l-.292.159a.873.873 0 0 1-1.255-.52l-.094-.319Zm-2.633.283c.246-.835 1.428-.835 1.674 0l.094.319a1.873 1.873 0 0 0 2.693 1.115l.291-.16c.764-.415 1.6.42 1.184 1.185l-.159.292a1.873 1.873 0 0 0 1.116 2.692l.318.094c.835.246.835 1.428 0 1.674l-.319.094a1.873 1.873 0 0 0-1.115 2.693l.16.291c.415.764-.42 1.6-1.185 1.184l-.291-.159a1.873 1.873 0 0 0-2.693 1.116l-.094.318c-.246.835-1.428.835-1.674 0l-.094-.319a1.873 1.873 0 0 0-2.692-1.115l-.292.16c-.764.415-1.6-.42-1.184-1.185l.159-.291A1.873 1.873 0 0 0 1.945 8.93l-.319-.094c-.835-.246-.835-1.428 0-1.674l.319-.094A1.873 1.873 0 0 0 3.06 4.377l-.16-.292c-.415-.764.42-1.6 1.185-1.184l.292.159a1.873 1.873 0 0 0 2.692-1.115l.094-.319Z" />
    </Svg>
  );
}

/** Wyjscie - wylogowanie w rozwijanym menu. */
export function IconLogout(props) {
  return (
    <Svg {...props}>
      <path d="M6 12.5a.5.5 0 0 0-.5-.5H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h2.5a.5.5 0 0 0 0-1H3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2.5a.5.5 0 0 0 .5-.5Z" />
      <path d="M11.854 5.646a.5.5 0 0 0-.708.708L12.293 7.5H6.5a.5.5 0 0 0 0 1h5.793l-1.147 1.146a.5.5 0 0 0 .708.708l2-2a.5.5 0 0 0 0-.708l-2-2Z" />
    </Svg>
  );
}
