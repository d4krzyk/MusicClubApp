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
  const [checkingSession, setCheckingSession] = useState(true);

  // Uruchamia sie raz, przy pierwszym otwarciu aplikacji
  useEffect(() => {
    async function checkCurrentUser() {
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
        const response = await client.get('/auth/me');
        setUser(response.data);
      } catch {
        // 401 to normalna sytuacja: nikt nie jest zalogowany
        setUser(null);
      } finally {
        setCheckingSession(false);
      }
    }

    checkCurrentUser();
  }, []);

  const login = useCallback(async (username, password, rememberMe) => {
    const response = await client.post('/auth/login', { username, password, rememberMe });
    setUser(response.data);
    return response.data;
  }, []);

  const register = useCallback(async (data) => {
    // Rejestracja NIE loguje automatycznie - backend tylko zaklada konto
    const response = await client.post('/auth/register', data);
    return response.data;
  }, []);

  /**
   * Zmiana loginu i e-maila we wlasnym profilu.
   *
   * <p>Wynik od razu wstawiamy do stanu - inaczej menu i strona glowna
   * pokazywalyby stary login az do odswiezenia strony.</p>
   */
  const updateProfile = useCallback(async (data) => {
    const response = await client.put('/profile', data);
    setUser(response.data);
    return response.data;
  }, []);

  /**
   * Podmienia dane zalogowanego uzytkownika w stanie aplikacji.
   *
   * <p>Uzywane po wgraniu albo usunieciu zdjecia profilowego - endpoint
   * zwraca aktualnego uzytkownika, a my od razu odswiezamy avatar w menu,
   * bez ponownego pytania serwera o sesje.</p>
   */
  const refreshUser = useCallback((newData) => {
    setUser(newData);
  }, []);

  /** Zmiana hasla. Nic nie zwraca - serwer odsyla 204 bez tresci. */
  const changePassword = useCallback(async (data) => {
    await client.put('/profile/password', data);
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

  const value = {
    user,
    checkingSession,
    login,
    register,
    logout,
    updateProfile,
    changePassword,
    refreshUser,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** Skrot do korzystania z kontekstu: {@code const { user, logout } = useAuth();} */
export function useAuth() {
  const kontekst = useContext(AuthContext);
  if (!kontekst) {
    throw new Error('useAuth musi byc uzyte wewnatrz <AuthProvider>');
  }
  return kontekst;
}
