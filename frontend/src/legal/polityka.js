/*
 * POLITYKA PRYWATNOSCI - tresc.
 *
 * SZABLON zgodny z tym, co Serwis naprawde robi z danymi (patrz CLAUDE.md, "Wybory" i sekcje o
 * poczcie, push, klanach) - ale nie porada prawna: przed wpuszczeniem prawdziwych uzytkownikow
 * powinien go przeczytac prawnik i uzupelnic nazwy dostawcow (hosting, poczta) w punkcie 5.
 * Zmiana tresci = podniesienie app.legal.version.
 */

const pl = {
  tytul: 'Polityka prywatności serwisu MusicClub',
  sekcje: [
    {
      tytul: '1. Kto jest administratorem Twoich danych',
      akapity: [
        'Administratorem danych osobowych jest {{administrator}} („Administrator”). Kontakt we wszystkich sprawach związanych z danymi: {{kontakt}}.',
        'Ta Polityka opisuje, jakie dane zbieramy w serwisie i aplikacji MusicClub, po co, komu je przekazujemy, jak długo je trzymamy i jakie masz prawa (RODO — rozporządzenie 2016/679).',
      ],
    },
    {
      tytul: '2. Jakie dane i po co',
      akapity: [
        {
          lista: [
            'Konto: login, adres e-mail, hasło (zapisujemy wyłącznie jego zaszyfrowany skrót, nigdy samego hasła), data założenia, potwierdzenie adresu, wersja i data akceptacji Regulaminu, ustawienia prywatności i kraj wydarzeń. Po co: założenie i obsługa konta, logowanie, zabezpieczenie konta — podstawa: umowa o świadczenie usługi (art. 6 ust. 1 lit. b RODO).',
            'Profil i muzyka: zdjęcie profilowe, ulubieni wykonawcy i utwory, playlisty, zestawienie najczęściej udostępnianej muzyki, gatunki. Po co: pokazanie profilu, „co nas łączy”, dopasowanie wydarzeń „Dla ciebie” i propozycji znajomych — umowa.',
            'Miasto (nieobowiązkowe): nazwa miasta, które wpiszesz w Ustawieniach, i — jeśli mamy je na swojej liście — współrzędne jego środka, a także ustawienie, czy miasto widać na profilu. Nie zbieramy dokładnego adresu ani lokalizacji z telefonu. Po co: żeby w propozycjach znajomych, wydarzeniach, klanach i na tablicy wyżej były osoby, koncerty i klany z Twojej okolicy (odległość między miastami liczy nasz serwer) — podstawa: Twoja zgoda (art. 6 ust. 1 lit. a RODO), którą cofniesz, usuwając miasto w Ustawieniach.',
            'Tablica „Dla ciebie”: kolejność postów osób spoza Twoich znajomych (znajomi są zawsze na górze, od najnowszych) liczymy automatycznie ze świeżości posta, odległości między Twoim miastem a miastem autora, wspólnych ulubionych wykonawców i gatunków (tylko z profili widocznych dla wszystkich) oraz liczby reakcji i komentarzy pod postem. Przy poście napisane jest, dlaczego jest wysoko („Z twojej okolicy”, „Podobny gust”, „Popularne”). Po co: podpowiedzi w ramach umowy; kolejność możesz zmienić na „Najnowsze”.',
            'Treści: posty (tekst, zdjęcia, linki do utworów wraz z tytułem i miniaturą pobranymi ze Spotify lub YouTube), reakcje, komentarze pod postami (także z oznaczeniami osób i odpowiedziami na cudze komentarze), wiadomości na czacie, GIF-y dołączone do komentarzy i wiadomości (zapisujemy adres pliku u dostawcy GIF-ów, jego wymiary i opis — nie sam plik), znajomi i zaproszenia, blokady. Po co: działanie Serwisu — umowa.',
            'Wyszukiwarka GIF-ów: fraza, którą wpiszesz (albo prośba o popularne GIF-y), idzie przez nasz serwer do dostawcy GIF-ów razem z językiem aplikacji i losowo wyglądającym identyfikatorem, z którego dostawca nie odczyta Twojego konta. Nie zapisujemy tych fraz w bazie; odpowiedź trzymamy w pamięci serwera kilka minut. Po co: działanie Serwisu — umowa.',
            'Wydarzenia: zapisy „Zainteresowany” i „Biorę udział” (i wybór, czy pokazywać Cię na liście uczestników), posty pod wydarzeniami, przypomnienia. Po co: umowa.',
            'Klany: członkostwo i rola, głos na kolor klanu, zaproszenia (także odrzucone — żeby nikt nie zapraszał w kółko osoby, która odmówiła), Twoje prośby o dołączenie do klanów (z krótką wiadomością do zarządu, jeśli ją wpiszesz), czat i posty klanu, odpowiedzi i reakcje na wiadomości, znacznik „przeczytane do” (z niego liczymy nieprzeczytane wiadomości) i ustawienie wyciszenia czatu, propozycje i głosy w „utworze tygodnia”, ankiety klanu i oddane w nich głosy, tytuły w klanie (nadane, wzięte samodzielnie albo przyznane automatycznie za aktywność), punkty i poziom aktywności w rankingu klanu (liczone z Twoich wiadomości, postów, propozycji, głosów i reakcji w klanie), ogłoszenie, zasady, hasło, miasto, gatunki i ustawienia widoczności klanu wpisane przez jego zarząd. Po co: umowa. O dostępie administratora do klanów — punkt 4.',
            'Gust klanu: wykonawców i gatunki, które ulubiło co najmniej dwóch członków, pokazujemy zbiorczo, bez wskazywania osób — członkom klanu, a jeśli klan jest w przeglądarce klanów, także osobom spoza niego (razem z poziomem aktywności czatu: cisza / spokojny / aktywny / bardzo aktywny, bez treści i liczby wiadomości). Nie wliczamy osób, które ograniczyły profil do znajomych. W przeglądarce klanów porównujemy gust klanu z Twoim, ale wynik widzisz tylko Ty. Po co: umowa.',
            'Powiadomienia: powiadomienia w dzwonku oraz — jeśli je włączysz — powiadomienia push (także o nowej wiadomości na czacie Twojego klanu, z nazwą nadawcy, bez treści; możesz je wyciszyć): adres subskrypcji nadany przez przeglądarkę, klucze szyfrujące, język. Po co: dostarczanie powiadomień — Twoja zgoda (art. 6 ust. 1 lit. a); cofniesz ją w każdej chwili.',
            'Wiadomości e-mail: potwierdzenie adresu, reset hasła, zgoda na zmianę adresu, informacja o zmianie hasła. Po co: umowa i bezpieczeństwo konta. Nie wysyłamy reklam.',
            'Moderacja: zgłoszenia (kto, kogo i dlaczego; migawka zgłoszonego posta lub komentarza albo — przy zgłoszeniu klanu — jego nazwy i opisu), decyzje, ostrzeżenia i zakazy. Po co: ochrona użytkowników i egzekwowanie Regulaminu — prawnie uzasadniony interes (art. 6 ust. 1 lit. f) oraz obowiązki wynikające z przepisów o usługach elektronicznych.',
            'Dane techniczne: czas ostatniej aktywności (możesz go ukryć), adres IP i czas zapytań w dziennikach serwera oraz w ograniczeniach liczby prób (np. wysyłki wiadomości), wpisy o wejściach administratora do klanów. Po co: bezpieczeństwo, zapobieganie nadużyciom, diagnostyka — prawnie uzasadniony interes.',
          ],
        },
        'Podanie danych do założenia konta jest dobrowolne, ale bez loginu, adresu e-mail i hasła nie możemy go założyć. Nie prowadzimy analityki reklamowej ani nie sprzedajemy danych.',
      ],
    },
    {
      tytul: '3. Kto widzi Twoje dane w Serwisie',
      akapity: [
        {
          lista: [
            'Login, zdjęcie profilowe i plakietka klanu są widoczne dla zalogowanych osób. Klan, w którym jesteś, jest widoczny w przeglądarce klanów (jego nazwa, skrót, ikona, hasło, miasto, gatunki, liczba osób i data założenia) — chyba że jego zarząd ukryje go w ustawieniach. Resztę profilu (ulubieni, znajomi, posty, playlisty) możesz ukryć przed osobami spoza znajomych.',
            'Posty ustawiasz jako publiczne albo tylko dla znajomych. Komentarze pod postem widzą te same osoby, które widzą post (pod postem tylko dla znajomych — znajomi autora, pod postem klanu — członkowie klanu); komentarze zablokowanych osób są dla siebie niewidoczne. Osoba oznaczona w komentarzu (@login) dostaje powiadomienie, o ile widzi ten post. Posty klanu, czat, ogłoszenie, zasady, propozycje i głosy w „utworze tygodnia”, ankiety, ranking aktywności i tytuły widzą tylko członkowie klanu (zasady — także osoba zaproszona, przed dołączeniem).',
            'Możesz ukryć, że jesteś aktywny, wyłączyć pojawianie się w propozycjach znajomych i domyślnie ukrywać się na listach uczestników wydarzeń.',
            'Miasto widać na Twoim profilu (przy pełnym widoku, nie przy profilu tylko dla znajomych) i na Twojej karcie w propozycjach znajomych — chyba że wyłączysz „Pokazuj moje miasto na profilu i w propozycjach znajomych”. W propozycjach pokazujemy innym tylko, czy jesteś z ich miasta albo z okolicy, bez odległości. Miasto wpływa na kolejność proponowanych osób, wydarzeń, klanów i postów także wtedy, gdy ukryjesz je na profilu.',
            'Zablokowana osoba nie widzi Twojego profilu ani postów i nie może Cię zapraszać; nie dowiaduje się o blokadzie.',
            'Wiadomości na czacie widzisz tylko Ty i druga osoba — z wyjątkiem opisanym w punkcie 4 (zgłoszenia).',
          ],
        },
      ],
    },
    {
      tytul: '4. Klany i dostęp administratora',
      akapity: [
        'Czat i posty klanu (wraz z komentarzami pod nimi) widzą jego członkowie. Administrator Serwisu może je przeglądać — a także rozwiązać klan — wyłącznie wtedy, gdy wpłynęło zgłoszenie, istnieje uzasadnione podejrzenie naruszenia Regulaminu lub prawa (np. treści nielegalnych) albo żąda tego uprawniony organ. Podstawa: prawnie uzasadniony interes Administratora i obowiązki prawne (art. 6 ust. 1 lit. f i c).',
        'Każde takie wejście zostaje zapisane w dzienniku (kto i który klan), a strona klanu informuje o możliwości takiego dostępu. Administrator może też zobaczyć treść rozmowy, posta lub komentarza, który został zgłoszony.',
      ],
    },
    {
      tytul: '5. Komu przekazujemy dane',
      akapity: [
        {
          lista: [
            'Dostawca infrastruktury, na której działa Serwis (serwer, baza, pliki, kopie zapasowe): {{hosting}}',
            'Dostawca poczty, przez którego wysyłamy wiadomości do Ciebie (przetwarza Twój adres e-mail i treść wiadomości o koncie): {{poczta}}',
            'Usługi powiadomień push przeglądarek — Google, Mozilla, Apple, Microsoft (zależnie od Twojej przeglądarki): dostają adres subskrypcji i zaszyfrowaną treść, której nie mogą odczytać.',
            'Spotify i YouTube (Google): odtwarzacze i miniatury są ładowane z ich serwerów, więc te firmy widzą Twój adres IP i mogą ustawiać własne pliki cookie zgodnie ze swoimi zasadami.',
            'Dostawca GIF-ów (KLIPY albo GIPHY — nazwę widać pod przeglądarką GIF-ów): dostaje od naszego serwera wpisane frazy i pseudonimowy identyfikator, a Twoja przeglądarka pobiera od niego pliki GIF-ów (podglądy i wybrany GIF), więc widzi Twój adres IP i może ustawiać własne pliki cookie zgodnie ze swoimi zasadami. Nie dostaje Twojego loginu, adresu e-mail ani treści rozmów.',
            'Deezer i Last.fm: wysyłamy im nazwy wykonawców i utworów, by wyszukać wykonawcę, okładki i gatunki — bez Twoich danych osobowych. Ticketmaster: pobieramy od nich informacje o koncertach; nie wysyłamy im danych użytkowników.',
            'Organy państwa — tylko wtedy, gdy przepisy prawa tego wymagają.',
          ],
        },
        'Niektórzy z tych odbiorców (np. Google, Apple, Spotify) mogą przetwarzać dane poza Europejskim Obszarem Gospodarczym, na podstawie mechanizmów przewidzianych w RODO (decyzja o adekwatności, standardowe klauzule umowne).',
      ],
    },
    {
      tytul: '6. Jak długo trzymamy dane',
      akapity: [
        {
          lista: [
            'Konto, profil i treści — do usunięcia konta (możesz to zrobić w każdej chwili w Ustawieniach).',
            'Niepotwierdzone konta — 7 dni. Linki w wiadomościach — 24 godziny (reset hasła: 1 godzina); ich zużyte wpisy techniczne znikają po dobie.',
            'Komentarze — do skasowania przez ich autora, autora posta, administratora Serwisu albo (pod postem klanu) zarząd klanu, do usunięcia posta lub konta autora. Razem z komentarzem znikają odpowiedzi pod nim, oznaczenia i powiadomienia o nim. GIF znika razem z komentarzem albo wiadomością, do której jest dołączony.',
            'Powiadomienia w dzwonku — do usunięcia przez Ciebie albo konta. Urządzenia push — do wyłączenia powiadomień, wylogowania, zmiany hasła lub usunięcia konta.',
            'Prośby o dołączenie do klanu — oczekująca znika po przyjęciu, cofnięciu albo po 30 dniach; odrzucona — tydzień po odmowie (do tego czasu chroni przed ponawianiem próśb).',
            'Miasto — do usunięcia przez Ciebie (Ustawienia) albo konta.',
            'Zgłoszenia i decyzje moderacyjne — do usunięcia konta osoby zgłaszającej lub zgłoszonej.',
            'Dzienniki serwera (adres IP, czas i adres zapytań — a więc także fraza wpisana w wyszukiwarce GIF-ów — oraz wpisy o wejściach administratora do klanów) — w rotacji, zwykle do kilku tygodni.',
            'Po usunięciu konta znikają Twój profil, posty, zdjęcia, komentarze (razem z odpowiedziami pod nimi), wiadomości, reakcje na wiadomości w klanie, propozycje i głosy w „utworze tygodnia”, ankiety klanu i Twoje głosy w nich, tytuły, prośby o dołączenie, zapisy na wydarzenia, zaproszenia, blokady, urządzenia push i członkostwo w klanie (założyciela zastępuje inny członek, a klan, w którym nie było nikogo poza Tobą, znika). Usunięte dane mogą przez pewien czas zostać w kopiach zapasowych bazy, o ile są wykonywane; są nadpisywane w normalnej rotacji.',
          ],
        },
      ],
    },
    {
      tytul: '7. Twoje prawa',
      akapity: [
        {
          lista: [
            'Dostęp i kopia danych oraz przenoszenie: w Ustawieniach możesz pobrać swoje dane w formacie do odczytu maszynowego.',
            'Sprostowanie: login, adres e-mail, zdjęcie i ustawienia zmienisz w Ustawieniach.',
            'Usunięcie („prawo do bycia zapomnianym”): usuń konto w Ustawieniach.',
            'Ograniczenie przetwarzania i sprzeciw wobec przetwarzania opartego na prawnie uzasadnionym interesie: napisz na {{kontakt}}.',
            'Cofnięcie zgody (powiadomienia push): w Ustawieniach lub w ustawieniach przeglądarki — bez wpływu na wcześniejsze przetwarzanie.',
            'Skarga do organu nadzorczego: Prezes Urzędu Ochrony Danych Osobowych, ul. Stawki 2, 00-193 Warszawa (uodo.gov.pl).',
          ],
        },
        'Nie podejmujemy wobec Ciebie decyzji opartych wyłącznie na zautomatyzowanym przetwarzaniu, które wywołują skutki prawne. Dopasowanie wydarzeń „Dla ciebie”, propozycje znajomych i kolejność postów na tablicy „Dla ciebie” korzystają z Twoich ulubionych, znajomych i miasta, ale służą tylko do podpowiedzi — propozycje możesz wyłączyć w ustawieniach prywatności, a kolejność postów zmienić na „Najnowsze”.',
      ],
    },
    {
      tytul: '8. Pliki cookie i pamięć przeglądarki',
      akapity: [
        'Używamy wyłącznie plików niezbędnych do działania Serwisu, więc nie pytamy o zgodę na nie: ciasteczka sesji (utrzymują zalogowanie), ciasteczko zabezpieczające przed fałszowaniem żądań (CSRF) i — jeśli zaznaczysz „Zapamiętaj mnie” — ciasteczko trwałego logowania. Wszystkie mają ustawienia zabezpieczające (HttpOnly, Secure, SameSite).',
        'W pamięci przeglądarki zapisujemy język, motyw kolorystyczny, ostatnio wybrane miasto i widok wydarzeń, wybrany zasięg „w okolicy” oraz kopię plików aplikacji, dzięki której otwiera się szybciej i bez zasięgu. Kopia nie zawiera Twoich danych z konta. Nie używamy cookie reklamowych ani analitycznych. Spotify i YouTube, których odtwarzacze widzisz w postach, oraz dostawca GIF-ów, z którego serwerów ładują się GIF-y, mogą ustawiać własne pliki cookie.',
      ],
    },
    {
      tytul: '9. Bezpieczeństwo',
      akapity: [
        'Połączenie z Serwisem jest szyfrowane (HTTPS). Hasła przechowujemy w postaci zaszyfrowanego skrótu (BCrypt). Zmiana adresu e-mail wymaga hasła i zgody ze starej skrzynki, zmiana hasła wylogowuje pozostałe urządzenia, a powiadomienia push są szyfrowane end-to-end od naszego serwera do Twojej przeglądarki. Ograniczamy liczbę prób wysyłki wiadomości. Żaden system nie jest w pełni bezpieczny — o naruszeniu ochrony danych, które mogłoby skutkować wysokim ryzykiem dla Ciebie, powiadomimy Cię zgodnie z prawem.',
      ],
    },
    {
      tytul: '10. Dzieci',
      akapity: [
        'Serwis jest przeznaczony dla osób, które ukończyły 16 lat. Jeśli dowiemy się, że konto założyła młodsza osoba, usuniemy je. Rodzic albo opiekun, który zauważy takie konto, może napisać na {{kontakt}}.',
      ],
    },
    {
      tytul: '11. Zmiany Polityki',
      akapity: [
        'O istotnych zmianach informujemy w Serwisie i prosimy o ponowną akceptację. Obecna wersja obowiązuje od {{wersja}}.',
      ],
    },
  ],
};

