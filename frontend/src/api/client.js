import axios from 'axios';
import i18n from 'i18next';
import { formatDateTime } from '../utils/dates';

/** Jedno miejsce, przez ktore ida WSZYSTKIE zapytania do backendu. */
const client = axios.create({
  // Adres wzgledny - Vite przekieruje go na localhost:8080 (patrz vite.config.js)
  baseURL: '/api',

  /*
   * Bez tego przegladarka NIE wysyla ciasteczka sesji (JSESSIONID) i backend przy kazdym zapytaniu
   * widzi anonimowego uzytkownika.
   */
  withCredentials: true,

  /*
   * Ochrona CSRF: axios czyta ciasteczko XSRF-TOKEN i sam wklada je do naglowka X-XSRF-TOKEN przy
   * kazdym POST.
   */
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

/** Czy przegladarka ma teraz ciasteczko z tokenem CSRF. */
function hasCsrfCookie() {
  return document.cookie.split('; ').some((c) => c.startsWith('XSRF-TOKEN='));
}

/** Pobiera token CSRF (endpoint zaklada ciasteczko XSRF-TOKEN). */
export function refreshCsrfToken() {
  return client.get('/auth/csrf');
}

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

  /* Brak naglowka nie znaczy "wylogowano". */
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

/* Zapisujemy przy zapytaniu, czy ciasteczko BYLO w chwili wysylki. */
client.interceptors.request.use((request) => {
  request._hadCsrfCookie = hasCsrfCookie();
  return request;
});

/** Ustawia jezyk wysylany do backendu w naglowku Accept-Language. */
export function setRequestLanguage(language) {
  client.defaults.headers.common['Accept-Language'] = language;
}

/** Zamienia blad z axiosa na GOTOWY tekst do pokazania uzytkownikowi. */
export function describeError(error, fallbackKey = 'errors.unknown') {
  // Serwer nie odpowiedzial w ogole - najczesciej backend jest wylaczony
  if (!error.response) {
    return { message: i18n.t('errors.network'), fieldErrors: {} };
  }

  const data = error.response.data;

  // Bledy przy konkretnych polach formularza -> { email: "Podaj poprawny adres" }
  const fieldErrors = {};
  if (Array.isArray(data?.errors)) {
    for (const error of data.errors) {
      fieldErrors[error.field] = error.message;
    }
  }

  /* Gdy mamy bledy przy konkretnych polach, NIE pokazujemy komunikatu ogolnego. */
  const hasFieldErrors = Object.keys(fieldErrors).length > 0;

  /* Termin konca kary DOKLADAMY TUTAJ, a nie bierzemy gotowego z serwera. */
  if (hasFieldErrors) {
    return { message: null, fieldErrors };
  }

  // Komunikat z serwera jest juz przetlumaczony (wyslalismy Accept-Language);
  // gdy go nie ma, siegamy po tekst zapasowy z tlumaczen frontendu
  let message = data?.message ?? i18n.t(fallbackKey);

  if (data?.deadline) {
    message += ' ' + i18n.t('errors.until', {
      date: formatDateTime(data.deadline, i18n.language),
    });
  }

  return { message, fieldErrors };
}

export default client;
