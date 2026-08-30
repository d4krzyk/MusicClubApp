import { useEffect, useRef } from 'react';

/** Co ile domyslnie pytamy serwer, gdy karta jest na wierzchu. */
export const DOMYSLNY_ODSTEP_MS = 45_000;

/** Powtarza zapytanie do serwera co zadany czas - ale TYLKO gdy ktos patrzy. */
export default function useOdswiezanie(
  odswiez,
  odstepMs = DOMYSLNY_ODSTEP_MS,
  wlaczone = true,
) {
  /* Funkcja siedzi w referencji, a nie w zaleznosciach efektu. */
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
