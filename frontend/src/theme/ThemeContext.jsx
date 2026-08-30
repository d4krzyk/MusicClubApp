import { createContext, useCallback, useContext, useEffect, useState } from 'react';

/** Motyw jasny/ciemny. */
const ThemeContext = createContext(null);

const STORAGE_KEY = 'motyw';

/** Odczyt zapisanego wyboru. Osobna funkcja, bo localStorage potrafi rzucic wyjatkiem. */
function readStored() {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    /*
     * W trybie prywatnym (i przy zablokowanych danych witryn) samo siegniecie po localStorage
     * konczy sie wyjatkiem.
     */
    return null;
  }
}

export function ThemeProvider({ children }) {
  /* Stan startowy czytamy z ATRYBUTU, a nie z localStorage. */
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
   * Gdy uzytkownik NIE wybral nic recznie, chodzimy za ustawieniem systemu - takze wtedy, gdy
   * zmieni je w trakcie (np. o zmierzchu).
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
