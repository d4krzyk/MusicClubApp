import { useEffect, useRef } from 'react';

/** Co ile domyslnie pytamy serwer, gdy karta jest na wierzchu. */
export const DOMYSLNY_ODSTEP_MS = 45_000;

/**
 * Powtarza zapytanie do serwera co zadany czas - ale TYLKO gdy ktos patrzy.
 *
 * <p><b>Po co osobny hook.</b> Aplikacja dopytuje serwer w pieciu miejscach:
 * czat co 3 sekundy, lista rozmow co 10, liczniki co minute, reakcje co 45
 * sekund. Kazde z nich mialo wlasny {@code setInterval} i wlasne zasady -
 * a raczej ich brak: cztery z pieciu tykaly dalej przy zminimalizowanym
 * oknie. Zminimalizowana karta z otwarta rozmowa to 1200 zapytan na godzine
 * wyslanych w prozne: nikt ich nie zobaczy, a lacze i bateria swoje
 * zaplaca.</p>
 *
 * <p><b>Zasady sa tu, w jednym miejscu.</b> Zegar chodzi wylacznie przy
 * widocznej karcie. Powrot do zakladki odswieza od razu - to najczestszy
 * moment, w ktorym cos zdazylo sie zmienic - i dopiero potem wznawia zegar.
 * Gdyby kiedys doszla reguła "zwolnij, gdy serwer zwraca blad", trzeba ja
 * bedzie dopisac raz, a nie w pieciu plikach.</p>
 *
 * <p><b>Pierwszego zapytania hook nie wysyla.</b> Kazde miejsce ma inny
 * warunek, kiedy ma sie zaladowac po raz pierwszy (panel czatu robi to przy
 * otwarciu, dzwonek przy zmianie adresu), a inny - kiedy ma sie ODSWIEZAC.
 * Mieszanie tego dawaloby zapytania, o ktore nikt nie prosil.</p>
 *
 * @param odswiez   co wywolac; <b>nie musi</b> byc opakowane w useCallback -
 *                  hook trzyma zawsze najswiezsza wersje w referencji
 * @param odstepMs  co ile milisekund przy widocznej karcie
 * @param wlaczone  {@code false} calkowicie wstrzymuje odpytywanie - np. gdy
 *                  panel jest zamkniety albo zaslania go otwarta rozmowa
 */
export default function useOdswiezanie(
  odswiez,
  odstepMs = DOMYSLNY_ODSTEP_MS,
  wlaczone = true,
) {
  /*
   * Funkcja siedzi w referencji, a nie w zaleznosciach efektu. Gdyby byla
   * zaleznoscia, kazdy render komponentu, ktory nie owinal jej w useCallback,
   * zdejmowalby i zakladal zegar od nowa - a przy odstepie dluzszym niz czas
   * miedzy renderami odswiezenie nie doszloby do skutku ANI RAZU.
   */
  const biezaca = useRef(odswiez);
  biezaca.current = odswiez;

  useEffect(() => {
    if (!wlaczone) {
      return undefined;
    }

    let zegar = null;
    const wywolaj = () => biezaca.current();

    function stop() {
      if (zegar !== null) {
        clearInterval(zegar);
        zegar = null;
      }
    }

    function start() {
      stop();
      zegar = setInterval(wywolaj, odstepMs);
    }

    function przyZmianieWidocznosci() {
      if (document.visibilityState === 'visible') {
        wywolaj();
        start();
      } else {
        stop();
      }
    }

    if (document.visibilityState === 'visible') {
      start();
    }

    document.addEventListener('visibilitychange', przyZmianieWidocznosci);
    window.addEventListener('focus', wywolaj);

    return () => {
      stop();
      document.removeEventListener('visibilitychange', przyZmianieWidocznosci);
      window.removeEventListener('focus', wywolaj);
    };
  }, [odstepMs, wlaczone]);
}
