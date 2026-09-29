package com.musicclubapp.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Koncert albo inne wydarzenie muzyczne pobrane z Ticketmastera.
 *
 * Trzymamy je u siebie, a nie pytamy Ticketmastera przy kazdym wejsciu na
 * zakladke. Po pierwsze limit: 5000 zapytan dziennie na cala aplikacje
 * starczylby na kilkaset wejsc. Po drugie - do wydarzenia beda sie odwolywac
 * nasze wlasne dane (kto jest zainteresowany, kto idzie), a do tego potrzebny
 * jest wiersz w naszej bazie.
 */
@Entity
@Table(
    name = "music_events",
    indexes = {
        /* Lista zawsze zaczyna od "od dzis w gore". */
        @Index(name = "idx_music_events_start", columnList = "start_date"),
        /* ...i zawsze w jednym kraju. */
        @Index(name = "idx_music_events_country_start", columnList = "country_code, start_date"),
        /* Kolejne terminy tego samego wydarzenia - na liscie i na stronie wydarzenia. */
        @Index(name = "idx_music_events_series", columnList = "series_key, start_date")
    })
public class MusicEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identyfikator u Ticketmastera - po nim poznajemy przy imporcie, co juz mamy. */
    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    @Column(nullable = false, length = 300)
    private String name;

    /**
     * Co laczy kolejne terminy tego samego wydarzenia: nazwa i miejsce.
     *
     * Bez tego "Koncert przy swiecach" w tej samej sali, grany co wieczor,
     * zajmowal szesc z pierwszych dwudziestu pozycji listy. Z tym kluczem
     * lista pokazuje go raz, z dopiskiem, ile ma jeszcze terminow.
     */
    @Column(name = "series_key", nullable = false, length = 400)
    private String seriesKey;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Godzina moze byc jeszcze nieznana ("TBA") - wtedy pusta. */
    @Column(name = "start_time")
    private LocalTime startTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EventStatus status = EventStatus.SCHEDULED;

    /** Opis od organizatora. Bywa dlugi, bywa go brak. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Strona wydarzenia u Ticketmastera - tam sa bilety. */
    @Column(name = "ticket_url", length = 1000)
    private String ticketUrl;

    /** Duze zdjecie na strone wydarzenia. */
    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    /**
     * Mniejsze zdjecie na liste. Osobno, bo lista pokazuje dziesiatki kart,
     * a przy danych komorkowych roznica miedzy 1024 a 640 pikseli szerokosci
     * mnozy sie przez kazda karte.
     */
    @Column(name = "thumb_url", length = 1000)
    private String thumbUrl;

    @Column(name = "venue_external_id", length = 64)
    private String venueExternalId;

    @Column(name = "venue_name", length = 200)
    private String venueName;

    /**
     * Kraj wydarzenia (kod ISO, np. "PL", "DE"). Lista pokazuje wydarzenia
     * z kraju wybranego na koncie.
     */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /** Miasto tak, jak podal je Ticketmaster (zwykle po angielsku: "Warsaw", "Krakow"). */
    @Column(length = 100)
    private String city;

    /**
     * Miasto sprowadzone do jednej postaci: male litery, bez polskich znakow.
     * Po nim dziala filtr - "Łódź " i "Lodz" to ma byc jedno miasto.
     */
    @Column(name = "city_key", length = 100)
    private String cityKey;

    @Column(length = 300)
    private String address;

    private Double latitude;

    private Double longitude;

    /** Gatunek wedlug Ticketmastera, np. "Rock". Bardzo ogolny - to podpowiedz, nie etykieta. */
    @Column(length = 60)
    private String genre;

    @Column(name = "sub_genre", length = 60)
    private String subGenre;

    /**
     * Kiedy ostatni import widzial to wydarzenie. Czego pelny import juz nie
     * zobaczyl, tego Ticketmaster nie sprzedaje - i to znika tez u nas.
     */
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    /**
     * Kiedy wydarzenie zniknelo z Ticketmastera, choc ktos byl na nie zapisany.
     *
     * Takiego wydarzenia nie kasujemy - razem z nim zniknalby slad, ze ktos
     * sie na nie wybieral, i nikt by sie nie dowiedzial, co sie stalo. Znika
     * tylko z listy, a zapisani widza na jego stronie, ze Ticketmaster go juz
     * nie ma. Wydarzenie bez zapisanych jest po prostu usuwane.
     */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    /** Sklad w kolejnosci z plakatu: najpierw gwiazda, potem support. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "music_event_performers", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "performer_order")
    private List<EventPerformer> performers = new ArrayList<>();

    protected MusicEvent() {
        // wymagany przez JPA
    }

    public MusicEvent(String externalId) {
        this.externalId = externalId;
    }

    /* --- Uzupelnianie przy imporcie. Kazda metoda to jedna grupa pol. --- */

    public void describe(String name, String description, EventStatus status,
                         String genre, String subGenre) {
        this.name = name;
        this.description = description;
        this.status = status == null ? EventStatus.SCHEDULED : status;
        this.genre = genre;
        this.subGenre = subGenre;
    }

    public void schedule(LocalDate startDate, LocalTime startTime) {
        this.startDate = startDate;
        this.startTime = startTime;
    }

    public void place(String venueExternalId, String venueName, String city, String cityKey,
                      String address, Double latitude, Double longitude) {
        this.venueExternalId = venueExternalId;
        this.venueName = venueName;
        this.city = city;
        this.cityKey = cityKey;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void link(String ticketUrl, String imageUrl, String thumbUrl) {
        this.ticketUrl = ticketUrl;
        this.imageUrl = imageUrl;
        this.thumbUrl = thumbUrl;
    }

    public void inCountry(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void groupAs(String seriesKey) {
        this.seriesKey = seriesKey;
    }

    /**
     * Podmienia sklad, ale tylko gdy faktycznie sie zmienil.
     *
     * Kolekcja @ElementCollection nie ma wlasnych identyfikatorow, wiec kazda
     * jej zmiana to "usun wszystkie wiersze i wstaw od nowa". Przy kilkuset
     * wydarzeniach co kilka godzin to kilka tysiecy niepotrzebnych zapisow,
     * skoro sklad prawie nigdy sie nie zmienia.
     */
    public void replacePerformers(List<EventPerformer> updated) {
        if (!performers.equals(updated)) {
            performers.clear();
            performers.addAll(updated);
        }
    }

    /** Import znow je widzi - jesli bylo wycofane, wraca na liste. */
    public void markSeen(LocalDateTime when) {
        this.lastSeenAt = when;
        this.withdrawnAt = null;
    }

    public void withdraw(LocalDateTime when) {
        if (withdrawnAt == null) {
            withdrawnAt = when;
        }
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    public LocalDateTime getWithdrawnAt() {
        return withdrawnAt;
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public String getSeriesKey() {
        return seriesKey;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getTicketUrl() {
        return ticketUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getThumbUrl() {
        return thumbUrl;
    }

    public String getVenueExternalId() {
        return venueExternalId;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getCity() {
        return city;
    }

    public String getCityKey() {
        return cityKey;
    }

    public String getAddress() {
        return address;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getGenre() {
        return genre;
    }

    public String getSubGenre() {
        return subGenre;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public List<EventPerformer> getPerformers() {
        return performers;
    }
}
