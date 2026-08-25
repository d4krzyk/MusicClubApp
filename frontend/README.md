# Frontend — MusicClub

React + Vite. Realizuje wymaganie nr 12b (frontend dla REST API) oraz domyka
nr 2 (dwa języki — menu i etykiety po stronie interfejsu).

## Uruchomienie

Backend i baza muszą już działać (`docker compose up -d` + `mvn spring-boot:run`).

```bash
cd frontend
npm install     # tylko za pierwszym razem
npm run dev     # http://localhost:5173
```

## Zależności — dlaczego akurat te

Cztery paczki, każda z konkretnym powodem. Świadomie **nie ma** Reduxa,
Tailwinda ani biblioteki komponentów — projekt jest za mały, żeby się opłacały,
a każda z nich to kolejna rzecz do wytłumaczenia na obronie.

| Paczka | Po co |
|--------|-------|
| `react-router-dom` | nawigacja między stronami i ochrona tras |
| `axios` | sam przepisuje ciasteczko CSRF do nagłówka `X-XSRF-TOKEN` |
| `i18next` + `react-i18next` | dwa języki (wymaganie nr 2) |

Style to zwykły CSS w `src/styles.css`, kolory jako zmienne w `:root`.

## Struktura

```
src/
├── main.jsx              # punkt wejścia
├── App.jsx               # mapa adresów (routing)
├── api/client.js         # jedna instancja axios: ciasteczka, CSRF, język, błędy
├── auth/
│   ├── AuthContext.jsx   # kto jest zalogowany — pytamy serwer RAZ przy starcie
│   └── RouteGuards.jsx   # TylkoZalogowany / TylkoNiezalogowany
├── i18n/                 # konfiguracja + pl.json i en.json
├── components/
│   ├── Layout.jsx        # nagłówek, menu, przełącznik PL/EN
│   └── Pole.jsx          # pole formularza z etykietą i błędem
├── pages/                # Login, Register, Home, Users
└── utils/daty.js         # formatowanie dat wg języka
```

## Trzy rzeczy, które warto rozumieć

**Proxy zamiast CORS-a.** `vite.config.js` przekierowuje `/api` na
`localhost:8080`. Przeglądarka widzi jeden adres, więc ciasteczko sesji i token
CSRF działają bez żadnych sztuczek. Bez proxy trzeba by walczyć z CORS-em przy
każdym zapytaniu.

**Stan „jeszcze nie wiem".** `AuthContext` ma flagę `sprawdzanieSesji`. Zanim
serwer odpowie na `GET /api/auth/me`, aplikacja *nie wie*, czy ktoś jest
zalogowany. Bez tej flagi przy każdym odświeżeniu (F5) zalogowany użytkownik
na ułamek sekundy wylatywałby na ekran logowania.

**Język zmienia też komunikaty serwera.** Przełącznik PL/EN ustawia nagłówek
`Accept-Language` w axiosie, więc błędy walidacji wracają z backendu już
przetłumaczone. Frontend nie tłumaczy ich sam — pokazuje to, co przyszło.

## Sprawdzone przypadki

Przetestowane w przeglądarce (Playwright), przechodzą:

- niezalogowany wchodzący na `/users` → przekierowanie na logowanie, po
  zalogowaniu powrót dokładnie na `/users`
- zalogowany wchodzący na `/login` → przekierowanie na stronę główną
- odświeżenie strony (F5) nie wylogowuje
- błędne hasło → 401 z komunikatem w wybranym języku
- błędy walidacji → podświetlone konkretne pola, bez technicznego bannera
- zajęty login → czytelna informacja przy polu
- stronicowanie, sortowanie i wyszukiwanie na liście użytkowników
- zmiana filtra cofa na pierwszą stronę
- brak wyników → komunikat zamiast pustej tabeli
- wyłączony backend → „Nie można połączyć się z serwerem", nie biała strona
- podwójne kliknięcie „Zaloguj" zablokowane na czas wysyłania
