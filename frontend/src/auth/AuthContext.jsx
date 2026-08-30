import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import {
  refreshCsrfToken, rememberShownUser, setAccountSwitchHandler,
} from '../api/client';
import * as konto from '../api/konto';

/** Przechowuje informacje o zalogowanym uzytkowniku i udostepnia je calej aplikacji. */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // null = nikt nie jest zalogowany
  const [user, setUser] = useState(null);

  /* Kluczowy stan. */
  const [checkingSession, setCheckingSession] = useState(true);

  /* Kogo ta karta pokazuje - do porownania z tym, kogo widzi serwer. */
  useEffect(() => {
    rememberShownUser(user?.username ?? null);
  }, [user]);

  useEffect(() => {
    setAccountSwitchHandler((previous, current) => {
      /* Zapamietujemy, co sie stalo, i przeladowujemy strone. */
      try {
        sessionStorage.setItem('accountSwitch', JSON.stringify({ previous, current }));
      } catch {
        // Tryb prywatny moze zabronic zapisu - przeladowanie i tak jest wazniejsze
      }
      window.location.reload();
    });

    return () => setAccountSwitchHandler(null);
  }, []);

  /* Sprawdzenie tozsamosci w chwili POWROTU do karty. */
  useEffect(() => {
    if (!user) {
      return undefined;
    }

    function verify() {
      if (document.visibilityState === 'visible') {
        konto.ktoJestem().catch(() => {
          // 401 znaczy, ze sesja zniknela - obsluguja to zwykle sciezki bledow
        });
      }
    }

    window.addEventListener('focus', verify);
    document.addEventListener('visibilitychange', verify);
    return () => {
      window.removeEventListener('focus', verify);
      document.removeEventListener('visibilitychange', verify);
    };
  }, [user]);

  // Uruchamia sie raz, przy pierwszym otwarciu aplikacji
  useEffect(() => {
    async function checkCurrentUser() {
      try {
        /* Najpierw token CSRF. */
        await refreshCsrfToken();
      } catch {
        // Backend nie odpowiada - blad pokaze sie przy pierwszej akcji uzytkownika
      }

      try {
        setUser(await konto.ktoJestem());
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
    const zalogowany = await konto.zaloguj(username, password, rememberMe);
    setUser(zalogowany);
    return zalogowany;
  }, []);

  const register = useCallback(async (data) => {
    // Rejestracja NIE loguje automatycznie - backend tylko zaklada konto
    return konto.zarejestruj(data);
  }, []);

  /** Zmiana loginu i e-maila we wlasnym profilu. */
  const updateProfile = useCallback(async (data) => {
    const zmienione = await konto.zmienDane(data);
    setUser(zmienione);
    return zmienione;
  }, []);

  /** Podmienia dane zalogowanego uzytkownika w stanie aplikacji. */
  const refreshUser = useCallback((newData) => {
    setUser(newData);
  }, []);

  /** Zmiana hasla. Nic nie zwraca - serwer odsyla 204 bez tresci. */
  const changePassword = useCallback(async (data) => {
    await konto.zmienHaslo(data);
  }, []);

  const logout = useCallback(async () => {
    try {
      await konto.wyloguj();
    } finally {
      /* Czyscimy uzytkownika nawet gdy zapytanie sie nie powiodlo. */
      setUser(null);

      /* NOWY TOKEN CSRF PO WYLOGOWANIU - i nie jest to ostroznosc na zapas. */
      refreshCsrfToken().catch(() => {
        // Nie udalo sie? Zostaje przechwytywacz w client.js, ktory powtorzy
        // pierwsze nieudane zapytanie po pobraniu tokenu.
      });
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
