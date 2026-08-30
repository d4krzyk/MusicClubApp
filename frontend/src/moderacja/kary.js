/** Kary w panelu administratora - wspolne dla WSZYSTKICH miejsc, ktore je nakladaja. */

/** Gotowe okresy do wyboru, w godzinach: godzina, doba, tydzien, miesiac. */
export const OKRESY_KARY = [1, 24, 168, 720];

/** Wartosc pozycji „na zawsze". */
export const BEZTERMINOWO = 'forever';

/** Wartosc pozycji „zdejmij kare". */
export const ZDEJMIJ = 'lift';

/** Zamienia wybor z listy na tresc zapytania do serwera. */
export function trescKary(wybor) {
  if (wybor === BEZTERMINOWO) {
    return { hours: null, forever: true };
  }
  return {
    hours: (wybor === '' || wybor === ZDEJMIJ) ? null : Number(wybor),
    forever: false,
  };
}
