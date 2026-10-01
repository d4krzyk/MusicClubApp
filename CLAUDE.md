# MusicClubApp — kontekst projektu

## Stan

**Projekt został oddany i zaliczony** (wrzesień 2026, „Programowanie w Javie III").
Checklista z `docs/WYMAGANIA.md` jest zamknięta: 20 zagadnień przy 17 wymaganych na 5.
To **przestało być praca na ocenę** — nie trzeba już pilnować punktów z listy ani
tłumaczyć decyzji wymaganiami przedmiotu.

## Cel na teraz

Zrobić z tego **prawdziwą aplikację na Androida w Google Play**, z backendem
postawionym na serwerze, tak żeby każdy mógł ją pobrać i się połączyć.

Zrobione: responsywność na telefonie, PWA, logo i napis MusicClub, krój Poppins,
konfiguracja produkcyjna z HTTPS i migracjami (`docs/WDROZENIE.md`),
zakładka Wydarzenia — etapy 1–2 i wybór kraju (niżej), potwierdzanie adresu
e-mail (niżej), automatyczne uzupełnianie gatunków ulubionych artystów, klany
z przeglądarką, prośbami, tytułami, ankietami i rankingiem (V9–V12).
Zostało: stały adres → sprawdzenie PWA na prawdziwym telefonie → TWA przez
Bubblewrap → Google Play.

## Jak pracujemy

Praca idzie na gałąź `claude/music-club-app-restart-op04i1`, z której jest
**PR #1**. Kolejne commity na tę gałąź aktualizują ten PR. Na `master` nie
wchodzimy — scalenie zostaje po stronie użytkownika.

Wcześniej obowiązywała zasada „Claude nie commituje, tylko oddaje `.patch`".
Użytkownik ją zmienił, gdy nie miał jak nakładać łatek. Jeśli poprosi o łatkę
zamiast commita — robimy łatkę względem tego, co jest na zdalnej gałęzi.

Po nałożeniu łatki zawsze `clean` przy testach (`mvnw.cmd clean test`) — bez tego
tłumaczenia nie przeładowują się do `target/classes` i testy fałszywie czerwienieją.

## Zasada sprawdzania

Zielone testy to nie to samo co działająca aplikacja. Każdą zmianę sprawdzamy
dodatkowo na prawdziwym PostgreSQL i w prawdziwej przeglądarce (Playwright +
Chromium). Przy podejrzeniu błędu — najpierw test, który czerwienieje, potem
poprawka. Historia takich wpadek jest w `docs/WYMAGANIA.md`.

Mierzyć, nie szacować. Kilka rzeczy w tym projekcie wyglądało dobrze i było
zepsute: `getBBox()` na tekście SVG zwraca pudełko wiersza, a nie wysokość
liter; `gradientUnits="userSpaceOnUse"` liczy gradient **po** transformacji
elementu; nginx nadpisywał `X-Forwarded-Proto`, przez co znikała flaga `Secure`
na ciasteczku, a logowanie działało dalej i nikt by tego nie zauważył.

Dwie kolejne z zakładki Wydarzenia:
- **Przelew strony mierzy się bez `isMobile`.** Przy emulacji telefonu
  Playwright/Chromium poszerza układ do szerokości treści, więc
  `scrollWidth - innerWidth` wychodzi 0 nawet wtedy, gdy pasek wystaje o 11 px.
  Mierzyć w oknie o sztywnej szerokości: prawa krawędź najdalszego elementu
  minus `clientWidth`.
- **Blok tekstowy w Javie (`"""`) obcina spacje na końcach linii.**
  `"WHERE " + warunek` w bloku dał `WHEREe.start_date`. Tam, gdzie spacja na
  końcu linii jest potrzebna, pisze się `\s`.
- **`text-overflow: ellipsis` nie działa na kontenerze `flex`** — goły tekst
  staje się anonimowym elementem flex i jest ucinany w pół słowa bez
  wielokropka. Tekst trzeba włożyć we własny `span` z obcięciem (sprawdzać:
  `scrollWidth > clientWidth` i `textOverflow` na tym spanie).
- **Nagłówki `X-Forwarded-*` od klienta.** Link w wiadomości buduje się
  z adresu zapytania, a nginx domyślnie przepuszczał `X-Forwarded-Host`,
  `Forwarded` i `X-Forwarded-Prefix` od klienta. Podrobione zapytanie przez
  prawdziwy nginx dało link `http://zly2.example/zly/potwierdz-email?token=…`.
  Teraz nginx je nadpisuje/czyści — sprawdzać tą samą próbą, nie na oko.
- **Actuator sam dokłada sprawdzanie SMTP** do `/actuator/health`, gdy jest
  starter poczty. Przy pustym `MAIL_HOST` zdrowie wychodziłoby DOWN i Docker
  restartowałby zdrowy backend — stąd `management.health.mail.enabled=false`.
- **Apostrof w komunikatach z argumentami.** Gdy do `messages*.properties`
  idą argumenty (`{0}`), tekst przechodzi przez `MessageFormat` i pojedynczy
  `'` znika („Weve”). Tam pisze się `''`.
- **Wiadomość wychodzi po zatwierdzeniu transakcji.** Test z `@Transactional`
  na klasie nigdy jej nie zobaczy — `EmailVerificationFlowTest` jest bez niego
  i sprząta konta sam.
- **`ddl-auto=update` nie zdejmuje `NOT NULL`.** Kolumna, która przestała
  być wymagana, zostaje wymagana w bazie z pracy lokalnej — stąd lista
  `NULLABLE` w `EnumConstraintRefresher`. Na produkcji robi to migracja.
- **Konta sprzed V5 mają `security_stamp = NULL`** (znacznik powstaje przy
  pierwszej zmianie hasła). Porównania ze znacznikiem przez `COALESCE(…, '')`.
- **Zegar testowy a północ w Polsce.** Testy importu stoją na 28.09 10:00 UTC;
  +12 h to już 29.09 w Warszawie, więc „dziś" się przesuwa i udawany
  Ticketmaster przestaje odpowiadać. Przesunięcia w testach — w obrębie dnia.

## Konfiguracja produkcyjna

Opisana w `docs/WDROZENIE.md`. W skrócie:

- `docker-compose.prod.yml` z dwoma profilami: `tunel` (Cloudflare, darmowy,
  bez domeny, adres losowy) i `domena` (Caddy z Let's Encrypt).
- Profil Springa `prod` (`application-prod.properties`): `ddl-auto=validate`
  plus Flyway, `Secure`/`HttpOnly`/`SameSite` na ciasteczku,
  `forward-headers-strategy`, ciche logi, Swagger wyłączony, pusty CORS.
- Migracje w `src/main/resources/db/migration/`. Poza produkcją Flyway jest
  **wyłączony** — w pracy nad kodem zostaje `ddl-auto=update`, a testy chodzą
  na H2 z `create-drop`. Migracji już wypuszczonej się nie edytuje.

## Do zrobienia przed wystawieniem na świat

- **Uzupełnić dane administratora w `.env`** (`LEGAL_CONTROLLER`,
  `LEGAL_CONTACT_EMAIL`, `LEGAL_HOSTING`, `LEGAL_MAIL_PROVIDER`) i dać
  regulamin z polityką do przeczytania prawnikowi — bez tego polityka
  prywatności jest niepełna, a Google Play jej wymaga.
- **Ustawić własne `REMEMBER_ME_KEY` i `ADMIN_PASSWORD` w `.env`.** Na produkcji
  nie mają wartości domyślnych, więc bez nich nic nie wystartuje — ale wartość
  trzeba wygenerować (`openssl rand -base64 48`). Domyślny klucz z repozytorium
  pozwala podpisać ciasteczko „zapamiętaj mnie" na dowolne konto.
- Wgrane pliki (`app.uploads.dir`) leżą na wolumenie Dockera. Na jednym serwerze
  to wystarcza; na hostingu kontenerowym bez trwałego dysku znikną przy każdym
  wdrożeniu — wtedy potrzebny będzie magazyn obiektowy.
- Manifest PWA ma `background_color` i `theme_color` tylko jasne, więc ekran
  startowy jest biały także w ciemnym motywie.
- Poczta (opcjonalna, ale bez niej nikt nie potwierdza adresu): `MAIL_HOST`,
  `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` i przy własnej domenie
  `APP_PUBLIC_URL`. Gmail z hasłem aplikacji albo Brevo — `docs/WDROZENIE.md`.
  `MAIL_HOST` bez poprawnego nadawcy = backend nie wstaje (celowo).

## Wydarzenia

Koncerty w Polsce z **Ticketmaster Discovery API** (darmowy klucz,
5000 zapytań/dzień, `TICKETMASTER_API_KEY`). Inne źródła sprawdzone i odrzucone:
Facebook nie daje cudzych wydarzeń od 2018, Songkick tylko płatnie,
Bandsintown wymaga pisemnej zgody, Eventbrite wyłączył wyszukiwanie w 2020,
Going./eBilet nie mają API. Ticketmaster zwrócił 801 koncertów w Polsce,
także klubowych (Progresja, Hydrozagadka, Drizzly Grizzly).

Etap 1 (zrobiony): import co 6 h do `music_events` (migracja V2; po nieudanym
imporcie ponowna próba co kwadrans), lista z grupowaniem serii („Koncert przy
świecach" grany co wieczór to jedna karta z „+3 terminy"), filtr miasta,
szukanie, strona wydarzenia z mapą i biletami.

Etap 2 (zrobiony, migracja V3): „Zainteresowany" / „Biorę udział" / rezygnacja,
lista uczestników i trzy widoki listy — **Dla ciebie**, Najbliższe, Moje.
- Lista uczestników: tylko „idę"; widzą ją wszyscy zalogowani, a kto zaznaczy
  „nie pokazuj mnie", liczy się do licznika bez nazwy konta (wariant A —
  przyjęty domyślnie, użytkownik nie wybrał innego). Siebie widzi się zawsze.
- „Dla ciebie" (`EventMatchService`): ulubiony artysta w składzie albo w nazwie
  wydarzenia 100 pkt, wykonawca ulubionego utworu 70, wspólny gatunek 15
  (maks. 3), wspólna rodzina gatunków 8 (maks. 2), zapisany znajomy 20
  (maks. 3). Przy każdej pozycji lista powodów. Nazwy porównywane po
  `NameKeys` (bez wielkości liter, polskich znaków i „The").
- Gatunki wykonawców koncertów z Last.fm w `performer_tags` — do 300 na przebieg
  importu, ważne 60 dni; awaria Last.fm nie zapisuje pustych tagów. Bez
  `LASTFM_API_KEY` zostają ogólne etykiety Ticketmastera (a gust użytkownika
  i tak ma gatunki tylko z Last.fm).
- Wydarzenie, które zniknęło z Ticketmastera, a ktoś był na nie zapisany, nie
  jest kasowane: dostaje `withdrawn_at`, znika z list poza „Moje", a jego
  strona to wyjaśnia. Wraca, gdy Ticketmaster znów je pokaże. Po 30 dniach od
  daty znika razem z zapisami.

Wybór kraju (migracja V4): 19 krajów, zapisany na koncie
(`users.events_country`, pusty = PL). Import obejmuje Polskę i każdy wybrany
kraj; nowo wybrany pobiera się od razu w tle (`requestCountry`), a lista pokazuje
„Pobieramy” i sama się odświeża. Najwyżej 60 zapytań na kraj w przebiegu;
znikanie wydarzeń tylko w pokrytym zakresie dat. Co kwadrans odświeżają się
tylko kraje starsze niż 6 h.

Na telefonie (< 576 px) ikony administratora przechodzą do menu konta, a licznik
zgłoszeń na awatar — z ikoną Wydarzeń pasek administratora wychodził poza
ekran o 41 px przy 360 px. Teraz mieści się od 320 px w górę (zmierzone).

Posty pod wydarzeniem (migracja V7) — „szukam ekipy”: `posts.event_id`
z `ON DELETE SET NULL`. To zwykłe posty — są też na tablicy i na profilu,
z plakietką prowadzącą do wydarzenia (na stronie wydarzenia plakietki nie ma).
Lista `GET /api/posts?event={id}` ma te same zasady widoczności („tylko
znajomi”) i blokad co tablica. Wydarzenie z postami, które zniknęło
z Ticketmastera, jest wycofywane, a nie kasowane (jak przy zapisach); po 30
dniach od daty znika, a posty zostają bez odnośnika. Komentarzy jeszcze nie
ma — gdy dojdą, mają działać także pod tymi postami.

Przypomnienia i push (migracja V8) — opis niżej, w „Powiadomienia push”.
Klany (migracja V9) — niżej, w „Klany”, rozszerzone w V11. Regulamin i polityka
prywatności (V10) oraz pobranie własnych danych — niżej.

## Poczta i potwierdzanie adresów

Z `MAIL_HOST` nowe konto dostaje wiadomość z linkiem (`/potwierdz-email?token=`)
i nie zaloguje się bez kliknięcia — odmowa dopiero **po** sprawdzeniu hasła
(403, kod `EMAIL_NOT_VERIFIED`, zamaskowany adres). Bez `MAIL_HOST` poczta jest
wyłączona i wszyscy są potwierdzeni (także przy starcie serwera — konta
z czasów bez poczty nie są blokowane po jej włączeniu; migracja V4 robi to
samo na produkcji).

- Token: 32 losowe bajty, w bazie SHA-256 (`email_tokens`), 24 h, jednorazowy,
  potwierdza konkretny adres (po zmianie adresu stary link nic nie robi).
- Strona potwierdzenia wysyła POST — samo otwarcie linku przez skaner poczty
  niczego nie potwierdza. Nie loguje (link bywa otwierany na innym urządzeniu).
- Limity: minuta odstępu i 5 na dobę na konto, 20 na godzinę z IP.
  Ponowna wysyłka przed zalogowaniem wymaga loginu i hasła; można przy niej
  poprawić literówkę w adresie.
- Niepotwierdzone konto znika po 7 dniach (`UnverifiedAccountCleanup`).
- Zmiana adresu w ustawieniach (V5): hasło + zgoda ze STAREJ skrzynki
  (`/potwierdz-zmiane-adresu`, przyciski zgoda / „To nie ja”) + potwierdzenie
  nowej, w dowolnej kolejności; stary obowiązuje do obu. „To nie ja” anuluje
  i wylogowuje wszystko.
- Reset hasła: `/reset-hasla` → link 1 h na obecny adres → `/nowe-haslo`.
  Prośba zawsze 204 (także przy wyczerpanym limicie) — nie zdradza kont.
- Znacznik bezpieczeństwa (`users.security_stamp`): sesja zapamiętuje go przy
  logowaniu (`SecurityStampFilter`), podpis „zapamiętaj mnie” go zawiera.
  Zmiana hasła, reset, „To nie ja”, „Wyloguj z innych urządzeń” → nowy
  znacznik = inne urządzenia wylogowane. Filtr działa tylko przy sesji;
  w testach kontrolerów atrapa `securityStampOf` musi zwracać konto.
- Linki uzyte i porzucone są **wygaszane, nie kasowane** — limit wysyłek
  liczy linki z doby, a kasowanie pozwalałoby go obejść („zmień → anuluj”).
- Adresy zapisywane małymi literami, unikalność bez wielkości liter; skrzynki
  jednorazowe odrzucane (`mail/disposable-domains.txt`).
- Szablon `mail/potwierdzenie.html` (tabele, style w elementach, logo jako
  `cid:napis` — biały napis z `NapisMC` wyrenderowany do PNG 630×119) plus
  wersja tekstowa; teksty w `messages*.properties` (`mail.*`). Rodzaje
  w `AccountMails.Kind`; `AccountMailsTest` renderuje każdy w PL i EN.

## Blokady i prywatność (V6)

- Blokada (`user_blocks`, `BlockService`) działa w obie strony: zrywa
  znajomość i zaproszenia, czyści powiadomienia między tymi osobami, ukrywa
  posty (tablica, pojedynczy post = 404, reakcje), profil, propozycje,
  listę znajomych, uczestników wydarzeń i obecność. Czat jest tylko dla
  znajomych, więc pisać się nie da; historia zostaje.
- Zablokowany **nie wie** o blokadzie: profil blokującego = 404, zaproszenie
  = ten sam komunikat co przy „nie przyjmuje zaproszeń”.
- Ustawienia na koncie: profil dla wszystkich / znajomych
  (`PrivacyService.view()` → FULL / RESTRICTED / BLOCKED_BY_ME), kto może
  zaprosić (każdy / znajomi znajomych / nikt), pokazywanie aktywności
  (`PresenceResponse.hidden`), obecność w propozycjach, domyślne ukrycie na
  listach uczestników (zapis bez `hidden` = domyślne z ustawień).
- Szczegóły profilu (ulubieni, znajomi, top, playlisty, „co nas łączy”,
  posty autora) pilnuje `privacy.requireDetails()` w każdym endpoincie.
  Administrator widzi wszystko.
- Zbiór „ukrytych” do `NOT IN` bierze się z `blocks.hiddenForQuery()` —
  pusta lista jest podmieniana na `-1`.

## Powiadomienia push i przypomnienia (V8)

- Przypomnienia (`EventReminderService`): progi `app.events.reminders.days`
  (3,1), co godzinę 9–21 czasu polskiego. `event_participations.reminded_days`
  = ostatni zaliczony próg; przy zapisie ustawiany na bieżący (kto zapisuje się
  dzień przed, wie, kiedy to jest). Nowe przypomnienie o tym samym wydarzeniu
  zastępuje stare; rezygnacja je usuwa. `users.event_reminders` wyłącza.
- `notifications.actor_id` może być NULL (przypomnienia pisze aplikacja) —
  lista powiadomień musi łączyć sprawcę przez LEFT JOIN.
- Web Push bez bibliotek: `push/WebPushEncryption` (RFC 8291, aes128gcm),
  `push/Vapid` (RFC 8292, ES256 w formacie P1363), `push/P256`. Test na
  wzorcu z biblioteki referencyjnej `http_ece`. Klucze `VAPID_*`; bez nich
  push wyłączony, z połową/złym kluczem/bez kontaktu backend nie wstaje.
- Wysyłka tylko do usług push z listy (`PushService.USLUGI`; test może
  dopisać `app.push.extra-hosts`, http tylko dla localhost). 404/410 kasuje
  subskrypcję.
- Subskrypcja pamięta znacznik bezpieczeństwa konta (`stamp`, NULL → "").
  Zmiana hasła/reset/„wyloguj wszędzie” = stare urządzenia milkną; bieżące
  zapisuje się samo przy otwarciu aplikacji (`utils/push.js synchronizuj`).
  Wylogowanie wypisuje urządzenie.
- Push dla: przypomnień, zaproszeń, przyjętych zaproszeń, zgłoszeń (admin),
  rozpatrzonych zgłoszeń. Reakcje tylko w dzwonku.
- Service worker: `push` pokazuje powiadomienie (tag zastępuje poprzednie),
  `notificationclick` przechodzi pod adres z tej samej domeny. W `npm run dev`
  nie ma service workera, więc push jest niedostępny — sprawdza się na
  `npm run build && npm run preview`.
- Sprawdzanie w Chromium: udawana usługa push w skrypcie Playwrighta,
  `PushManager.subscribe` podmieniony w init script, dostarczenie do
  prawdziwego service workera przez CDP `ServiceWorker.deliverPushMessage`,
  kliknięcie przez `NotificationEvent` w `serviceWorkers()[0].evaluate`.

## Klany (V9)

- Jedna osoba = najwyżej jeden klan (`clan_members.user_id` unikalny). Do klanu
  wchodzi się **wyłącznie za zgodą klanu**: z zaproszenia od członka
  (`clan_invitations`) albo — od V12, jeśli klan to włączył — przez prośbę
  o dołączenie, którą rozpatruje zarząd (niżej, „Społeczność klanów”);
  przyjęcie kasuje wszystkie inne zaproszenia i prośby tej osoby. Odmowa zostaje jako
  `DECLINED` — przez 7 dni nie da się zaprosić ponownie, a odpowiedź dla
  zapraszającego jest ta sama co przy blokadzie i „nikt” (nie zdradza powodu).
  Ustawienie `users.clan_invites_from`: wszyscy / tylko znajomi / nikt; blokada
  w obie strony też odbiera możliwość zaproszenia.
- Role: `FOUNDER` (nazwa, skrót, role, przekazanie, rozwiązanie), `ADMIN`
  (wyrzuca zwykłych członków, zmienia opis i obrazy, kasuje cudze posty i
  wiadomości w klanie), `MEMBER`. Założyciel nie odejdzie bez przekazania klanu;
  gdy jest sam, odejście = rozwiązanie klanu. Limit 30 osób, 50 oczekujących
  zaproszeń.
- **Kolor klanu wybierają członkowie**: jeden głos na osobę z palety 10
  kolorów (`ClanColor`, wszystkie ciemne — biały napis ma kontrast ≥ 4,5:1),
  wygrywa najwięcej głosów, przy remisie ten głosowany wcześniej (nanosekundy —
  dwa głosy w jednej sekundzie to normalka). `clans.color` to zapisany wynik,
  przeliczany przy głosie i zmianie składu. Łatwo zmienić na „ustala założyciel”:
  wystarczy nie wołać `recomputeColor`.
- Post klanu = zwykły `Post` z `clan_id`. **Wszystkie** zapytania publiczne
  (tablica, krąg, profil, pod wydarzeniem, liczniki, „top muzyki” — natywne SQL!)
  mają `p.clan IS NULL`. Widoczność: `Post.isVisibleTo` (członek albo
  administrator aplikacji); dla obcego pojedynczy post, reakcje, edycja i
  kasowanie dają **404**, nie 409 — klan nie zdradza, że istnieje. Nowa
  kwerenda na `Post` musi pamiętać o tym filtrze.
- Czat klanu (`ClanChatService`): odpytywanie co 4 s (`after=` nowsze,
  `before=` starsze); wiadomości osób z blokad oglądającego są pomijane.
- **Administrator aplikacji może czytać klany** (czat, posty, członkowie) i
  je rozwiązać — po zgłoszeniu albo przy podejrzeniu naruszenia prawa. Każde
  wejście zostaje w logu (`Audyt: administrator … przegląda klan …`); strona
  klanu mówi to członkom wprost, a administratorowi pokazuje baner. Polityka
  prywatności **musi** o tym mówić.
- Po odejściu/wyrzuceniu posty i wiadomości zostają w klanie (UI o tym
  uprzedza); po usunięciu konta znikają. Usunięcie konta: zaproszenia i
  wiadomości kasowane, założyciela zastępuje administrator albo najstarszy
  członek, sam członek zabiera klan ze sobą (`ClanService.deleteAllOf`).
- Rozwiązanie klanu kasuje wiersz **zapytaniem** (`ClanRepository.deleteRow`),
  nie `em.remove`: po zapytaniach czyszczących kontekst `remove` scala odpiętą
  encję razem z listą członków, których już nie ma („Unable to find
  ClanMember”). Reakcje pod postami kasowane wprost — kaskada z encji nie
  widzi reakcji dopisanych w tej samej sesji. W teście jednosesyjnym po
  rozwiązaniu klanu trzeba `em.clear()`, żeby `findById` nie oddał z pamięci.
- Powiadomienia: `CLAN_INVITE` (z push), `CLAN_KICKED` (bez sprawcy); klucz
  klanu w powiadomieniu znika razem z klanem (`ON DELETE CASCADE`). Po
  przyjęciu/odrzuceniu zaproszenie znika z dzwonka.

## Rozszerzenia klanów (V11)

- **Nieprzeczytane i push z czatu.** `clan_members.chat_read_id` = numer
  ostatniej wiadomości, którą osoba widziała. Nowy członek startuje od ostatniej
  wiadomości w klanie (historia sprzed wejścia nie jest „nowa”; migracja robi to
  samo istniejącym członkom). Licznik to wiadomości **innych** osób nowsze niż
  znacznik, bez osób z blokad (`ClanMessageRepository.unread`). Klient oznacza
  czat jako przeczytany dopiero, gdy jest na ekranie (`POST /chat/read`;
  znacznik nigdy się nie cofa i jest przycinany do ostatniej wiadomości), a kto
  sam pisze, ma znacznik przesunięty na własną wiadomość.
- Push z czatu (`ClanChatService.powiadom`): dostaje go tylko ten, kto nie ma
  jeszcze żadnej nieprzeczytanej (`ClanMemberRepository.toNotify`) — jedno
  powiadomienie do czasu zajrzenia, telefon nie brzęczy przy każdej wiadomości.
  Pomijani: autor, wyciszeni (`chat_muted`), osoby z blokadą. W powiadomieniu
  jest nazwa nadawcy, **bez treści** (ekran blokady). `sw.js` nie pokazuje
  powiadomień z tagiem `clan-chat-…`, gdy aplikacja jest widoczna. Licznik
  w menu (`GET /mine/unread`) odświeża się co 30 s i po przeczytaniu czatu
  (zdarzenie `mc-klan-odswiez`).
- **Odpowiedzi i reakcje.** `clan_messages.reply_to_id` z `ON DELETE SET NULL`:
  skasowanie oryginału zostawia odpowiedź bez cytatu; cytat znika też wtedy, gdy
  oglądający blokuje autora oryginału (odpowiedź zostaje z „wiadomość
  niedostępna”). `clan_message_reactions`: jedna reakcja na osobę i wiadomość
  (inne emoji podmienia), stała lista `ClanEmoji` (kolumna z CHECK — wpis
  w `EnumConstraintRefresher`), liczniki bez osób z blokad oglądającego.
  Odpytywanie czatu dopytuje też `GET /chat/reactions?since=`, a odpowiedź
  opisuje **cały** zakres — wiadomości, której w niej nie ma, nie ma reakcji.
- **Zgłoś klan.** `ReportContext.CLAN` i osobna ścieżka
  `POST /api/reports/clans/{id}`; zwykła ścieżka odrzuca ten kontekst (nie
  wiedziałaby, którego klanu dotyczy). „Zgłaszanym” jest założyciel. Do
  zgłoszenia idzie migawka nazwy, skrótu i opisu (`report_evidence`), a
  `reports.clan_id` ma `ON DELETE SET NULL` — rozwiązanie klanu zostawia
  zgłoszenie z dowodem. Nie zgłosisz własnego klanu; jedno otwarte zgłoszenie
  na osobę i klan; dzienny limit jest wspólny z pozostałymi zgłoszeniami.
- **Ogłoszenie i zasady.** `clans.announcement` (500 zn.) i `clans.rules`
  (600 zn.) ustawia zarząd tym samym `PUT /api/clans/{id}` (`null` = bez zmiany,
  puste = zdejmij; data ogłoszenia przesuwa się tylko przy zmianie treści).
  Obca osoba nie widzi żadnego z nich; zasady widzi też zaproszony — w
  zaproszeniu, przed przyjęciem. „Rozumiem” pamięta tylko przeglądarka
  (`localStorage`), po zmianie zasad rozwijają się znowu.
- **Gust klanu** (`GET /taste`): wykonawcy i gatunki, które lubią co najmniej
  dwie osoby — bez wskazywania kto. Nie wliczamy osób z profilem „tylko
  znajomi” ani z blokad oglądającego. Polityka prywatności o tym mówi.
  (Warunek `counted < MINIMUM` w `ClanMusicService.taste` jest tylko
  oszczędnością zapytań — `HAVING` i tak by to odrzuciło; mutant go wyłączający
  jest równoważny.)
- **Utwór tygodnia** (`clan_tracks`, `clan_track_votes`): tylko link do
  pojedynczego utworu (`MusicKind.TRACK`), 3 propozycje na osobę w tygodniu,
  bez duplikatów w tygodniu, jeden głos na propozycję. Tydzień od poniedziałku
  wg czasu polskiego (`week_start`); po nim głosowanie jest zamknięte
  (409), a wygrana zostaje w historii (8 tygodni; tygodnie bez głosów pomijane).
  Prowadzi propozycja z co najmniej jednym głosem, przy remisie wcześniejsza.
- **Koncerty klanu** (`GET /events`): nadchodzące, niewycofane wydarzenia
  z zapisami członków; ukryci (`hidden`) są tylko w liczniku, a siebie widzisz
  zawsze. „Kto jedzie?” to zwykły post klanu z `eventId` (istniejące
  `POST /api/posts` z `clanId` i `eventId`) — nie pokazuje się pod wydarzeniem
  dla wszystkich (`p.clan IS NULL`), a wydarzenie z takim postem import
  wycofuje, a nie kasuje.
- **Sprzątanie**: rozwiązanie klanu i usunięcie konta kasują reakcje, głosy
  i propozycje (`ClanService.delete` / `deleteAllOf`); odpowiedzi na skasowaną
  wiadomość zostają. Eksport ma reakcje, propozycje, głosy i wyciszenie, a
  polityka je wymienia. `app.legal.version` zostało na 2026-09-30 (data
  tekstów, których nikt jeszcze nie akceptował) — po wypuszczeniu aplikacji
  każda zmiana tekstu = nowa data.
- Odpowiedź serwera po `charset`: `MockHttpServletResponse` bez charsetu czyta
  JSON jako ISO-8859-1 — test z polskimi znakami albo „…” musi wołać
  `getContentAsString(UTF_8)` (cytat z wielokropkiem „miał” 122 znaki zamiast 120).

## Społeczność klanów (V12)

Przeglądarka klanów, prośby o dołączenie, wizytówka, tytuły, ankiety, ranking.
Reguła stała: **czat, posty, ankiety, ranking, tytuły, utwór tygodnia i koncerty
klanu widzą tylko członkowie i administrator aplikacji** (obcy: 409 albo 404).
Obcy dostaje wyłącznie „rekonesans” — dane, po których poznaje, co to za klan.

- **Prośby o dołączenie** (`clan_join_requests`, `ClanRequestService`):
  `clans.join_policy` = `REQUESTS` albo `INVITE_ONLY` (domyślnie; istniejące
  klany zostają `INVITE_ONLY`). Wiadomość do zarządu ≤ 200 znaków, najwyżej
  5 oczekujących próśb na osobę, jedna na klan. Przyjmuje i odrzuca zarząd
  (`POST …/requests/{id}/accept|decline`); odmowa nie wysyła powiadomienia
  i nie ma powodu, ale zostaje jako `DECLINED` na 7 dni
  (`ClanService.PROSBA_PO_ODMOWIE`) — wtedy `canRequest=false`. Wejście do klanu
  (założenie, przyjęcie zaproszenia, przyjęcie prośby) kasuje wszystkie inne
  prośby i zaproszenia tej osoby (`ClanService.dropApplications`). Blokada
  w obie strony z założycielem daje tę samą odpowiedź co klan bez próśb.
  Powiadomienia i push: `CLAN_JOIN_REQUEST` (do zarządu), `CLAN_REQUEST_ACCEPTED`.
  Oczekujące prośby wygasają po 30 dniach, odrzucone po 7 (`ClanRequestCleanup`).
- **Przeglądarka** (`GET /api/clans/directory`, `ClanDirectoryService`):
  szukanie (nazwa, skrót, hasło, miasto), filtr gatunku (fasety z liczbą klanów)
  i miasta, „tylko do których mogę dołączyć”, sortowanie MATCH / MEMBERS /
  ACTIVE / NEWEST / OLDEST / NAME. Liczy się w pamięci (kilka zapytań
  grupujących na całość, nie zapytanie na klan). Strona 12, najwyżej 50.
  Klan z `listed=false` nie ma go na liście; klany blokującego/zablokowanego
  założyciela też znikają. **Istniejące klany dostały `listed=false`** (V12) —
  nikt się nie zgodził na przeglądarkę; nowe mają domyślnie `true`.
  Filtry siedzą w adresie (`?q=&genre=&city=&joinable=1&sort=`).
- **Rekonesans dla obcych**: `activityLevel` (cisza / spokojny / aktywny / bardzo
  aktywny z liczby wiadomości z 7 dni — progi 1 / 20 / 100, nigdy treść ani
  liczba) i gust zbiorczy (`GET …/taste`) — tylko dla klanu z przeglądarki,
  członka, administratora albo zaproszonego (`ClanService.requireRecon`).
  Gust nadal wlicza tylko profile `EVERYONE` i wymaga ≥ 2 osób. Dopasowanie do
  *twojego* gustu (`match`, `sharedGenres`) widzi tylko oglądający.
- **Wizytówka**: `motto` (80), `city` (60), do 3 gatunków (`clan_genres`,
  `@ElementCollection` z `@OrderColumn`, małe litery), `joinPolicy`, `listed`;
  ustawia zarząd (`PUT /api/clans/{id}`, `null` = bez zmiany) albo założyciel
  przy zakładaniu. Ikona przy zakładaniu: formularz pokazuje podgląd plakietki
  (`ClanBadge`) z lokalnego pliku, a wgrywa ją po założeniu klanu — gdy się nie
  uda, klan i tak powstaje, a strona mówi, że ikonę można dodać w ustawieniach.
- **Tytuły** (`clan_titles`, `clan_member_titles`, `ClanTitleService`,
  `ClanTitleEngine`): tytuł to ozdoba, **nie zmienia uprawnień**. Definiuje je
  zarząd (≤ 12 na klan, nazwa 2–24 znaki, kolor z palety klanu). Tryby:
  `MANUAL` (nadaje zarząd), `SELF` (każdy członek bierze sam, najwyżej 2),
  `AUTO` (z progu: wiadomości, posty, propozycje utworów, głosy, reakcje albo
  dni w klanie; liczone z całości, **bez wierszy w `clan_member_titles`** —
  przeliczają się przy każdym wejściu na stronę klanu). Jedna osoba najwyżej
  5 nadanych i wziętych. Zmiana trybu na `AUTO` zdejmuje nadane i wzięte.
  Tytuły widzą tylko członkowie i administrator aplikacji.
- **Ankiety** (`clan_polls`, `_options`, `_votes`, `ClanPollService`): pytanie
  ≤ 150, 2–6 odpowiedzi ≤ 80 bez duplikatów, 1/3/7/14 dni, jeden głos na osobę
  (do zmiany, póki otwarta), najwyżej 2 otwarte na osobę i 5 na klan. Wyniki na
  bieżąco, bez wskazywania kto na co. Zamyka i kasuje autor albo zarząd.
- **Ranking** (`ClanActivityCounter`, `ClanActivityService`): punkty — wiadomość 1,
  post 4, propozycja utworu 3, głos (utwór albo ankieta) 1, reakcja 1. „Tydzień”
  to ostatnie 7 dni, nie kalendarzowy. **Poziom** (0–4: 0 / 30 / 100 / 300 / 800
  pkt) liczy się z całego czasu, więc nie spada. Cel tygodnia klanu:
  max(40, 20 × liczba członków) — wspólny wynik, nie wyścig. Osoby z blokad
  oglądającego pomijane (administrator widzi wszystkich).
- **Sprzątanie**: `ClanCleanup` (bez zależności od `ClanService`, żeby nie
  robić cyklu) kasuje ankiety, głosy, tytuły, prośby i gatunki przy
  rozwiązaniu klanu (`ofClan`) i usunięciu konta (`ofUser`); przy odejściu
  (`ofMembership`) znikają tylko tytuły tej osoby — jej ankiety i głosy
  zostają w klanie, tak jak posty i wiadomości. Gatunki to osobna tabela — przed `ClanRepository.deleteRow`
  kasuje je natywne zapytanie (`deleteGenres`). `memberTitles.deleteByUserId`
  w `ofUser` jest „paskiem i szelkami” (członkostwo i tak sprząta tytuły) —
  mutant, który go wyłącza, przeżywa i jest równoważny.
- **Eksport i polityka**: `DataExportService` ma prośby, ankiety, głosy
  i tytuły; regulamin i polityka mówią o przeglądarce, prośbach, gustach
  zbiorczych dla obcych, ankietach, rankingu i tytułach. `app.legal.version`
  zostało na 2026-09-30 (nikt jeszcze nie akceptował) — po wypuszczeniu każda
  zmiana tekstu = nowa data.
- **Pułapki z tej rundy**: zbiorcze `UPDATE` w JPQL nie przyjmuje złączeń
  (podzapytanie `IN (SELECT …)`); `MockMvc` koduje `%20` drugi raz — w testach
  spacja wprost w adresie; mutant, który robi nieużywany parametr nazwany,
  rozwala kontekst Springa (mutować tak, by parametr został); w Playwrighcie
  `waitForSelector` **ignoruje** `hasText` — `locator(…, { hasText }).waitFor()`;
  przyciski szablonów tytułów zawierają te same napisy co wiersze tytułów, więc
  sprawdzenia „tytuł zniknął” robić na wierszach, nie na `body`.

## Regulamin i polityka prywatności (V10)

- Treść to **szablon, nie porada prawna**: `frontend/src/legal/regulamin.js` i
  `polityka.js` (PL i EN, dane + placeholdery `{{administrator}}`, `{{kontakt}}`,
  `{{hosting}}`, `{{poczta}}`, `{{wersja}}`). Przed wpuszczeniem prawdziwych
  użytkowników powinien go przeczytać prawnik. Polityka opisuje to, co
  aplikacja **naprawdę** robi (retencja: konta niepotwierdzone 7 dni, linki 24 h,
  itd.) — zmieniasz zachowanie (nowa tabela z danymi osobowymi, nowy odbiorca
  danych, nowy okres przechowywania) → poprawiasz też politykę.
- **Dane administratora nie leżą w repozytorium** (to dane osobowe właściciela):
  `LEGAL_CONTROLLER`, `LEGAL_CONTACT_EMAIL`, `LEGAL_HOSTING`,
  `LEGAL_MAIL_PROVIDER` w `.env`; serwer oddaje je przez `/api/public/info`, a
  strona podstawia. Puste = w tekście widnieje „[do uzupełnienia…]” i baner
  ostrzegawczy, a backend loguje ostrzeżenie przy starcie.
- Wersja dokumentów: `app.legal.version` (data ostatniej zmiany treści).
  **Każda zmiana tekstu = podniesienie wersji**, inaczej nikt nie dostanie
  prośby o ponowną akceptację. Konto pamięta `terms_version` i
  `terms_accepted_at`; rejestracja wymaga `acceptTerms=true` (`@AssertTrue`,
  422 z polem); starsze konta (NULL) i konta ze starą wersją widzą baner
  `AkceptacjaRegulaminu` (nie blokuje) → `POST /api/profile/terms/accept`.
- Wiek: 16 lat (art. 8 RODO w Polsce). Polityka mówi o dostępie administratora
  do klanów i o zapisie takich wejść w logu — to musi zostać prawdą.
- Docker: rotacja logów w `docker-compose.prod.yml` (3×10 MB na usługę); w
  logach są adresy IP, a polityka mówi o krótkim przechowywaniu.

## Pobranie własnych danych

`POST /api/profile/export` (hasło w treści, najwyżej raz na minutę, CSRF) →
ZIP: `dane.json`, `CZYTAJ-TO.txt`, `zdjecia/` (awatar i zdjęcia własnych
postów). `DataExportService`: dane zbiera w transakcji (`przygotuj`), a
do strumienia pisze poza nią (`zapisz`, `StreamingResponseBody`) — zdjęcia
czytane z dysku po nazwach z bazy (sama nazwa pliku, bez ścieżki). W archiwum
są: konto i ustawienia, profil i ulubione, posty (także klanu), reakcje,
wiadomości (wysłane i otrzymane, **bez rozmów skasowanych u siebie**),
znajomi, zaproszenia, blokady, zapisy, powiadomienia, klan i własne
wiadomości w nim, urządzenia push (sam host usługi — bez kluczy), zgłoszenia
złożone oraz „o tobie” (**bez nazwy zgłaszającego i opisu**). Nie ma hasła,
znaczników bezpieczeństwa ani e-maili innych osób. **Nowa tabela z danymi
osobowymi → dopisujesz ją tu** i do `AccountDeletionService`; polityka
prywatności obiecuje obie rzeczy.

## Wybory, do których nie wracamy

- **TWA, nie Capacitor.** Frontend i API stoją pod jednym adresem (nginx
  przekazuje `/api` i `/uploads` do backendu), więc ciasteczko sesji i CSRF
  działają bez żadnych zmian w kodzie. Capacitor dałby WebView z własnym
  originem i wróciłyby `SameSite=None`, CORS i osobne ustawienia ciasteczek.
- **Napis MusicClub jest na krzywych**, nie tekstem — nie zależy od doładowania
  kroju. Kształty w `frontend/src/components/napisMCKsztalty.js` są generowane;
  nie poprawiamy ich ręcznie.
