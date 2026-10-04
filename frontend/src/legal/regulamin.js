/*
 * REGULAMIN - tresc.
 *
 * To SZABLON przygotowany dla serwisu spolecznosciowego, a nie porada prawna: przed wpuszczeniem
 * prawdziwych uzytkownikow powinien go przeczytac prawnik. Dane administratora ({{administrator}},
 * {{kontakt}}) przychodza z serwera (LEGAL_CONTROLLER, LEGAL_CONTACT_EMAIL).
 *
 * KAZDA zmiana tresci = podniesienie app.legal.version w application.properties, zeby uzytkownicy
 * dostali prosbe o ponowna akceptacje.
 *
 * Akapit to zwykly tekst albo { lista: [...] }.
 */

const pl = {
  tytul: 'Regulamin serwisu MusicClub',
  sekcje: [
    {
      tytul: '1. Postanowienia ogólne',
      akapity: [
        'Regulamin określa zasady korzystania z serwisu i aplikacji MusicClub („Serwis”) — społecznościowej platformy dla osób, które lubią muzykę: profili, postów, znajomych, czatu, wydarzeń koncertowych i klanów.',
        'Usługodawcą jest {{administrator}} („Usługodawca”). Kontakt w sprawach Serwisu, reklamacji i danych osobowych: {{kontakt}}.',
        'Korzystanie z Serwisu jest bezpłatne. Umowa o świadczenie usług drogą elektroniczną zostaje zawarta w chwili założenia konta i trwa do jego usunięcia.',
        'Zakładając konto akceptujesz Regulamin i potwierdzasz zapoznanie się z Polityką prywatności (osoba korzystająca z konta to „Ty” albo „Użytkownik”).',
      ],
    },
    {
      tytul: '2. Konto',
      akapity: [
        {
          lista: [
            'Konto może założyć osoba, która ukończyła 16 lat.',
            'Jedna osoba ma jedno konto. Podajesz swój prawdziwy adres e-mail i potwierdzasz go linkiem z wiadomości. Adresy jednorazowych skrzynek są odrzucane.',
            'Hasło jest tajne. Nie udostępniaj go nikomu. Jeśli podejrzewasz, że ktoś je zna, zmień je i użyj w Ustawieniach opcji „Wyloguj z innych urządzeń”.',
            'Dla ochrony konta zmiana adresu e-mail wymaga podania hasła oraz zgody ze starej skrzynki, a reset hasła działa tylko przez obecny adres.',
            'Login nie może naruszać cudzych praw ani udawać Usługodawcy lub jego obsługi.',
          ],
        },
      ],
    },
    {
      tytul: '3. Czego nie wolno',
      akapity: [
        'W Serwisie nie wolno publikować ani wysyłać treści i zachowań, które:',
        {
          lista: [
            'są sprzeczne z prawem, w szczególności zawierają groźby, nawołują do przemocy lub nienawiści, przedstawiają seksualne wykorzystywanie małoletnich albo propagują zakazane ideologie;',
            'nękają, poniżają lub dyskryminują inne osoby albo ujawniają cudze dane osobowe bez ich zgody;',
            'podszywają się pod inną osobę, organizację lub obsługę Serwisu;',
            'są spamem, reklamą albo masowymi, niechcianymi zaproszeniami i wiadomościami;',
            'naruszają cudze prawa autorskie lub prawa do wizerunku — wgrywaj tylko zdjęcia i teksty, do których masz prawa;',
            'na karcie profilu: przedstawiają inne osoby bez ich zgody, mają charakter seksualny albo udają kogoś, kim nie jesteś;',
            'zakłócają działanie Serwisu: próby obejścia zabezpieczeń, automatyczne pobieranie danych, wgrywanie złośliwego kodu.',
          ],
        },
        'Serwis nie przechowuje nagrań muzycznych. Utwory, albumy i wykonawców pokazujemy przez wbudowane odtwarzacze i linki Spotify oraz YouTube — obowiązują je regulaminy tych serwisów.',
      ],
    },
    {
      tytul: '4. Twoje treści',
      akapity: [
        'Prawa do zdjęć, postów, wiadomości i opisów, które publikujesz, pozostają przy Tobie. Odpowiadasz za ich treść.',
        'Udzielasz Usługodawcy nieodpłatnej, niewyłącznej licencji na przechowywanie i wyświetlanie tych treści w Serwisie — wyłącznie w zakresie potrzebnym do świadczenia usługi i tylko osobom, którym je udostępniasz zgodnie z ustawieniami widoczności. Licencja wygasa wraz z usunięciem treści lub konta.',
        'Na karcie profilu możesz pokazać do 6 zdjęć, kilka słów o sobie, to, czego szukasz, i odpowiedzi na muzyczne pytania. W trybie Poznawaj (włączasz go w zakładce Znajomi, a wyłączasz w każdej chwili w Ustawieniach) Twoja karta trafia do osób, które też go włączyły — znajomymi zostajecie tylko wtedy, gdy obie osoby powiedzą „tak”. Dzienny limit decyzji chroni przed masowym przeglądaniem kart. Administrator może wyczyścić kartę naruszającą Regulamin i wyłączyć tryb Poznawaj.',
        'Post możesz ustawić jako publiczny albo tylko dla znajomych, a profil ukryć przed osobami spoza znajomych. Pamiętaj jednak, że każda osoba, która widzi treść, może zrobić jej zrzut ekranu — Serwis tego nie uniemożliwi.',
        'Pod postem możesz pisać komentarze, odpowiadać na cudze komentarze i oznaczać osoby znakiem @ przed loginem. Komentarz widzą te same osoby, które widzą post. Skasować go może jego autor, autor posta, administrator Serwisu, a pod postem klanu także zarząd klanu; razem z komentarzem znikają odpowiedzi pod nim. Oznaczanie osób nie służy do nękania ani rozsyłania spamu. Do komentarza i wiadomości możesz dołączyć GIF z wbudowanej przeglądarki GIF-ów. GIF-y pochodzą od zewnętrznego dostawcy i nie podlegają naszej redakcji — jeśli któryś narusza Regulamin, zgłoś komentarz lub rozmowę przyciskiem „Zgłoś”. Dostępność GIF-ów zależy od dostawcy.',
      ],
    },
    {
      tytul: '5. Wydarzenia',
      akapity: [
        'Informacje o koncertach pochodzą z serwisu Ticketmaster i mają charakter informacyjny. Nie gwarantujemy ich aktualności — o terminie, miejscu i cenie decyduje organizator. Bilety kupujesz u organizatora lub sprzedawcy, a nie w Serwisie.',
        'Oznaczenie „Zainteresowany” lub „Biorę udział” nie jest rezerwacją. Przypomnienia o wydarzeniach są dodatkiem i nie zastępują sprawdzenia terminu u organizatora. Na liście uczestników możesz się ukryć — liczysz się wtedy tylko do licznika.',
        'Posty pod wydarzeniem działają jak zwykłe posty (widoczność, blokady, zgłoszenia).',
      ],
    },
    {
      tytul: '6. Klany',
      akapity: [
        {
          lista: [
            'Do klanu dołącza się za zgodą klanu: z zaproszenia od jego członka albo — jeśli klan to włączył — przez prośbę o dołączenie, którą przyjmuje lub odrzuca zarząd klanu (odmowa nie wymaga uzasadnienia; po odmowie można poprosić ponownie po tygodniu). Zaproszenie możesz przyjąć albo odrzucić, a w ustawieniach prywatności — ograniczyć, kto może Cię zapraszać (wszyscy, tylko znajomi, nikt). Możesz należeć do jednego klanu.',
            'Klanem zarządzają jego założyciel i administratorzy klanu: zapraszają, rozpatrują prośby o dołączenie, wyrzucają członków, zmieniają opis, ikonę i zdjęcie, wpisują przypięte ogłoszenie i krótkie zasady klanu, hasło, miasto i gatunki klanu, nadają tytuły oraz decydują, czy klan jest widoczny w przeglądarce klanów. Kolor klanu wybierają jego członkowie głosowaniem. Nazwa, skrót, ikona, zdjęcie, opis, hasło, miasto, gatunki, liczba członków i lista członków są widoczne dla wszystkich zalogowanych osób, a zbiorczy gust członków i poziom aktywności czatu (bez treści wiadomości) — także dla osób spoza klanu, jeśli klan jest w przeglądarce klanów; zasady widzi także osoba zaproszona. Zasady klanu nie mogą być sprzeczne z Regulaminem.',
            'W klanie możesz pisać na czacie (odpowiadać na wiadomości i reagować emoji), dodawać posty, proponować „utwór tygodnia”, zakładać ankiety i głosować w nich, zdobywać tytuły i punkty w rankingu aktywności, głosować na propozycje innych osób oraz sprawdzać, na jakie koncerty zapisali się członkowie. Możesz wyciszyć powiadomienia z czatu klanu.',
            'Każdy klan możesz zgłosić administratorowi Serwisu (np. za obraźliwą nazwę, opis albo obraz).',
            'Czat i posty klanu widzą jego członkowie. Administrator Serwisu może je przeglądać wyłącznie wtedy, gdy wpłynie zgłoszenie, istnieje uzasadnione podejrzenie naruszenia Regulaminu lub prawa albo żąda tego uprawniony organ; każde takie wejście jest zapisywane w dzienniku. Klan nie może więc ukryć przed moderacją treści niezgodnych z prawem lub z Regulaminem.',
            'Po odejściu z klanu lub wyrzuceniu Twoje posty, komentarze pod postami klanu i wiadomości pozostają w klanie. Usuwając konto, usuwasz własne posty, komentarze i wiadomości.',
            'Nazwa i skrót klanu nie mogą udawać Serwisu ani jego obsługi. Usługodawca może rozwiązać klan naruszający Regulamin.',
          ],
        },
      ],
    },
    {
      tytul: '7. Zgłoszenia i moderacja',
      akapity: [
        'Profil, post, komentarz lub rozmowę, które naruszają Regulamin, możesz zgłosić przyciskiem „Zgłoś”. Zgłoszenia rozpatruje administrator: może je odrzucić, usunąć treść, nałożyć czasowy lub stały zakaz publikowania albo pisania wiadomości, a w skrajnych przypadkach usunąć konto.',
        'Osobę zgłaszającą informujemy o rozpatrzeniu zgłoszenia. Jeśli nie zgadzasz się z decyzją dotyczącą Twojego konta lub treści, napisz na {{kontakt}} — sprawę rozpatrzymy ponownie.',
        'O treści bezprawnej możesz też zawiadomić nas wiadomością na {{kontakt}}, podając adres treści i uzasadnienie. Po uzyskaniu wiarygodnej wiadomości o jej bezprawności reagujemy niezwłocznie.',
        'Możesz zablokować dowolną osobę: znika wtedy z Twoich znajomych, nie widzi Twoich postów i profilu i nie może Cię zapraszać.',
      ],
    },
    {
      tytul: '8. Wiadomości i powiadomienia',
      akapity: [
        'Wysyłamy wyłącznie wiadomości e-mail związane z kontem (potwierdzenie adresu, reset hasła, zmiana adresu, informacja o zmianie hasła). Nie wysyłamy reklam.',
        'Powiadomienia na telefon (push) włączasz w Ustawieniach po wyrażeniu zgody w przeglądarce; możesz je wyłączyć w każdej chwili tam lub w ustawieniach przeglądarki.',
      ],
    },
    {
      tytul: '9. Usunięcie konta',
      akapity: [
        'Konto możesz usunąć w każdej chwili w Ustawieniach. Usunięcie kasuje profil, posty, komentarze, zdjęcia, wiadomości, zapisy na wydarzenia, zaproszenia, blokady i zapisane urządzenia powiadomień; zasady szczegółowe opisuje Polityka prywatności. Niepotwierdzone konta usuwamy po 7 dniach.',
        'Usługodawca może zawiesić lub usunąć konto, które narusza Regulamin — po ostrzeżeniu, a przy poważnych naruszeniach od razu.',
      ],
    },
    {
      tytul: '10. Odpowiedzialność i dostępność',
      akapity: [
        'Serwis dostarczamy w stanie, w jakim jest. Dokładamy starań, by działał bez przerw, ale nie gwarantujemy tego — zdarzają się przerwy techniczne i awarie.',
        'Nie odpowiadamy za treści publikowane przez Użytkowników ani za dane z zewnętrznych serwisów (Ticketmaster, Spotify, YouTube, Last.fm, Deezer). Postanowienia Regulaminu nie ograniczają uprawnień konsumenta wynikających z bezwzględnie obowiązujących przepisów.',
      ],
    },
    {
      tytul: '11. Reklamacje',
      akapity: [
        'Reklamacje dotyczące działania Serwisu składasz na {{kontakt}}, podając login i opis problemu. Odpowiadamy w ciągu 14 dni.',
      ],
    },
    {
      tytul: '12. Zmiany Regulaminu',
      akapity: [
        'O zmianach Regulaminu i Polityki prywatności informujemy w Serwisie i prosimy o ponowną akceptację. Jeśli ich nie akceptujesz, możesz usunąć konto. Obecna wersja obowiązuje od {{wersja}}.',
      ],
    },
    {
      tytul: '13. Postanowienia końcowe',
      akapity: [
        'Do umowy stosuje się prawo polskie. Wobec konsumentów nie pozbawia to ochrony, która przysługuje im na mocy przepisów państwa ich zamieszkania. Spory rozstrzygają sądy powszechne właściwe zgodnie z przepisami.',
      ],
    },
  ],
};

