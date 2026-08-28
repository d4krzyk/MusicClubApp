package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Uzytkownik aplikacji - jedna encja = jedna tabela w bazie.
 *
 * <p><b>Realizuje wymagania z listy:</b></p>
 * <ul>
 *   <li>nr 1 - uzycie JPA (adnotacje {@code @Entity}, {@code @Id}, {@code @Column}),</li>
 *   <li>nr 4 - encja przechowujaca date/czas ({@code createdAt}),</li>
 *   <li>nr 7 - relacja {@code @ManyToMany} ({@link #friends}).</li>
 * </ul>
 */
@Entity
// "user" jest slowem zarezerwowanym w PostgreSQL, dlatego tabela nazywa sie "users".
@Table(name = "users")
public class User {

    /**
     * Klucz glowny. IDENTITY = numerowanie zalatwia baza (kolumna BIGSERIAL
     * w PostgreSQL), my nie musimy sami wymyslac ID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Login. {@code unique = true} zaklada w bazie indeks unikalny - dwoch takich samych nie bedzie. */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * Hash hasla (BCrypt), NIGDY haslo jawnym tekstem.
     * Hashowanie dojdzie w KROKU 3 razem ze Spring Security.
     */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * Data i godzina zalozenia konta - wymaganie nr 4 z listy.
     * Wykorzystamy ja pozniej do sortowania uzytkownikow "od najnowszych"
     * (wymaganie nr 5) i do wyswietlania "czlonek od ...".
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Rola uzytkownika. {@code EnumType.STRING} zapisuje w bazie tekst
     * ("USER"), a nie numer pozycji w enumie - dzieki temu dodanie nowej roli
     * w srodku listy nie popsuje istniejacych danych.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    /**
     * Czy konto jest aktywne. Przyda sie przy wymaganiu nr 16
     * (potwierdzenie maila) - do tego czasu zawsze {@code true}.
     */
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Termin oznaczajacy zakaz <b>bezterminowy</b>.
     *
     * <p>Nie jest to "koniec czasu", tylko data na tyle odlegla, ze zadne
     * konto jej nie dozyje. Dzieki temu zakaz bezterminowy jest zwyklym
     * zakazem z terminem - bez osobnej kolumny, osobnej flagi i osobnej
     * sciezki w kazdym sprawdzeniu.</p>
     *
     * <p><b>Rok 9999, a nie {@link LocalDateTime#MAX}.</b> {@code MAX} to
     * rok miliardowy z nanosekundami; PostgreSQL takiego znacznika nie
     * zapisze, a i w logach wygladalby jak usterka. Rok 9999 miesci sie
     * w kazdej bazie i od razu widac, ze jest umowny.</p>
     */
    public static final LocalDateTime FOREVER = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    /**
     * Czy podany termin oznacza zakaz bezterminowy.
     *
     * <p>Porownanie jest {@code >=}, a nie {@code ==}: gdyby kiedys ktos
     * zapisal termin jeszcze dalszy, ma byc traktowany tak samo. Pytanie
     * zadajemy w jednym miejscu, zeby interfejs i serwer nigdy nie
     * odpowiedzialy na nie inaczej.</p>
     */
    public static boolean isForever(LocalDateTime until) {
        return until != null && !until.isBefore(FOREVER);
    }

    /**
     * Do kiedy obowiazuje zakaz publikowania nalozony przez administratora.
     *
     * <p>{@code null} znaczy "bez zakazu". Data w przeszlosci tez znaczy
     * "bez zakazu" - zakaz <b>wygasa sam</b>, bez zadnego zadania w tle, bo
     * liczy sie wylacznie porownanie z chwila obecna. Wpisu nie kasujemy,
     * dzieki czemu administrator widzi w panelu, ze ktos byl juz kiedys
     * zablokowany.</p>
     *
     * <p><b>Dlaczego termin, a nie flaga.</b> Kara z terminem wygasa sama,
     * bez zadania w tle i bez polegania na tym, ze ktos o niej pamieta.</p>
     *
     * <p><b>A zakaz bezterminowy?</b> Poczatkowo go tu nie bylo, wlasnie
     * z powyzszego powodu. Okazal sie jednak potrzebny: przy koncie zalozonym
     * tylko po to, zeby dokuczac, "rok przerwy" jest udawaniem, ze sprawa
     * kiedys sama przyschnie. Zamiast dokladac druga kolumne z flaga -
     * i drugi stan do sprawdzania w kazdym miejscu - zapisujemy zakaz
     * bezterminowy jako {@link #FOREVER}, czyli termin tak odlegly, ze
     * praktycznie nie nadejdzie. Cala reszta kodu nie musi o tym wiedziec:
     * {@link #isPostingBanned()} dziala bez zmian, a jedyne miejsce, ktore
     * traktuje te date wyjatkowo, to interfejs - zeby pokazac
     * <i>„na zawsze"</i> zamiast <i>„do 31.12.9999"</i>.</p>
     */
    @Column(name = "posting_banned_until")
    private LocalDateTime postingBannedUntil;

    /**
     * Do kiedy obowiazuje zakaz WYSYLANIA WIADOMOSCI.
     *
     * <p>Osobny od {@link #postingBannedUntil} i to jest zmiana wzgledem
     * pierwszej wersji czatu. Poczatkowo zakaz publikowania obejmowal takze
     * wiadomosci - rozumowanie bylo takie, ze kara zostawiajaca otwarta droge
     * do pisania prywatnie nie jest kara. Odkad administrator ma DWA osobne
     * przelaczniki, to rozumowanie sie odwraca: gdyby zakaz publikowania
     * dalej po cichu wylaczal czat, nie dalo by sie w ogole ustawic
     * "nie wolno pisac postow, ale wolno rozmawiac ze znajomymi" - a to
     * najczestszy przypadek przy kims, kto zasmieca tablice, ale nikomu
     * nie dokucza.</p>
     *
     * <p>Kto chce obu kar naraz, wlacza obie. Kazda z nich wygasa sama,
     * bez zadania w tle - liczy sie wylacznie porownanie z chwila obecna.</p>
     */
    @Column(name = "messaging_banned_until")
    private LocalDateTime messagingBannedUntil;

    /**
     * Kiedy ta osoba ostatnio cokolwiek w aplikacji zrobila.
     *
     * <p>Na tym opiera sie kropka "online" i podpis "aktywny 5 minut temu".
     * {@code null} znaczy "nigdy" - konto zalozone i nieuzywane, albo takie,
     * ktore nie bylo uzywane od czasu dodania tej kolumny.</p>
     *
     * <p><b>Kolumna jest nullowalna z tego samego powodu co
     * {@code Post.visibility}</b>: projekt chodzi na {@code ddl-auto=update},
     * a Hibernate nie dolozy kolumny {@code NOT NULL} do tabeli, w ktorej sa
     * juz wiersze. Tu jednak nie ma czego uzupelniac migracja - "nie wiemy,
     * kiedy byl ostatnio" to uczciwa odpowiedz, a wpisanie tam daty startu
     * aplikacji byloby zmysleniem aktywnosci, ktorej nie bylo.</p>
     *
     * <p><b>Znaczenie slowa "online" jest przyblizone i inne byc nie moze.</b>
     * HTTP nie ma pojecia zamknietej karty: przegladarka nie melduje wyjscia,
     * a serwer dowiaduje sie o czyms wylacznie wtedy, gdy przychodzi
     * zapytanie. "Online" znaczy wiec: <i>cos robil w ciagu ostatnich kilku
     * minut</i> - patrz {@code PresenceService.ONLINE_WINDOW}.</p>
     */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /**
     * Nazwa pliku ze zdjeciem profilowym, np. {@code a1b2...ff.jpg}.
     * {@code null} oznacza brak zdjecia - interfejs pokazuje wtedy kolo
     * z pierwsza litera loginu.
     *
     * <p>W bazie trzymamy sama nazwe, a plik leży na dysku - tak samo jak
     * przy zdjeciach w postach.</p>
     */
    @Column(name = "avatar_file_name", length = 120)
    private String avatarFileName;

    /**
     * Znajomi - <b>wymaganie nr 7 (ManyToMany)</b>.
     *
     * <p>Relacja uzytkownika z samym soba: jeden uzytkownik ma wielu znajomych,
     * a kazdy z nich ma wielu swoich. W bazie powstaje osobna tabela
     * {@code user_friends} z dwiema kolumnami - kto i z kim.</p>
     *
     * <p><b>Znajomosc jest OBUSTRONNA i zapisujemy ja dwoma wierszami</b>
     * (A→B oraz B→A). Da sie inaczej - trzymac jeden wiersz i w kazdym
     * zapytaniu sprawdzac obie kolumny przez {@code OR} - ale wtedy KAZDE
     * pytanie o znajomych robi sie dwa razy trudniejsze do przeczytania.
     * Tu placimy jednym dodatkowym wierszem za to, ze zapytania sa proste.
     * Dopisywaniem obu stron zajmuje sie {@link #addFriend(User)}.</p>
     *
     * <p>{@code Set}, a nie {@code List}: tej samej osoby nie da sie miec
     * w znajomych dwa razy. Dziala to dzieki temu, ze {@code equals} i
     * {@code hashCode} nizej porownuja po identyfikatorze.</p>
     */
    @ManyToMany
    @JoinTable(
        name = "user_friends",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "friend_id"))
    private Set<User> friends = new HashSet<>();

    /**
     * Ulubieni wykonawcy - <b>serce dopasowywania ludzi po guscie</b>.
     *
     * <p>Kazdy element to wiersz w tabeli {@link Artist}, wspolny dla
     * wszystkich uzytkownikow. Porownanie dwoch osob sprowadza sie wiec do
     * policzenia czesci wspolnej dwoch zbiorow identyfikatorow - a nie do
     * porownywania tekstow, ktore roznilyby sie wielkoscia liter i literowkami.</p>
     *
     * <p>{@code LinkedHashSet} zamiast zwyklego {@code HashSet}, zeby na
     * profilu artysci pokazywali sie w kolejnosci dodawania. To czysto
     * wizualna sprawa, ale bez tego kolejnosc zmienialaby sie przy kazdym
     * odswiezeniu strony i wygladalo to jak usterka.</p>
     */
    @ManyToMany
    @JoinTable(
        name = "user_favorite_artists",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "artist_id"))
    private Set<Artist> favoriteArtists = new LinkedHashSet<>();

    /** Ulubione utwory - te same zasady co przy {@link #favoriteArtists}. */
    @ManyToMany
    @JoinTable(
        name = "user_favorite_tracks",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "track_id"))
    private Set<Track> favoriteTracks = new LinkedHashSet<>();

    /**
     * Metoda oznaczona {@code @PrePersist} uruchamia sie automatycznie tuz przed
     * pierwszym zapisem encji do bazy. Dzieki temu nie musimy pamietac o ustawianiu
     * daty w kazdym miejscu, gdzie tworzymy uzytkownika.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** Konstruktor bezargumentowy jest WYMAGANY przez JPA (Hibernate tworzy nim obiekty). */
    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getMessagingBannedUntil() {
        return messagingBannedUntil;
    }

    public void setMessagingBannedUntil(LocalDateTime messagingBannedUntil) {
        this.messagingBannedUntil = messagingBannedUntil;
    }

    /**
     * Czy zakaz wysylania wiadomosci obowiazuje <b>teraz</b>.
     *
     * <p>Ta sama zasada co przy {@link #isPostingBanned()}: pytanie zadajemy
     * encji, zeby regula "null albo przeszlosc znaczy: wolno" byla zapisana
     * raz. Wystarczy pomylic sie w jednym miejscu, zeby zakaz dalo sie
     * obejsc jednym niesprawdzonym wejsciem.</p>
     */
    public boolean isMessagingBanned() {
        return messagingBannedUntil != null && messagingBannedUntil.isAfter(LocalDateTime.now());
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getAvatarFileName() {
        return avatarFileName;
    }

    public void setAvatarFileName(String avatarFileName) {
        this.avatarFileName = avatarFileName;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getPostingBannedUntil() {
        return postingBannedUntil;
    }

    public void setPostingBannedUntil(LocalDateTime postingBannedUntil) {
        this.postingBannedUntil = postingBannedUntil;
    }

    /**
     * Czy zakaz publikowania obowiazuje <b>teraz</b>.
     *
     * <p>Pytanie zadajemy encji, a nie porownujemy dat w serwisie. Inaczej ta
     * sama regula ("null albo przeszlosc znaczy: wolno") musialaby byc
     * powtorzona w kazdym miejscu, ktore jej pilnuje - a wystarczy pomylic sie
     * raz, zeby zakaz dalo sie obejsc jednym niesprawdzonym wejsciem.</p>
     */
    public boolean isPostingBanned() {
        return postingBannedUntil != null && postingBannedUntil.isAfter(LocalDateTime.now());
    }

    public Set<User> getFriends() {
        return friends;
    }

    /**
     * Dodaje znajomego po OBU stronach relacji.
     *
     * <p>Gdyby dopisac tylko jedna strone, znajomy widzialby nas na swojej
     * liscie, a my jego nie - i nikt by nie zauwazyl, bo zadne zapytanie
     * by sie nie wywalilo.</p>
     *
     * <p><b>UWAGA na {@code inny.getFriends()} zamiast {@code inny.friends}.</b>
     * Java pozwala siegnac wprost do prywatnego pola innego obiektu tej samej
     * klasy - i wlasnie na tym mozna sie przejechac. {@code inny} bywa
     * <i>leniwym proxy</i> Hibernate'a (tak jest, gdy przychodzi
     * z {@code zaproszenie.getSender()}). Odczyt POLA na proxy siega do pustego
     * pola samego proxy, a nie do prawdziwej encji - dopisanie znika bez sladu
     * i bez bledu. Wywolanie METODY proxy przekazuje dalej, do wlasciwego
     * obiektu, wiec dziala poprawnie.</p>
     */
    public void addFriend(User other) {
        this.friends.add(other);
        other.getFriends().add(this);
    }

    /** Usuwa znajomosc po obu stronach - z ta sama uwaga o proxy co wyzej. */
    public void removeFriend(User other) {
        this.friends.remove(other);
        other.getFriends().remove(this);
    }

    /*
     * Ulubione sa relacja JEDNOSTRONNA - inaczej niz znajomosc. Nie ma tu
     * odpowiednika addFriend(): polubienie artysty nie powoduje, ze
     * artysta "polubil" nas. Encja Artist nie wie nawet, kto ja lubi -
     * i nie musi wiedziec, bo pytamy zawsze od strony uzytkownika.
     */

    public Set<Artist> getFavoriteArtists() {
        return favoriteArtists;
    }

    public Set<Track> getFavoriteTracks() {
        return favoriteTracks;
    }

    /**
     * equals/hashCode po ID. Wazne przy encjach - domyslne porownywanie po
     * referencji potrafi psuc dzialanie kolekcji (Set) i cache'u Hibernate'a.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', createdAt=" + createdAt + "}";
    }
}
