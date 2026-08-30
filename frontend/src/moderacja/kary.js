/**
 * Kary w panelu administratora - wspolne dla WSZYSTKICH miejsc, ktore je nakladaja.
 *
 * <p><b>Skad ten plik.</b> Te same stale staly wczesniej w dwoch miejscach:
 * {@code UsersPage} mial {@code BAN_OPTIONS}, a {@code ReportsPage} -
 * {@code BAN_HOURS} o identycznej zawartosci, plus po jednym
 * {@code const FOREVER} kazdy. Obie listy <b>musza</b> znaczyc to samo:
 * administrator naklada te sama kare, tylko z dwoch roznych ekranow. Przy
 * rozjezdzie panel i zgloszenia zaczely by oferowac rozne kary i nikt by
 * tego nie zauwazyl, bo kazdy ekran z osobna wygladalby poprawnie.</p>
 */

/**
 * Gotowe okresy do wyboru, w godzinach: godzina, doba, tydzien, miesiac.
 *
 * <p>Lista zamiast pola na dowolna liczbe godzin - moderacja to decyzja
 * podejmowana w pospiechu i przy wpisywaniu recznym za latwo o pomylke
 * (8760 zamiast 24).</p>
 */
export const OKRESY_KARY = [1, 24, 168, 720];

/**
 * Wartosc pozycji „na zawsze".
 *
 * <p>Tekst, a nie liczba - bo to nie jest skrajnie duza liczba godzin, tylko
 * inny rodzaj decyzji. Umowna liczba (np. 999999) predzej czy pozniej
 * trafilaby do walidacji godzin i zostala odrzucona jako "poza zakresem".</p>
 */
export const BEZTERMINOWO = 'forever';

/**
 * Wartosc pozycji „zdejmij kare".
 *
 * <p><b>Musi byc rozna od pustej</b> - i to nie jest drobiazg, tylko naprawa
 * bledu, przez ktory kary w ogole nie dalo sie zdjac. Lista wraca po kazdym
 * wyborze do stanu neutralnego ({@code value=""}), a pozycja „zdejmij" tez
 * miala puste value. Wybranie jej nie zmienialo wiec wartosci listy,
 * przegladarka nie zglaszala zadnej zmiany i {@code onChange} nigdy sie nie
 * wywolywalo. Klikniecie wygladalo na przyjete i nie robilo nic.</p>
 */
export const ZDEJMIJ = 'lift';

/**
 * Zamienia wybor z listy na tresc zapytania do serwera.
 *
 * <p>Jedno miejsce na te zamiane, bo oba ekrany musza wysylac dokladnie to
 * samo. Serwer nie odroznia „zdejmij" od „nic nie wybrano" - jedno i drugie
 * znaczy brak kary.</p>
 *
 * @param wybor wartosc z listy: {@code ''}, {@link ZDEJMIJ},
 *              {@link BEZTERMINOWO} albo liczba godzin
 */
export function trescKary(wybor) {
  if (wybor === BEZTERMINOWO) {
    return { hours: null, forever: true };
  }
  return {
    hours: (wybor === '' || wybor === ZDEJMIJ) ? null : Number(wybor),
    forever: false,
  };
}
