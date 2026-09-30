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

/** Uzytkownik aplikacji - jedna encja = jedna tabela w bazie. */
@Entity
// "user" jest slowem zarezerwowanym w PostgreSQL, dlatego tabela nazywa sie "users".
@Table(name = "users")
public class User {

    /** Klucz glowny. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Login. {@code unique = true} zaklada w bazie indeks unikalny - dwoch takich samych nie bedzie. */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** Hash hasla (BCrypt), NIGDY haslo jawnym tekstem. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Data i godzina zalozenia konta - wymaganie nr 4 z listy. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Rola uzytkownika. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    /** Czy konto jest aktywne. */
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Kiedy adres e-mail zostal potwierdzony linkiem z wiadomosci. Pusty =
     * jeszcze nie. Gdy serwer ma skonfigurowana poczte, takie konto nie
     * moze sie zalogowac - to cala ochrona przed kontami na zmyslone adresy.
     */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /**
     * Nowy adres, na ktory wyslalismy link przy zmianie e-maila w profilu.
     * Do czasu klikniecia obowiazuje stary - inaczej wystarczyloby
     * potwierdzic prawdziwy adres i od razu podmienic go na zmyslony.
     */
    @Column(name = "pending_email", length = 255)
    private String pendingEmail;

    /**
     * Zmiana adresu wymaga DWOCH klikniec: zgody ze starej skrzynki i
     * potwierdzenia nowej. Bez zgody ze starej ktos, kto przejal sesje, mogl
     * podmienic adres na swoj, a potem przez "nie pamietam hasla" zabrac konto.
     */
    @Column(name = "pending_email_old_approved_at")
    private LocalDateTime pendingEmailOldApprovedAt;

    @Column(name = "pending_email_new_verified_at")
    private LocalDateTime pendingEmailNewVerifiedAt;

    /**
     * Znacznik bezpieczenstwa. Zmienia sie przy zmianie i resecie hasla oraz
     * przy "wyloguj z innych urzadzen". Sesja zapamietuje go przy logowaniu,
     * a podpis ciasteczka "zapamietaj mnie" go zawiera - po zmianie znacznika
     * wszystkie inne urzadzenia sa wylogowane.
     */
    @Column(name = "security_stamp", length = 32)
    private String securityStamp;

    /* --- Prywatnosc ---------------------------------------------------
     * Wartosci domyslne siedza takze w bazie (@ColumnDefault): kolumna
     * NOT NULL dodawana do tabeli z kontami musi od razu miec wartosc.
     */

    /** Kto widzi szczegoly profilu. */
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.ColumnDefault("'EVERYONE'")
    @Column(name = "profile_visibility", nullable = false, length = 20)
    private ProfileVisibility profileVisibility = ProfileVisibility.EVERYONE;

    /** Kto moze wyslac zaproszenie do znajomych. */
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.ColumnDefault("'EVERYONE'")
    @Column(name = "friend_requests_from", nullable = false, length = 20)
    private InvitePolicy friendRequestsFrom = InvitePolicy.EVERYONE;

    /** Kto moze zaprosic mnie do klanu. */
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.ColumnDefault("'EVERYONE'")
    @Column(name = "clan_invites_from", nullable = false, length = 20)
    private ClanInvitePolicy clanInvitesFrom = ClanInvitePolicy.EVERYONE;

    /** Czy inni widza, ze jestem teraz aktywny i kiedy bylem ostatnio. */
    @org.hibernate.annotations.ColumnDefault("true")
    @Column(name = "show_online", nullable = false)
    private boolean showOnline = true;

    /** Czy pojawiam sie w propozycjach znajomych u innych. */
    @org.hibernate.annotations.ColumnDefault("true")
    @Column(name = "show_in_suggestions", nullable = false)
    private boolean showInSuggestions = true;