const en = {
  tytul: 'MusicClub Privacy Policy',
  sekcje: [
    {
      tytul: '1. Who controls your data',
      akapity: [
        'The controller of your personal data is {{administrator}} (the “Controller”). Contact on all data matters: {{kontakt}}.',
        'This Policy describes what data we collect in the MusicClub website and app, why, who we share it with, how long we keep it and what rights you have (GDPR — Regulation 2016/679).',
      ],
    },
    {
      tytul: '2. What data and why',
      akapity: [
        {
          lista: [
            'Account: username, e-mail address, password (we store only its hashed form, never the password itself), creation date, address confirmation, the version and date you accepted the Terms, privacy settings and events country. Why: creating and running the account, signing in, securing the account — basis: the contract for the service (Art. 6(1)(b) GDPR).',
            'Profile and music: profile photo, favourite artists and tracks, playlists, most-shared music, genres. Why: showing your profile, “what we have in common”, “For you” event matching and friend suggestions — contract.',
            'City (optional): the city name you enter in Settings and — if it is on our list — the coordinates of its centre, and the setting for whether the city is shown on your profile. We do not collect an exact address or your phone’s location. Why: to put people, concerts and clans from your area higher in friend suggestions, events, clans and the feed (our server works out the distance between cities) — basis: your consent (Art. 6(1)(a) GDPR), which you can withdraw by removing the city in Settings.',
            '“For you” feed: the order of posts from people who are not your friends (friends always come first, newest first) is computed automatically from how fresh the post is, the distance between your city and the author’s, favourite artists and genres you have in common (only from profiles visible to everyone) and the number of reactions and comments under the post. Each post says why it is high up (“From your area”, “Similar taste”, “Popular”). Why: hints as part of the contract; you can switch the order to “Newest”.',
            'Content: posts (text, photos, track links with the title and thumbnail fetched from Spotify or YouTube), reactions, comments under posts (including mentions of people and replies to other comments), chat messages, GIFs attached to comments and messages (we store the address of the file at the GIF provider, its size and description — not the file itself), friends and invitations, blocks. Why: running the Service — contract.',
            'GIF search: the phrase you type (or a request for trending GIFs) goes through our server to the GIF provider together with the app language and a random-looking identifier from which the provider cannot read your account. We do not store these phrases in the database; we keep the response in server memory for a few minutes. Why: running the Service — contract.',
            'Events: “Interested” and “Going” sign-ups (and whether you show on the attendee list), posts under events, reminders. Why: contract.',
            'Clans: membership and role, your vote on the clan colour, invitations (declined ones too, so nobody keeps inviting someone who said no), your requests to join clans (with a short message to the leaders, if you write one), clan chat and posts, replies and reactions to messages, a “read up to” marker (from which we count unread messages) and the chat mute setting, suggestions and votes in the “track of the week”, clan polls and the votes cast in them, titles in the clan (given, self-claimed or awarded automatically for activity), activity points and level in the clan ranking (counted from your messages, posts, suggestions, votes and reactions in the clan), the announcement, rules, motto, city, genres and visibility settings entered by the clan’s leaders. Why: contract. Administrator access to clans — section 4.',
            'Clan taste: artists and genres that at least two members have favourited are shown in aggregate, without naming anyone — to clan members and, if the clan is in the clan browser, to people outside it too (together with the chat activity level: quiet / calm / active / very active, without message content or counts). People who limited their profile to friends are not counted. In the clan browser we compare a clan’s taste with yours, but only you see the result. Why: contract.',
            'Notifications: bell notifications and — if you turn them on — push notifications (including for a new message in your clan chat, with the sender’s name and no message text; you can mute them): the subscription address issued by your browser, encryption keys, language. Why: delivering notifications — your consent (Art. 6(1)(a)); you can withdraw it at any time.',
            'E-mail: address confirmation, password reset, address change approval, password-changed notice. Why: contract and account security. We send no advertising.',
            'Moderation: reports (who reported whom and why; a snapshot of the reported post or comment or — for a clan report — of its name and description), decisions, warnings and bans. Why: protecting users and enforcing the Terms — legitimate interest (Art. 6(1)(f)) and duties under e-commerce rules.',
            'Technical data: last activity time (you can hide it), IP address and time of requests in server logs and in rate limits (e.g. for sending messages), entries about administrator visits to clans. Why: security, preventing abuse, diagnostics — legitimate interest.',
          ],
        },
        'Giving the data needed to create an account is voluntary, but without a username, e-mail address and password we can’t create one. We run no advertising analytics and sell no data.',
      ],
    },
    {
      tytul: '3. Who sees your data in the Service',
      akapity: [
        {
          lista: [
            'Your username, profile photo and clan badge are visible to logged-in users. The clan you are in is visible in the clan browser (its name, tag, icon, motto, city, genres, member count and founding date) — unless its leaders hide it in the settings. You can hide the rest of your profile (favourites, friends, posts, playlists) from non-friends.',
            'You set posts as public or friends-only. Comments under a post are seen by the same people who see the post (under a friends-only post — the author’s friends, under a clan post — clan members); comments by blocked people are invisible to each other. A person mentioned in a comment (@username) gets a notification if they can see that post. Clan posts, chat, announcement, rules, “track of the week” suggestions and votes, polls, the activity ranking and titles are seen only by clan members (the rules also by an invited person, before joining).',
            'You can hide that you are active, opt out of friend suggestions and hide yourself on event attendee lists by default.',
            'Your city is visible on your profile (in the full view, not on a friends-only profile) and on your card in friend suggestions — unless you turn off “Show my city on my profile and in friend suggestions”. In suggestions we only tell others whether you are from their city or nearby, without a distance. Your city affects the order of suggested people, events, clans and posts even when you hide it on your profile.',
            'A blocked person can’t see your profile or posts and can’t invite you; they are not told about the block.',
            'Only you and the other person see your chat messages — except as described in section 4 (reports).',
          ],
        },
      ],
    },
    {
      tytul: '4. Clans and administrator access',
      akapity: [
        'A clan’s chat and posts (with the comments under them) are seen by its members. The Service administrator may view them — and disband the clan — only when a report has been made, there is a reasonable suspicion of a breach of the Terms or the law (e.g. illegal content) or an authorised body requires it. Basis: the Controller’s legitimate interest and legal obligations (Art. 6(1)(f) and (c)).',
        'Each such visit is written to a log (who and which clan), and the clan page tells members that such access is possible. The administrator can also see the content of a conversation, post or comment that has been reported.',
      ],
    },
    {
      tytul: '5. Who we share data with',
      akapity: [
        {
          lista: [
            'The provider of the infrastructure the Service runs on (server, database, files, backups): {{hosting}}',
            'The mail provider we send messages through (processes your e-mail address and the content of account messages): {{poczta}}',
            'Browser push services — Google, Mozilla, Apple, Microsoft (depending on your browser): they receive the subscription address and an encrypted message they can’t read.',
            'Spotify and YouTube (Google): players and thumbnails load from their servers, so these companies see your IP address and may set their own cookies under their own policies.',
            'GIF provider (KLIPY or GIPHY — its name is shown under the GIF browser): receives the typed phrases and a pseudonymous identifier from our server, and your browser loads GIF files (previews and the chosen GIF) from it, so it sees your IP address and may set its own cookies under its own policies. It does not receive your username, e-mail address or the content of your conversations.',
            'Deezer and Last.fm: we send them artist and track names to look up the artist, covers and genres — no personal data. Ticketmaster: we fetch concert information from them; we send them no user data.',
            'Public authorities — only when the law requires it.',
          ],
        },
        'Some of these recipients (e.g. Google, Apple, Spotify) may process data outside the European Economic Area under the mechanisms provided by the GDPR (adequacy decision, standard contractual clauses).',
      ],
    },
    {
      tytul: '6. How long we keep data',
      akapity: [
        {
          lista: [
            'Account, profile and content — until you delete the account (you can do it at any time in Settings).',
            'Unconfirmed accounts — 7 days. Links in messages — 24 hours (password reset: 1 hour); their used technical entries disappear after a day.',
            'Comments — until deleted by their author, the post’s author, the Service administrator or (under a clan post) the clan’s leaders, or until the post or the author’s account is deleted. Replies under a comment, mentions and notifications about it disappear with it. A GIF disappears with the comment or message it is attached to.',
            'Bell notifications — until you or the account is deleted. Push devices — until you turn notifications off, log out, change your password or delete the account.',
            'Requests to join a clan — a pending one disappears when it is accepted, withdrawn or after 30 days; a declined one a week after the refusal (until then it protects against repeated requests).',
            'City — until you remove it (Settings) or the account is deleted.',
            'Reports and moderation decisions — until the reporting or the reported account is deleted.',
            'Server logs (IP address, time and address of requests — including the phrase typed in the GIF search — and entries about administrator visits to clans) — in rotation, usually up to a few weeks.',
            'After you delete your account, your profile, posts, photos, comments (with the replies under them), messages, reactions to clan messages, “track of the week” suggestions and votes, clan polls and your votes in them, titles, requests to join, event sign-ups, invitations, blocks, push devices and clan membership disappear (another member takes over as founder, and a clan you were alone in disappears). Deleted data may stay for a while in database backups, if made; they are overwritten in normal rotation.',
          ],
        },
      ],
    },
    {
      tytul: '7. Your rights',
      akapity: [
        {
          lista: [
            'Access, a copy of your data and portability: in Settings you can download your data in a machine-readable format.',
            'Rectification: change your username, e-mail, photo and settings in Settings.',
            'Erasure (“right to be forgotten”): delete your account in Settings.',
            'Restriction of processing and objection to processing based on legitimate interest: write to {{kontakt}}.',
            'Withdrawing consent (push notifications): in Settings or your browser settings — without affecting earlier processing.',
            'Complaint to the supervisory authority: the President of the Personal Data Protection Office (UODO), ul. Stawki 2, 00-193 Warsaw, Poland (uodo.gov.pl).',
          ],
        },
        'We take no decisions about you based solely on automated processing that have legal effects. “For you” event matching, friend suggestions and the order of posts in the “For you” feed use your favourites, friends and city but are only hints — you can turn suggestions off in your privacy settings and switch the order of posts to “Newest”.',
      ],
    },
    {
      tytul: '8. Cookies and browser storage',
      akapity: [
        'We use only what the Service needs to work, so we don’t ask for consent: session cookies (keep you signed in), a cookie protecting against forged requests (CSRF) and — if you tick “Remember me” — a persistent-login cookie. All are set with protective flags (HttpOnly, Secure, SameSite).',
        'In your browser storage we keep your language, colour theme, the last events city and view, the chosen “nearby” range, and a copy of the app files that lets it open faster and without a connection. The copy contains none of your account data. We use no advertising or analytics cookies. Spotify and YouTube, whose players you see in posts, and the GIF provider, from whose servers GIFs load, may set their own cookies.',
      ],
    },
    {
      tytul: '9. Security',
      akapity: [
        'The connection to the Service is encrypted (HTTPS). Passwords are stored as a hash (BCrypt). Changing your e-mail requires your password and approval from the old mailbox, changing your password logs out your other devices, and push notifications are encrypted end to end from our server to your browser. We limit how often messages can be sent. No system is completely secure — if a data breach that could result in a high risk to you occurs, we will notify you as the law requires.',
      ],
    },
    {
      tytul: '10. Children',
      akapity: [
        'The Service is for people aged 16 and over. If we learn that a younger person created an account, we will delete it. A parent or guardian who notices such an account can write to {{kontakt}}.',
      ],
    },
    {
      tytul: '11. Changes to this Policy',
      akapity: [
        'We announce significant changes in the Service and ask you to accept them again. The current version applies from {{wersja}}.',
      ],
    },
  ],
};

export default { pl, en };
