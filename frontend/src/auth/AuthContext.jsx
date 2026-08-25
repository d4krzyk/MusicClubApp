import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import client from '../api/client';

/**
 * Przechowuje informacje o zalogowanym uzytkowniku i udostepnia je
 * calej aplikacji.
 *
 * <p>Bez tego kazda strona musialaby sama pytac serwer "kto jest zalogowany?".
 * Tutaj pytamy RAZ przy starcie, a wynik jest dostepny wszedzie przez
 * hook {@code useAuth()}.</p>
 */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // null = nikt nie jest zalogowany
  const [user, setUser] = useState(null);

  /*
   * Kluczowy stan. Zanim serwer odpowie, kim jesteśmy, NIE WIEMY, czy
   * uzytkownik jest zalogowany. Bez tego znacznika aplikacja przez ulamek
   * sekundy uznawalaby kazdego za niezalogowanego i wyrzucala na ekran
   * logowania nawet zalogowana osobe - klasyczny migajacy ekran przy F5.
   */
  const [sprawdzanieSesji, setSprawdzanieSesji] = useState(true);

  // Uruchamia sie raz, przy pierwszym otwarciu aplikacji
  useEffect(() => {
    async function sprawdzKtoZalogowany() {
      try {
        /*
         * Najpierw token CSRF. Ten endpoint ustawia ciasteczko XSRF-TOKEN,
         * bez ktorego pierwszy POST (logowanie, rejestracja) zostalby
         * odrzucony przez Spring Security.
         */
        await client.get('/auth/csrf');
      } catch {
        // Backend nie odpowiada - blad pokaze sie przy pierwszej akcji uzytkownika
      }

      try {
        const odpowiedz = await client.get('/auth/me');
        setUser(odpowiedz.data);
      } catch {
        // 401 to normalna sytuacja: nikt nie jest zalogowany
        setUser(null);
      } finally {
        setSprawdzanieSesji(false);
      }
    }

    sprawdzKtoZalogowany();
  }, []);

  const login = useCallback(async (username, password, rememberMe) => {
    const odpowiedz = await client.post('/auth/login', { username, password, rememberMe });
    setUser(odpowiedz.data);
    return odpowiedz.data;
  }, []);

  const register = useCallback(async (dane) => {
    // Rejestracja NIE loguje automatycznie - backend tylko zaklada konto
    const odpowiedz = await client.post('/auth/register', dane);
    return odpowiedz.data;
  }, []);

  /**
   * Zmiana loginu i e-maila we wlasnym profilu.
   *
   * <p>Wynik od razu wstawiamy do stanu - inaczej menu i strona glowna
   * pokazywalyby stary login az do odswiezenia strony.</p>
   */
  const updateProfile = useCallback(async (dane) => {
    const odpowiedz = await client.put('/profile', dane);
    setUser(odpowiedz.data);
    return odpowiedz.data;
  }, []);

  /** Zmiana hasla. Nic nie zwraca - serwer odsyla 204 bez tresci. */
  const changePassword = useCallback(async (dane) => {
    await client.put('/profile/password', dane);
  }, []);

  const logout = useCallback(async () => {
    try {
      await client.post('/auth/logout');
    } finally {
      /*
       * Czyscimy uzytkownika nawet gdy zapytanie sie nie powiodlo.
       * Z punktu widzenia osoby przy komputerze klikniecie "Wyloguj"
       * ma zawsze wylogowac - a sesja po stronie serwera i tak wygasnie.
       */
      setUser(null);
    }
  }, []);

  const wartosc = {
    user,
    sprawdzanieSesji,
    login,
    register,
    logout,
    updateProfile,
    changePassword,
  };

  return <AuthContext.Provider value={wartosc}>{children}</AuthContext.Provider>;
}

/** Skrot do korzystania z kontekstu: {@code const { user, logout } = useAuth();} */
export function useAuth() {
  const kontekst = useContext(AuthContext);
  if (!kontekst) {
    throw new Error('useAuth musi byc uzyte wewnatrz <AuthProvider>');
  }
  return kontekst;
}