    /** Czy nowy zapis na wydarzenie ma od razu "nie pokazuj mnie na liscie uczestnikow". */
    @org.hibernate.annotations.ColumnDefault("false")
    @Column(name = "hide_on_attendee_lists", nullable = false)
    private boolean hideOnAttendeeLists = false;

    /** Czy przypominac o wydarzeniach, na ktore jestem zapisany (dzwonek i push). */
    @org.hibernate.annotations.ColumnDefault("true")
    @Column(name = "event_reminders", nullable = false)
    private boolean eventReminders = true;

    /** Wersja regulaminu i polityki prywatnosci, ktora ta osoba zaakceptowala (null = konto sprzed regulaminu). */
    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    /** Kraj, z ktorego pokazujemy wydarzenia. Pusty = Polska. */
    @Column(name = "events_country", length = 2)
    private String eventsCountry;

    private static final java.security.SecureRandom LOSOWANIE = new java.security.SecureRandom();

    /** Termin oznaczajacy zakaz bezterminowy. */
    public static final LocalDateTime FOREVER = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    /** Czy podany termin oznacza zakaz bezterminowy. */
    public static boolean isForever(LocalDateTime until) {
        return until != null && !until.isBefore(FOREVER);
    }

    /**
     * Do kiedy obowiazuje zakaz publikowania nalozony przez administratora. null znaczy "bez
     * zakazu".
     */
    @Column(name = "posting_banned_until")
    private LocalDateTime postingBannedUntil;

    /** Do kiedy obowiazuje zakaz WYSYLANIA WIADOMOSCI. */
    @Column(name = "messaging_banned_until")
    private LocalDateTime messagingBannedUntil;

    /** Kiedy ta osoba ostatnio cokolwiek w aplikacji zrobila. */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /**
     * Nazwa pliku ze zdjeciem profilowym, np. a1b2...ff.jpg. null oznacza brak zdjecia - interfejs
     * pokazuje wtedy kolo z pierwsza litera loginu.
     */
    @Column(name = "avatar_file_name", length = 120)
    private String avatarFileName;

