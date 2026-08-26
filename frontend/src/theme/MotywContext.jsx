import { createContext, useCallback, useContext, useEffect, useState } from 'react';

/**
 * Motyw jasny/ciemny.
 *
 * <p><b>Podzial roboty z {@code index.html}.</b> Pierwsze ustawienie motywu
 * robi maly skrypt w {@code index.html} - jeszcze przed narysowaniem strony,
 * zeby nic nie mignelo w zlych kolorach. Ten kontekst tylko <b>przejmuje</b>
 * to, co tam ustawiono, i pozwala potem przelaczac.</p>
 *
 * <p><b>Dlaczego atrybut na {@code <html>}, a nie wlasne zmienne CSS?</b>
 * Bootstrap 5.3 ma wbudowana obsluge motywow wlasnie przez
 * {@code data-bs-theme}. Ustawienie tego jednego atrybutu przestawia
 * wszystkie kolory frameworka naraz - tla, obramowania, tekst, formularze.
 * Nasz {@code styles.css} korzysta z tych samych zmiennych
 * ({@code var(--bs-secondary-bg)} i podobnych), wiec przelacza sie razem
 * z nimi, bez ani jednej dodatkowej linijki.</p>
 */
const MotywContext = createContext(null);

const KLUCZ = 'motyw';

/** Odczyt zapisanego wyboru. Osobna funkcja, bo localStorage potrafi rzucic wyjatkiem. */
function odczytajZapisany() {
  try {
    return localStorage.getItem(KLUCZ);
  } catch {
    /*
     * W trybie prywatnym (i przy zablokowanych danych witryn) samo siegniecie
     * po localStorage konczy sie wyjatkiem. Wtedy motyw po prostu nie jest
     * zapamietywany - aplikacja ma dzialac dalej, a nie sie wywalac.
     */
    return null;
  }
}

export function MotywProvider({ children }) {
  /*
   * Stan startowy czytamy z ATRYBUTU, a nie z localStorage. Skrypt
   * w index.html juz rozstrzygnal, co pokazac (zapisany wybor albo ustawienie
   * systemu) - powtarzanie tej logiki tutaj groziloby tym, ze obie wersje
   * z czasem sie rozjada.
   */
  const [motyw, setMotyw] = useState(
    () => document.documentElement.getAttribute('data-bs-theme') ?? 'dark');

  useEffect(() => {
    document.documentElement.setAttribute('data-bs-theme', motyw);
    try {
      localStorage.setItem(KLUCZ, motyw);
    } catch {
      // Nie da sie zapamietac - motyw dziala do zamkniecia karty
    }
  }, [motyw]);

  /*
   * Gdy uzytkownik NIE wybral nic recznie, chodzimy za ustawieniem systemu -
   * takze wtedy, gdy zmieni je w trakcie (np. o zmierzchu). Po pierwszym
   * kliknieciu przelacznika jego wybor jest wazniejszy i systemu juz
   * nie sluchamy.
   */
  useEffect(() => {
    if (odczytajZapisany()) {
      return undefined;
    }

    const zapytanie = window.matchMedia('(prefers-color-scheme: light)');
    const reaguj = (e) => setMotyw(e.matches ? 'light' : 'dark');

    zapytanie.addEventListener('change', reaguj);
    return () => zapytanie.removeEventListener('change', reaguj);
  }, []);

  const przelacz = useCallback(
    () => setMotyw((poprzedni) => (poprzedni === 'dark' ? 'light' : 'dark')),
    []);

  return (
    <MotywContext.Provider value={{ motyw, przelacz, ciemny: motyw === 'dark' }}>
      {children}
    </MotywContext.Provider>
  );
}

export function useMotyw() {
  const kontekst = useContext(MotywContext);
  if (!kontekst) {
    throw new Error('useMotyw dziala tylko wewnatrz <MotywProvider>');
  }
  return kontekst;
}
