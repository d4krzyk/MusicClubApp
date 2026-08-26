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
const ThemeContext = createContext(null);

const STORAGE_KEY = 'motyw';

/** Odczyt zapisanego wyboru. Osobna funkcja, bo localStorage potrafi rzucic wyjatkiem. */
function readStored() {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    /*
     * W trybie prywatnym (i przy zablokowanych danych witryn) samo siegniecie
     * po localStorage konczy sie wyjatkiem. Wtedy motyw po prostu nie jest
     * zapamietywany - aplikacja ma dzialac dalej, a nie sie wywalac.
     */
    return null;
  }
}

export function ThemeProvider({ children }) {
  /*
   * Stan startowy czytamy z ATRYBUTU, a nie z localStorage. Skrypt
   * w index.html juz rozstrzygnal, co pokazac (zapisany wybor albo ustawienie
   * systemu) - powtarzanie tej logiki tutaj groziloby tym, ze obie wersje
   * z czasem sie rozjada.
   */
  const [theme, setTheme] = useState(
    () => document.documentElement.getAttribute('data-bs-theme') ?? 'dark');

  useEffect(() => {
    document.documentElement.setAttribute('data-bs-theme', theme);
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Nie da sie zapamietac - motyw dziala do zamkniecia karty
    }
  }, [theme]);

  /*
   * Gdy uzytkownik NIE wybral nic recznie, chodzimy za ustawieniem systemu -
   * takze wtedy, gdy zmieni je w trakcie (np. o zmierzchu). Po pierwszym
   * kliknieciu przelacznika jego wybor jest wazniejszy i systemu juz
   * nie sluchamy.
   */
  useEffect(() => {
    if (readStored()) {
      return undefined;
    }

    const query = window.matchMedia('(prefers-color-scheme: light)');
    const reaguj = (e) => setTheme(e.matches ? 'light' : 'dark');

    query.addEventListener('change', reaguj);
    return () => query.removeEventListener('change', reaguj);
  }, []);

  const toggle = useCallback(
    () => setTheme((previous) => (previous === 'dark' ? 'light' : 'dark')),
    []);

  return (
    <ThemeContext.Provider value={{ theme, toggle, dark: theme === 'dark' }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const kontekst = useContext(ThemeContext);
  if (!kontekst) {
    throw new Error('useTheme dziala tylko wewnatrz <ThemeProvider>');
  }
  return kontekst;
}