    /** Znajomi - wymaganie nr 7 (ManyToMany). */
    @ManyToMany
    @JoinTable(
        name = "user_friends",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "friend_id"))
    private Set<User> friends = new HashSet<>();

    /** Ulubieni wykonawcy - serce dopasowywania ludzi po guscie. */
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
     * Metoda oznaczona @PrePersist uruchamia sie automatycznie tuz przed pierwszym zapisem encji
     * do bazy.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (securityStamp == null) {
            rotateSecurityStamp();
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

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public LocalDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void markEmailVerified(LocalDateTime when) {
        this.emailVerifiedAt = when;
    }

    public String getPendingEmail() {
        return pendingEmail;
    }

    public LocalDateTime getPendingEmailOldApprovedAt() {
        return pendingEmailOldApprovedAt;
    }

    public LocalDateTime getPendingEmailNewVerifiedAt() {
        return pendingEmailNewVerifiedAt;
    }

    /** Nowa zmiana adresu - obie zgody od zera. */
    public void startEmailChange(String newEmail) {
        this.pendingEmail = newEmail;
        this.pendingEmailOldApprovedAt = null;
        this.pendingEmailNewVerifiedAt = null;
    }

    public void approveEmailChangeFromOld(LocalDateTime when) {
        this.pendingEmailOldApprovedAt = when;
    }

    public void verifyPendingEmail(LocalDateTime when) {
        this.pendingEmailNewVerifiedAt = when;
    }

    /** Czy nowy adres ma juz obie zgody. */
    public boolean emailChangeComplete() {
        return pendingEmail != null && pendingEmailOldApprovedAt != null && pendingEmailNewVerifiedAt != null;
    }

    /** Zmiana anulowana albo dokonczona - bez sladu. */
    public void clearEmailChange() {
        startEmailChange(null);
    }

    public String getSecurityStamp() {
        return securityStamp;
    }

    /** Nowy znacznik - wszystkie inne sesje i ciasteczka "zapamietaj mnie" przestaja dzialac. */
    public void rotateSecurityStamp() {
        byte[] bajty = new byte[24];
        LOSOWANIE.nextBytes(bajty);
        this.securityStamp = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bajty);
    }

    public ProfileVisibility getProfileVisibility() {
        return profileVisibility;
    }

    public InvitePolicy getFriendRequestsFrom() {
        return friendRequestsFrom;
    }

    public boolean isShowOnline() {
        return showOnline;
    }

    public boolean isShowInSuggestions() {
        return showInSuggestions;
    }

    public boolean isHideOnAttendeeLists() {
        return hideOnAttendeeLists;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public LocalDateTime getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void acceptTerms(String version, LocalDateTime now) {
        this.termsVersion = version;
        this.termsAcceptedAt = now;
    }

    public boolean isEventReminders() {
        return eventReminders;
    }

    public void setEventReminders(boolean eventReminders) {
        this.eventReminders = eventReminders;
    }

    public ClanInvitePolicy getClanInvitesFrom() {
        return clanInvitesFrom == null ? ClanInvitePolicy.EVERYONE : clanInvitesFrom;
    }

    /** Wszystkie ustawienia prywatnosci naraz - formularz zapisuje je razem. */
    public void setPrivacy(ProfileVisibility profileVisibility, InvitePolicy friendRequestsFrom,
                           ClanInvitePolicy clanInvitesFrom,
                           boolean showOnline, boolean showInSuggestions, boolean hideOnAttendeeLists) {
        this.clanInvitesFrom = clanInvitesFrom;
        this.profileVisibility = profileVisibility;
        this.friendRequestsFrom = friendRequestsFrom;
        this.showOnline = showOnline;
        this.showInSuggestions = showInSuggestions;
        this.hideOnAttendeeLists = hideOnAttendeeLists;
    }

    public String getEventsCountry() {
        return eventsCountry;
    }

    public void setEventsCountry(String eventsCountry) {
        this.eventsCountry = eventsCountry;
    }

    public LocalDateTime getMessagingBannedUntil() {
        return messagingBannedUntil;
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

    /* ------------------------------------------------------------------ */
    /*  Kary - jedno miejsce na obie                                       */
    /* ------------------------------------------------------------------ */

    /*
     * Ponizsze trzy metody sa JEDYNYM miejscem w calej aplikacji, ktore wie, ktora kolumna
     * odpowiada ktorej karze.
     */

    /** Do kiedy obowiazuje kara danego rodzaju (null = bez kary). */
    public LocalDateTime bannedUntil(BanKind kind) {
        return kind == BanKind.POSTING ? postingBannedUntil : messagingBannedUntil;
    }

    /** Ustawia termin konca kary; {@code null} ja zdejmuje. */
    public void setBannedUntil(BanKind kind, LocalDateTime until) {
        if (kind == BanKind.POSTING) {
            this.postingBannedUntil = until;
        } else {
            this.messagingBannedUntil = until;
        }
    }

    /** Czy kara tego rodzaju obowiazuje teraz. */
    public boolean isBanned(BanKind kind) {
        LocalDateTime until = bannedUntil(kind);
        return until != null && until.isAfter(LocalDateTime.now());
    }

    public Set<User> getFriends() {
        return friends;
    }

    /** Dodaje znajomego po OBU stronach relacji. */
    public void addFriend(User other) {
        this.friends.add(other);
        other.getFriends().add(this);
    }

    /** Usuwa znajomosc po obu stronach - z ta sama uwaga o proxy co wyzej. */
    public void removeFriend(User other) {
        this.friends.remove(other);
        other.getFriends().remove(this);
    }

    /* Ulubione sa relacja JEDNOSTRONNA - inaczej niz znajomosc. */

    public Set<Artist> getFavoriteArtists() {
        return favoriteArtists;
    }

    public Set<Track> getFavoriteTracks() {
        return favoriteTracks;
    }

    /** equals/hashCode po ID. */
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
