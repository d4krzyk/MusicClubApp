# MusicClubApp

Szkielet backendu Spring Boot (REST API) do laczenia ludzi o podobnym guscie muzycznym na podstawie Spotify API.

## Struktura pakietow
- `com.musicclubapp.config` - konfiguracje (Security, I18n)
- `com.musicclubapp.controller` - kontrolery REST
- `com.musicclubapp.dto` - obiekty zadania/odpowiedzi
- `com.musicclubapp.entity` - encje JPA
- `com.musicclubapp.error` - obsluga bledow globalnych
- `com.musicclubapp.repository` - repozytoria
- `com.musicclubapp.service` - logika aplikacji

## Uruchomienie lokalne
1. Zbuduj jar: `mvn -q test`
2. Uruchom aplikacje: `mvn -q spring-boot:run`

Swagger UI: `/swagger-ui/index.html`

