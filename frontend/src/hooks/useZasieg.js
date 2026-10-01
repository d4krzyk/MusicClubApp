import { useCallback, useState } from 'react';

/** Promienie do wyboru (km); 0 = caly kraj. */
export const ZASIEGI = [30, 50, 100, 200, 0];

/** Domyslny promien "w okolicy", gdy ktos ma miasto, a jeszcze nic nie wybral. */
const DOMYSLNY = 100;

const KLUCZ = 'zasieg';

function zapamietany() {
  try {
    const wartosc = localStorage.getItem(KLUCZ);
    const liczba = wartosc === null ? null : Number(wartosc);
    return liczba !== null && ZASIEGI.includes(liczba) ? liczba : null;
  } catch {
    // Tryb prywatny albo zablokowane dane strony - po prostu bez pamieci
    return null;
  }
}

/**
 * Promien "w okolicy" wspolny dla wydarzen i klanow - raz wybrany obowiazuje w obu miejscach.
 * Bez miasta w profilu nie ma od czego liczyc, wiec promien jest zawsze 0 (caly kraj).
 * Kto jeszcze nic nie wybral, dostaje 100 km - po to jest miasto w profilu.
 */
export default function useZasieg(miasto) {
  const [wybrany, setWybrany] = useState(zapamietany);

  const ustaw = useCallback((km) => {
    setWybrany(km);
    try {
      localStorage.setItem(KLUCZ, String(km));
    } catch {
      // jw.
    }
  }, []);

  if (!miasto) {
    return { zasieg: 0, ustaw, mozna: false };
  }
  return { zasieg: wybrany ?? DOMYSLNY, ustaw, mozna: true };
}
