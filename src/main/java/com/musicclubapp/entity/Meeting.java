package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Spotkanie wyslane na czacie: miejsce (tekst i opcjonalnie punkt na mapie), czas od-do i przypomnienie dla osob,
 * ktore potwierdzily. Nalezy do rozmowy dwoch osob ({@code partner}), do czatu klanu ({@code clan}) albo do czatu
 * ekipy na koncert ({@code crew}) - dokladnie jednego z nich. Wiadomosc, ktora je niesie, wskazuje na nie kolumna
 * {@code meeting_id}.
 *
 * <p>Czas to chwile (UTC): przegladarka wysyla czas lokalny zamieniony na UTC, a przypomnienie liczy sie od chwili,
 * nie od godziny na zegarze serwera.</p>
 */
@Entity
@Table(name = "meetings", indexes = {
    @Index(name = "idx_meetings_remind", columnList = "remind_at"),
    @Index(name = "idx_meetings_clan", columnList = "clan_id, updated_at"),
    @Index(name = "idx_meetings_crew", columnList = "crew_id, updated_at"),
    @Index(name = "idx_meetings_creator", columnList = "creator_id, ends_at")
})
public class Meeting {

    public static final int MAX_PLACE = 100;
    public static final int MAX_NOTE = 200;
    /** Spotkanie trwa najwyzej dobe i zaczyna sie najpozniej za 60 dni. */
    public static final Duration MAX_DURATION = Duration.ofHours(24);
    public static final Duration MAX_AHEAD = Duration.ofDays(60);
    /** Ile wolno "w przeszlosc" (zegar telefonu i chwila na wyslanie). */
    public static final Duration PAST_TOLERANCE = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    /** Rozmowa: druga osoba. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private User partner;

    /** Czat klanu: ten klan. Rozwiazanie klanu kasuje spotkania razem z nim. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clan_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Clan clan;

    /** Czat ekipy: ta ekipa (miejsce zbiorki). Znika razem z ekipa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crew_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Crew crew;

    @Column(nullable = false, length = MAX_PLACE)
    private String place;

    @Column(length = MAX_NOTE)
    private String note;

    private Double latitude;

    private Double longitude;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    /** 0 = bez przypomnienia. */
    @Column(name = "remind_minutes", nullable = false)
    private int remindMinutes;

    /** Kiedy przypomniec (start minus {@code remindMinutes}); null = bez przypomnienia. */
    @Column(name = "remind_at")
    private Instant remindAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Ostatnia zmiana (odpowiedz, odwolanie) - po niej otwarte czaty dociagaja nowy stan karty. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Meeting() {
        // wymagany przez JPA
    }

    public Meeting(User creator, User partner, Clan clan, String place, String note, Double latitude, Double longitude,
                   Instant startsAt, Instant endsAt, int remindMinutes, Instant now) {
        this.creator = creator;
        this.partner = partner;
        this.clan = clan;
        this.place = place;
        this.note = note;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.remindMinutes = remindMinutes;
        this.remindAt = remindMinutes > 0 ? startsAt.minus(Duration.ofMinutes(remindMinutes)) : null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Spotkanie z czatu ekipy - ustawiane przed zapisem, zamiast rozmowy i klanu. */
    public void inCrew(Crew crew) {
        this.crew = crew;
    }

    public void cancel(Instant now) {
        if (cancelledAt == null) {
            cancelledAt = now;
            updatedAt = now;
        }
    }

    public void touch(Instant now) {
        updatedAt = now;
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    /** Czy jeszcze mozna odpowiadac i odwolywac: nie odwolane i jeszcze sie nie skonczylo. */
    public boolean isOpen(Instant now) {
        return cancelledAt == null && now.isBefore(endsAt);
    }

    /** Czy osoba nalezy do tej rozmowy (zakladajacy albo druga strona) - przy spotkaniu klanu nic nie mowi. */
    public boolean isBetween(Long userId) {
        return creator.getId().equals(userId) || (partner != null && partner.getId().equals(userId));
    }

    public Long getId() {
        return id;
    }

    public User getCreator() {
        return creator;
    }

    public User getPartner() {
        return partner;
    }

    public Clan getClan() {
        return clan;
    }

    public Crew getCrew() {
        return crew;
    }

    public String getPlace() {
        return place;
    }

    public String getNote() {
        return note;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getRemindMinutes() {
        return remindMinutes;
    }

    public Instant getRemindAt() {
        return remindAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Meeting m && id != null && id.equals(m.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
