import axios from 'axios';
import i18n from 'i18next';
import { formatDateTime } from '../utils/dates';

/**
 * Jedno miejsce, przez ktore ida WSZYSTKIE zapytania do backendu.
 *
 * Dzieki temu ustawienia (ciasteczka, token CSRF, jezyk) konfigurujemy raz,
 * a nie przy kazdym wywolaniu z osobna.
 */
const client = axios.create({
  // Adres wzgledny - Vite przekieruje go na localhost:8080 (patrz vite.config.js)
  baseURL: '/api',

  /*
   * Bez tego przegladarka NIE wysyla ciasteczka sesji (JSESSIONID)
   * i backend przy kazdym zapytaniu widzi anonimowego uzytkownika.
   */
  withCredentials: true,

  /*
   * Ochrona CSRF: axios czyta ciasteczko XSRF-TOKEN i sam wklada je
   * do naglowka X-XSRF-TOKEN przy kazdym POST. Dokladnie tego oczekuje
   * nasza konfiguracja Spring Security.
   */
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

/** Czy przegladarka ma teraz ciasteczko z tokenem CSRF. */
function hasCsrfCookie() {
  return document.cookie.split('; ').some((c) => c.startsWith('XSRF-TOKEN='));
}

/**
 * Pobiera token CSRF (endpoint zaklada ciasteczko {@code XSRF-TOKEN}).
 *
 * Wywolujemy to zawsze, gdy ciasteczka moglo zabraknac - przy starcie
 * aplikacji i po wylogowaniu.
 */
export function refreshCsrfToken() {
  return client.get('/auth/csrf');
}

/*
 * =============================================================================
 *  RATUNEK NA BRAKUJACY TOKEN CSRF
 * =============================================================================
 *
 * BLAD, KTORY TO WYMUSIL. Po wylogowaniu pierwsze klikniecie "Zaloguj"
 * konczylo sie komunikatem "Wystapil nieoczekiwany blad", a drugie dzialalo
 * normalnie. Wygladalo to na losowa usterke, a bylo w pelni powtarzalne.
 *
 * SKAD SIE BRALO. Wylogowanie w Spring Security KASUJE ciasteczko
 * XSRF-TOKEN (robi to CsrfLogoutHandler - i slusznie, bo token nalezal do
 * poprzedniej sesji). Frontend nie pobieral go ponownie, wiec kolejne
 * zapytanie POST szlo bez tokenu i serwer je odrzucal. Dopiero odpowiedz
 * z bledem zakladala nowe ciasteczko - stad "za drugim razem dziala".
 *
 * JAK TO NAPRAWIAMY. Dwustopniowo, bo to dwie rozne rzeczy:
 *   1. AuthContext pobiera nowy token zaraz po wylogowaniu - to usuwa
 *      przyczyne w jedynym miejscu, w ktorym ona powstaje;
 *   2. ten przechwytywacz jest siatka bezpieczenstwa na wszystkie pozostale
 *      sposoby utraty ciasteczka (wygasniecie, wyczyszczenie danych strony,
 *      druga karta, ktora sie wylogowala).
 *
 * DLACZEGO TO NIE UKRYWA PRAWDZIWYCH BLEDOW. Powtarzamy WYLACZNIE wtedy,
 * gdy w chwili wysylki ciasteczka w ogole nie bylo. Zle haslo daje ten sam
 * kod odpowiedzi, ale ciasteczko jest wtedy na miejscu - taki blad przechodzi
 * do uzytkownika nietkniety. Powtarzamy tez tylko RAZ.
 */
/*
 * =============================================================================
 *  ZMIANA KONTA W INNEJ KARCIE
 * =============================================================================
 *
 * BLAD, KTORY TO WYMUSIL. Po zalogowaniu sie na drugie konto w innej karcie
 * STARA karta dalej wygladala jak poprzednie konto, ale pokazywala dane
 * nowego - np. jego znajomych na profilu poprzedniej osoby. Nic nie
 * ostrzegalo, ze cokolwiek sie zmienilo.
 *
 * SKAD SIE BRALO. Ciasteczko sesji nalezy do CALEJ przegladarki, a nie do
 * pojedynczej karty. Zalogowanie sie gdziekolwiek podmienia je wszedzie,
 * wiec od tej chwili stara karta wysyla zapytania juz jako nowe konto -
 * mimo ze w Reakcie siedzi jeszcze poprzedni uzytkownik. Widac wtedy
 * mieszanke: naglowki i menu z jednego konta, dane z drugiego.
 *
 * CZEGO SIE NIE DA ZROBIC. Nie da sie utrzymac dwoch kont naraz w jednej
 * przegladarce - jedno ciasteczko to jedna tozsamosc. Zeby prowadzic rozmowe
 * "sam ze soba", trzeba uzyc dwoch OSOBNYCH przegladarek albo okna prywatnego
 * (incognito), ktore ma wlasny zestaw ciasteczek.
 *
 * CO ROBIMY. Serwer dopisuje do kazdej odpowiedzi naglowek X-Current-User
 * z nazwa konta, ktore widzi jako zalogowane. Jesli rozni sie ona od tego,
 * co pokazuje ta karta, przeladowujemy aplikacje - dzieki temu karta
 * uczciwie pokazuje konto, na ktore naprawde jest zalogowana, zamiast
 * mieszac dwa. Przeladowanie jest tu wlasciwa reakcja, bo unieważnia
 * WSZYSTKIE dane poprzedniego konta naraz; wybieranie ich po jednym
 * predzej czy pozniej zostawiloby gdzies stary fragment.
 */
const CURRENT_USER_HEADER = 'x-current-user';

/** Kogo ta karta pokazuje. Ustawia AuthContext przy kazdej zmianie konta. */
let shownUser = null;

export function rememberShownUser(username) {
  shownUser = username ?? null;
}

/** Wywolywane raz, gdy serwer zglosi inne konto niz to na ekranie. */
let onAccountSwitch = null;

export function setAccountSwitchHandler(handler) {
  onAccountSwitch = handler;
}

function checkAccount(response) {
  const serverUser = response?.headers?.[CURRENT_USER_HEADER];

  /*
   * Brak naglowka nie znaczy "wylogowano". Dostaja go tylko odpowiedzi
   * zalogowanych zapytan do /api - przy 401 albo przy zapytaniu
   * anonimowym naglowka nie ma i nie ma tu czego porownywac.
   */
  if (!serverUser || !shownUser || serverUser === shownUser) {
    return;
  }

  const previous = shownUser;
  // Zerujemy od razu, zeby rownolegle zapytania nie zglosily tego drugi raz
  shownUser = null;
  onAccountSwitch?.(previous, serverUser);
}

client.interceptors.response.use(
  (response) => {
    checkAccount(response);
    return response;
  },
  async (error) => {
    checkAccount(error.response);
    const request = error.config;
    const status = error.response?.status;

    const worthRetrying =
      request
      && !request._csrfRetry
      && (status === 401 || status === 403)
      && !['get', 'head', 'options'].includes((request.method ?? 'get').toLowerCase())
      && !request.url?.includes('/auth/csrf')
      && !request._hadCsrfCookie;

    if (!worthRetrying) {
      return Promise.reject(error);
    }

    request._csrfRetry = true;
    try {
      await refreshCsrfToken();
    } catch {
      // Serwer nie odpowiada - oddajemy pierwotny blad, bo to on jest prawdziwy
      return Promise.reject(error);
    }
    return client(request);
  }
);

/*
 * Zapisujemy przy zapytaniu, czy ciasteczko BYLO w chwili wysylki. Sprawdzenie
 * tego dopiero w obsludze bledu byloby za pozne: odpowiedz z bledem zaklada
 * juz nowe ciasteczko, wiec zawsze wygladaloby na obecne.
 */
client.interceptors.request.use((request) => {
  request._hadCsrfCookie = hasCsrfCookie();
  return request;
});

/**
 * Ustawia jezyk wysylany do backendu w naglowku Accept-Language.
 *
 * Dzieki temu przelacznik PL/EN zmienia nie tylko napisy w interfejsie,
 * ale takze komunikaty bledow walidacji, ktore generuje serwer.
 */
export function setRequestLanguage(language) {
  client.defaults.headers.common['Accept-Language'] = language;
}

/**
 * Zamienia blad z axiosa na coś, co da sie pokazac uzytkownikowi.
 *
 * Backend zwraca bledy w jednym formacie (klasa ErrorResponse), np.:
 * {
 *   "status": 422,
 *   "message": "Blad walidacji...",
 *   "errors": [ { "field": "email", "message": "Podaj poprawny adres" } ]
 * }
 *
 * @returns {{message: string|null, messageKey: string|null, fieldErrors: Object}}
 *          messageKey ustawiamy tylko wtedy, gdy tekst musi pochodzic
 *          z tlumaczen frontendu (bo serwer nic nie odpowiedzial).
 */
export function describeError(error) {
  // Serwer nie odpowiedzial w ogole - najczesciej backend jest wylaczony
  if (!error.response) {
    return { message: null, messageKey: 'errors.network', fieldErrors: {} };
  }

  const data = error.response.data;

  // Bledy przy konkretnych polach formularza -> { email: "Podaj poprawny adres" }
  const fieldErrors = {};
  if (Array.isArray(data?.errors)) {
    for (const error of data.errors) {
      fieldErrors[error.field] = error.message;
    }
  }

  /*
   * Gdy mamy bledy przy konkretnych polach, NIE pokazujemy komunikatu
   * ogolnego. Serwer wysyla wtedy "Blad walidacji. Szczegoly w polu 'errors'" -
   * to zdanie jest dla programisty, nie dla uzytkownika, ktory i tak widzi
   * czerwony tekst pod kazdym niepoprawnym polem.
   */
  const hasFieldErrors = Object.keys(fieldErrors).length > 0;

  /*
   * Termin konca kary DOKLADAMY TUTAJ, a nie bierzemy gotowego z serwera.
   *
   * Wczesniej serwer wklejal date do komunikatu sam i wychodzilo z tego
   * „zakaz do 15:55" przy karze nalozonej o 16:55 - bo serwer liczy czas
   * w UTC i o strefie uzytkownika nie wie nic. Teraz przysyla sama chwile,
   * a godzine sklada przegladarka, ktora jako jedyna zna zegar uzytkownika.
   *
   * Robimy to w jednym miejscu, a nie przy kazdym wyswietlaniu bledu:
   * wszystkie ekrany pokazuja to, co odda `message`, wiec dolozenie terminu
   * tutaj dziala wszedzie naraz i nigdzie nie da sie o nim zapomniec.
   */
  let message = hasFieldErrors ? null : (data?.message ?? null);
  if (message && data?.deadline) {
    message += ' ' + i18n.t('errors.until', {
      date: formatDateTime(data.deadline, i18n.language),
    });
  }

  return {
    // Komunikat z serwera jest juz przetlumaczony (wyslalismy Accept-Language)
    message,
    messageKey: hasFieldErrors || data?.message ? null : 'errors.unknown',
    fieldErrors,
  };
}

export default client;
