import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import client, {
  refreshCsrfToken, rememberShownUser, setAccountSwitchHandler,
} from '../api/client';

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

  /*
   * Kogo ta karta pokazuje - do porownania z tym, kogo widzi serwer.
   * Klient HTTP sprawdza to przy kazdej odpowiedzi; szczegoly bledu,
   * ktory to wymusil, sa w komentarzu w api/client.js.
   */
  useEffect(() => {
    rememberShownUser(user?.username ?? null);
  }, [user]);

  useEffect(() => {
    setAccountSwitchHandler((previous, current) => {
      /*
       * Zapamietujemy, co sie stalo, i przeladowujemy strone. Zapis
       * przezywa przeladowanie, wiec po starcie mozna pokazac czlowiekowi
       * powod - inaczej strona odswiezylaby sie "sama z siebie", co
       * wyglada jak usterka.
       */
      try {
        sessionStorage.setItem('accountSwitch', JSON.stringify({ previous, current }));
      } catch {
        // Tryb prywatny moze zabronic zapisu - przeladowanie i tak jest wazniejsze
      }
      window.location.reload();
    });

    return () => setAccountSwitchHandler(null);
  }, []);

  /*
   * Sprawdzenie tozsamosci w chwili POWROTU do karty.
   *
   * Podmiana konta wychodzi na jaw przy pierwszym zapytaniu do serwera,
   * bo kazda odpowiedz niesie naglowek z nazwa zalogowanego konta. Karta
   * pozostawiona w tle wysyla jednak zapytania rzadko (licznik powiadomien
   * co minute), wiec bez tego czlowiek zdazylby kliknac kilka rzeczy jako
   * nie ta osoba, co trzeba.
   *
   * Powrot do karty to dokladnie ta chwila, w ktorej ma znaczenie, kim
   * jestesmy - i dlatego pytamy wlasnie wtedy. Samo zapytanie wystarczy:
   * odpowiedz przechodzi przez ten sam mechanizm co kazda inna.
   */
  useEffect(() => {
    if (!user) {
      return undefined;
    }

    function verify() {
      if (document.visibilityState === 'visible') {
        client.get('/auth/me').catch(() => {
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
        /*
         * Najpierw token CSRF. Ten endpoint ustawia ciasteczko XSRF-TOKEN,
         * bez ktorego pierwszy POST (logowanie, rejestracja) zostalby
         * odrzucony przez Spring Security.
         */
        await refreshCsrfToken();
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

      /*
       * NOWY TOKEN CSRF PO WYLOGOWANIU - i nie jest to ostroznosc na zapas.
       * Spring Security przy wylogowaniu kasuje ciasteczko XSRF-TOKEN, bo
       * nalezalo ono do poprzedniej sesji. Bez tej linijki pierwsze
       * nastepne klikniecie "Zaloguj" szlo bez tokenu i konczylo sie
       * komunikatem "Wystapil nieoczekiwany blad" - a drugie juz dzialalo,
       * bo odpowiedz z bledem zakladala nowe ciasteczko.
       */
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
