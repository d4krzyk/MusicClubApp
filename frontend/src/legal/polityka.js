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
            'Treści: posty (tekst, zdjęcia, linki do utworów wraz z tytułem i miniaturą pobranymi ze Spotify lub YouTube), reakcje, wiadomości na czacie, znajomi i zaproszenia, blokady. Po co: działanie Serwisu — umowa.',
            'Wydarzenia: zapisy „Zainteresowany” i „Biorę udział” (i wybór, czy pokazywać Cię na liście uczestników), posty pod wydarzeniami, przypomnienia. Po co: umowa.',
            'Klany: członkostwo i rola, głos na kolor klanu, zaproszenia (także odrzucone — żeby nikt nie zapraszał w kółko osoby, która odmówiła), czat i posty klanu. Po co: umowa. O dostępie administratora do klanów — punkt 4.',
            'Powiadomienia: powiadomienia w dzwonku oraz — jeśli je włączysz — powiadomienia push: adres subskrypcji nadany przez przeglądarkę, klucze szyfrujące, język. Po co: dostarczanie powiadomień — Twoja zgoda (art. 6 ust. 1 lit. a); cofniesz ją w każdej chwili.',
            'Wiadomości e-mail: potwierdzenie adresu, reset hasła, zgoda na zmianę adresu, informacja o zmianie hasła. Po co: umowa i bezpieczeństwo konta. Nie wysyłamy reklam.',
            'Moderacja: zgłoszenia (kto, kogo i dlaczego; migawka zgłoszonego posta), decyzje, ostrzeżenia i zakazy. Po co: ochrona użytkowników i egzekwowanie Regulaminu — prawnie uzasadniony interes (art. 6 ust. 1 lit. f) oraz obowiązki wynikające z przepisów o usługach elektronicznych.',
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
            'Login, zdjęcie profilowe i plakietka klanu są widoczne dla zalogowanych osób. Resztę profilu (ulubieni, znajomi, posty, playlisty) możesz ukryć przed osobami spoza znajomych.',
            'Posty ustawiasz jako publiczne albo tylko dla znajomych. Posty klanu widzą tylko członkowie klanu.',
            'Możesz ukryć, że jesteś aktywny, wyłączyć pojawianie się w propozycjach znajomych i domyślnie ukrywać się na listach uczestników wydarzeń.',
            'Zablokowana osoba nie widzi Twojego profilu ani postów i nie może Cię zapraszać; nie dowiaduje się o blokadzie.',
            'Wiadomości na czacie widzisz tylko Ty i druga osoba — z wyjątkiem opisanym w punkcie 4 (zgłoszenia).',
          ],
        },
      ],
    },
    {
      tytul: '4. Klany i dostęp administratora',
      akapity: [
        'Czat i posty klanu widzą jego członkowie. Administrator Serwisu może je przeglądać — a także rozwiązać klan — wyłącznie wtedy, gdy wpłynęło zgłoszenie, istnieje uzasadnione podejrzenie naruszenia Regulaminu lub prawa (np. treści nielegalnych) albo żąda tego uprawniony organ. Podstawa: prawnie uzasadniony interes Administratora i obowiązki prawne (art. 6 ust. 1 lit. f i c).',
        'Każde takie wejście zostaje zapisane w dzienniku (kto i który klan), a strona klanu informuje o możliwości takiego dostępu. Administrator może też zobaczyć treść rozmowy lub posta, który został zgłoszony.',
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
            'Powiadomienia w dzwonku — do usunięcia przez Ciebie albo konta. Urządzenia push — do wyłączenia powiadomień, wylogowania, zmiany hasła lub usunięcia konta.',
            'Zgłoszenia i decyzje moderacyjne — do usunięcia konta osoby zgłaszającej lub zgłoszonej.',
            'Dzienniki serwera (adres IP, czas i rodzaj zapytań, wpisy o wejściach administratora do klanów) — w rotacji, zwykle do kilku tygodni.',
            'Po usunięciu konta znikają Twój profil, posty, zdjęcia, wiadomości, zapisy na wydarzenia, zaproszenia, blokady, urządzenia push i członkostwo w klanie (założyciela zastępuje inny członek, a klan, w którym nie było nikogo poza Tobą, znika). Usunięte dane mogą przez pewien czas zostać w kopiach zapasowych bazy, o ile są wykonywane; są nadpisywane w normalnej rotacji.',
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
        'Nie podejmujemy wobec Ciebie decyzji opartych wyłącznie na zautomatyzowanym przetwarzaniu, które wywołują skutki prawne. Dopasowanie wydarzeń „Dla ciebie” i propozycje znajomych korzystają z Twoich ulubionych i znajomych, ale służą tylko do podpowiedzi — propozycje możesz wyłączyć w ustawieniach prywatności.',
      ],
    },
    {
      tytul: '8. Pliki cookie i pamięć przeglądarki',
      akapity: [
        'Używamy wyłącznie plików niezbędnych do działania Serwisu, więc nie pytamy o zgodę na nie: ciasteczka sesji (utrzymują zalogowanie), ciasteczko zabezpieczające przed fałszowaniem żądań (CSRF) i — jeśli zaznaczysz „Zapamiętaj mnie” — ciasteczko trwałego logowania. Wszystkie mają ustawienia zabezpieczające (HttpOnly, Secure, SameSite).',
        'W pamięci przeglądarki zapisujemy język, motyw kolorystyczny, ostatnio wybrane miasto i widok wydarzeń oraz kopię plików aplikacji, dzięki której otwiera się szybciej i bez zasięgu. Kopia nie zawiera Twoich danych z konta. Nie używamy cookie reklamowych ani analitycznych. Spotify i YouTube, których odtwarzacze widzisz w postach, mogą ustawiać własne pliki cookie.',
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
            'Content: posts (text, photos, track links with the title and thumbnail fetched from Spotify or YouTube), reactions, chat messages, friends and invitations, blocks. Why: running the Service — contract.',
            'Events: “Interested” and “Going” sign-ups (and whether you show on the attendee list), posts under events, reminders. Why: contract.',
            'Clans: membership and role, your vote on the clan colour, invitations (declined ones too, so nobody keeps inviting someone who said no), clan chat and posts. Why: contract. Administrator access to clans — section 4.',
            'Notifications: bell notifications and — if you turn them on — push notifications: the subscription address issued by your browser, encryption keys, language. Why: delivering notifications — your consent (Art. 6(1)(a)); you can withdraw it at any time.',
            'E-mail: address confirmation, password reset, address change approval, password-changed notice. Why: contract and account security. We send no advertising.',
            'Moderation: reports (who reported whom and why; a snapshot of the reported post), decisions, warnings and bans. Why: protecting users and enforcing the Terms — legitimate interest (Art. 6(1)(f)) and duties under e-commerce rules.',
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
            'Your username, profile photo and clan badge are visible to logged-in users. You can hide the rest of your profile (favourites, friends, posts, playlists) from non-friends.',
            'You set posts as public or friends-only. Clan posts are seen only by clan members.',
            'You can hide that you are active, opt out of friend suggestions and hide yourself on event attendee lists by default.',
            'A blocked person can’t see your profile or posts and can’t invite you; they are not told about the block.',
            'Only you and the other person see your chat messages — except as described in section 4 (reports).',
          ],
        },
      ],
    },
    {
      tytul: '4. Clans and administrator access',
      akapity: [
        'A clan’s chat and posts are seen by its members. The Service administrator may view them — and disband the clan — only when a report has been made, there is a reasonable suspicion of a breach of the Terms or the law (e.g. illegal content) or an authorised body requires it. Basis: the Controller’s legitimate interest and legal obligations (Art. 6(1)(f) and (c)).',
        'Each such visit is written to a log (who and which clan), and the clan page tells members that such access is possible. The administrator can also see the content of a conversation or post that has been reported.',
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
            'Bell notifications — until you or the account is deleted. Push devices — until you turn notifications off, log out, change your password or delete the account.',
            'Reports and moderation decisions — until the reporting or the reported account is deleted.',
            'Server logs (IP address, time and kind of requests, entries about administrator visits to clans) — in rotation, usually up to a few weeks.',
            'After you delete your account, your profile, posts, photos, messages, event sign-ups, invitations, blocks, push devices and clan membership disappear (another member takes over as founder, and a clan you were alone in disappears). Deleted data may stay for a while in database backups, if made; they are overwritten in normal rotation.',
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
        'We take no decisions about you based solely on automated processing that have legal effects. “For you” event matching and friend suggestions use your favourites and friends but are only hints — you can turn suggestions off in your privacy settings.',
      ],
    },
    {
      tytul: '8. Cookies and browser storage',
      akapity: [
        'We use only what the Service needs to work, so we don’t ask for consent: session cookies (keep you signed in), a cookie protecting against forged requests (CSRF) and — if you tick “Remember me” — a persistent-login cookie. All are set with protective flags (HttpOnly, Secure, SameSite).',
        'In your browser storage we keep your language, colour theme, the last events city and view, and a copy of the app files that lets it open faster and without a connection. The copy contains none of your account data. We use no advertising or analytics cookies. Spotify and YouTube, whose players you see in posts, may set their own cookies.',
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