const en = {
  tytul: 'MusicClub Terms of Service',
  sekcje: [
    {
      tytul: '1. General',
      akapity: [
        'These Terms set the rules for using the MusicClub website and app (the “Service”) — a social platform for people who love music: profiles, posts, friends, chat, concert events and clans.',
        'The service provider is {{administrator}} (the “Provider”). Contact for Service matters, complaints and personal data: {{kontakt}}.',
        'The Service is free. The contract for electronic services is concluded when you create an account and lasts until you delete it.',
        'By creating an account you accept these Terms and confirm that you have read the Privacy Policy.',
      ],
    },
    {
      tytul: '2. Your account',
      akapity: [
        {
          lista: [
            'You can create an account if you are at least 16 years old.',
            'One person, one account. You give your own real e-mail address and confirm it with the link we send. Disposable mailboxes are rejected.',
            'Your password is secret. Don’t share it. If you suspect someone knows it, change it and use “Log out on other devices” in Settings.',
            'To protect accounts, changing the e-mail address requires your password and approval from the old mailbox, and a password reset only works through the current address.',
            'Your username must not infringe anyone’s rights or imitate the Provider or its staff.',
          ],
        },
      ],
    },
    {
      tytul: '3. What is not allowed',
      akapity: [
        'You must not publish or send content or behave in a way that:',
        {
          lista: [
            'is unlawful — in particular contains threats, calls for violence or hatred, depicts sexual exploitation of minors or promotes banned ideologies;',
            'harasses, demeans or discriminates against others, or reveals other people’s personal data without their consent;',
            'impersonates another person, organisation or the Service’s staff;',
            'is spam, advertising or mass unwanted invitations and messages;',
            'infringes copyright or image rights — upload only photos and text you have the rights to;',
            'on a profile card: shows other people without their consent, is sexual in nature or pretends you are someone you are not;',
            'disrupts the Service: bypassing security, automated data scraping, uploading malicious code.',
          ],
        },
        'The Service does not store music recordings. We show tracks, albums and artists through embedded players and links from Spotify and YouTube — their terms apply.',
      ],
    },
    {
      tytul: '4. Your content',
      akapity: [
        'You keep the rights to the photos, posts, messages and descriptions you publish. You are responsible for them.',
        'You grant the Provider a free, non-exclusive licence to store and display this content in the Service — only as far as needed to provide it, and only to the people you share it with under your visibility settings. The licence ends when you delete the content or the account.',
        'On your profile card you can show up to 6 photos, a few words about yourself, what you are looking for and answers to music questions. In Discover (you turn it on in the Friends tab and can turn it off at any time in Settings) your card goes to people who also turned it on — you become friends only when both of you say “yes”. A daily limit on decisions protects against mass browsing of cards. The administrator may clear a card that breaches the Terms and turn Discover off.',
        'You can make a post public or friends-only, and hide your profile from non-friends. Remember that anyone who can see something can take a screenshot — the Service can’t prevent that.',
        'Under a post you can write comments, reply to other people’s comments and mention people with @ before their username. A comment is seen by the same people who see the post. It can be deleted by its author, the post’s author, the Service administrator and, under a clan post, the clan’s leaders; replies under a comment disappear with it. Mentions are not for harassment or spam. You can attach a GIF from the built-in GIF browser to a comment or message. GIFs come from an external provider and are not edited by us — if one breaks the Terms, report the comment or conversation with the “Report” button. GIF availability depends on the provider.',
      ],
    },
    {
      tytul: '5. Events',
      akapity: [
        'Concert information comes from Ticketmaster and is informational only. We don’t guarantee it is up to date — the organiser decides the date, place and price. You buy tickets from the organiser or seller, not in the Service.',
        '“Interested” or “Going” is not a reservation. Event reminders are an extra and don’t replace checking the date with the organiser. You can hide yourself on the attendee list — you then count only in the total.',
        'Posts under an event work like ordinary posts (visibility, blocks, reports).',
      ],
    },
    {
      tytul: '6. Clans',
      akapity: [
        {
          lista: [
            'You join a clan with the clan’s consent: by invitation from one of its members or — if the clan allows it — by a request to join that the clan’s leaders accept or decline (a refusal needs no justification; after one you can ask again a week later). You can accept or decline an invitation, and limit in your privacy settings who may invite you (everyone, friends only, nobody). You can belong to one clan.',
            'A clan is run by its founder and clan admins: they invite, handle requests to join, remove members, change the description, icon and photo, write a pinned announcement and short clan rules, set the clan’s motto, city and genres, give titles, and decide whether the clan is visible in the clan browser. The clan colour is chosen by its members by voting. The clan’s name, tag, icon, photo, description, motto, city, genres, member count and member list are visible to all logged-in users, and the aggregate taste of its members and the chat activity level (without message content) also to people outside the clan if the clan is in the clan browser; the rules are also visible to an invited person. Clan rules must not contradict the Terms.',
            'In a clan you can chat (reply to messages and react with emoji), add posts, suggest a “track of the week”, create polls and vote in them, earn titles and points in the activity ranking, vote on other people’s suggestions, and see which concerts members have signed up for. You can mute notifications from the clan chat.',
            'You can report any clan to the Service administrator (e.g. for an offensive name, description or image).',
            'A clan’s chat and posts are visible to its members. The Service administrator may view them only when a report has been made, there is a reasonable suspicion of a breach of the Terms or the law, or an authorised body requires it; each such visit is written to a log. A clan therefore cannot hide unlawful or rule-breaking content from moderation.',
            'After you leave or are removed, your posts, comments under clan posts and messages stay in the clan. By deleting your account you delete your own posts, comments and messages.',
            'A clan’s name and tag must not imitate the Service or its staff. The Provider may disband a clan that breaks the Terms.',
          ],
        },
      ],
    },
    {
      tytul: '7. Reports and moderation',
      akapity: [
        'You can report a profile, post, comment or conversation that breaks the Terms with the “Report” button. An administrator reviews reports and may dismiss them, remove content, impose a temporary or permanent ban on posting or messaging and, in extreme cases, delete the account.',
        'We tell the reporter when a report has been decided. If you disagree with a decision about your account or content, write to {{kontakt}} and we will review it again.',
        'You can also notify us of unlawful content by writing to {{kontakt}} with the content’s address and your reasons. Once we receive credible notice that it is unlawful, we act without delay.',
        'You can block anyone: they disappear from your friends, can’t see your posts and profile and can’t invite you.',
      ],
    },
    {
      tytul: '8. Messages and notifications',
      akapity: [
        'We send only account-related e-mail (address confirmation, password reset, address change, password-changed notice). We send no advertising.',
        'Phone (push) notifications are turned on by you in Settings after you allow them in the browser; you can turn them off at any time there or in your browser settings.',
      ],
    },
    {
      tytul: '9. Deleting your account',
      akapity: [
        'You can delete your account at any time in Settings. Deletion removes your profile, posts, comments, photos, messages, event sign-ups, invitations, blocks and saved notification devices; the Privacy Policy gives the details. Unconfirmed accounts are deleted after 7 days.',
        'The Provider may suspend or delete an account that breaks the Terms — after a warning, or immediately for serious breaches.',
      ],
    },
    {
      tytul: '10. Liability and availability',
      akapity: [
        'The Service is provided as it is. We do our best to keep it running without interruption but can’t guarantee it — there are technical breaks and outages.',
        'We are not responsible for content published by users or for data from external services (Ticketmaster, Spotify, YouTube, Last.fm, Deezer). Nothing in these Terms limits a consumer’s rights under mandatory law.',
      ],
    },
    {
      tytul: '11. Complaints',
      akapity: [
        'Send complaints about how the Service works to {{kontakt}} with your username and a description of the problem. We reply within 14 days.',
      ],
    },
    {
      tytul: '12. Changes to the Terms',
      akapity: [
        'We announce changes to the Terms and the Privacy Policy in the Service and ask you to accept them again. If you don’t accept them, you can delete your account. The current version applies from {{wersja}}.',
      ],
    },
    {
      tytul: '13. Final provisions',
      akapity: [
        'The contract is governed by Polish law. For consumers this does not remove the protection given by the law of the country where they live. Disputes are decided by the competent courts.',
      ],
    },
  ],
};

export default { pl, en };
