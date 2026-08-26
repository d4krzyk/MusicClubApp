import axios from 'axios';

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

  return {
    // Komunikat z serwera jest juz przetlumaczony (wyslalismy Accept-Language)
    message: hasFieldErrors ? null : (data?.message ?? null),
    messageKey: hasFieldErrors || data?.message ? null : 'errors.unknown',
    fieldErrors,
  };
}

export default client;
