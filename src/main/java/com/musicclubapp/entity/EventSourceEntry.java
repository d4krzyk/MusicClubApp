package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * Wydarzenie widziane w zrodle pobocznym (Bandsintown, Songkick): do ktorego naszego wydarzenia nalezy i co o nim
 * mowi. Trzymamy te dane osobno, zeby po kazdym imporcie Ticketmastera (ktory nadpisuje wydarzenie swoimi danymi)
 * znow uzupelnic braki - inaczej godzina dopisana z Bandsintown znikalaby co 6 godzin i wracala raz na dobe.
 */
@Entity
@Table(name = "event_sources",
    uniqueConstraints = @UniqueConstraint(name = "uk_event_source", columnNames = {"source", "external_id"}),
    indexes = @Index(name = "idx_event_sources_event", columnList = "event_id"))
public class EventSourceEntry {

    public static final int MAX_DESCRIPTION = 1000;
    public static final int MAX_PERFORMERS = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MusicEvent event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EventSource source;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    /** Strona wydarzenia w tym serwisie - do podpisu z odnosnikiem. */
    @Column(length = 1000)
    private String url;

    @Column(name = "seen_at", nullable = false)
    private LocalDateTime seenAt;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "venue_name", length = 200)
    private String venueName;

    @Column(length = 300)
    private String address;

    private Double latitude;

    private Double longitude;

    @Column(name = "ticket_url", length = 1000)
    private String ticketUrl;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(length = MAX_DESCRIPTION)
    private String description;

    /** Sklad, po jednym wykonawcy w wierszu. */
    @Column(length = MAX_PERFORMERS)
    private String performers;

    protected EventSourceEntry() {
        // wymagany przez JPA
    }

    public EventSourceEntry(MusicEvent event, EventSource source, String externalId) {
        this.event = event;
        this.source = source;
        this.externalId = externalId;
    }

    /** Co zrodlo mowi teraz - za kazdym razem od nowa (puste = nie mowi). */
    public void update(String url, LocalTime startTime, String venueName, String address, Double latitude,
                       Double longitude, String ticketUrl, String imageUrl, String description, List<String> performers,
                       LocalDateTime now) {
        this.url = url;
        this.startTime = startTime;
        this.venueName = venueName;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.ticketUrl = ticketUrl;
        this.imageUrl = imageUrl;
        this.description = description;
        String sklad = performers == null || performers.isEmpty() ? null : String.join("\n", performers);
        this.performers = sklad == null || sklad.length() <= MAX_PERFORMERS ? sklad : null;
        this.seenAt = now;
    }

    /** Przeniesienie do innego wydarzenia - gdy dwa nasze okazaly sie jednym. */
    public void moveTo(MusicEvent event) {
        this.event = event;
    }

    public Long getId() {
        return id;
    }

    public MusicEvent getEvent() {
        return event;
    }

    public EventSource getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getUrl() {
        return url;
    }

    public LocalDateTime getSeenAt() {
        return seenAt;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public String getVenueName() {
        return venueName;
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

    public String getTicketUrl() {
        return ticketUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getPerformers() {
        return performers == null ? List.of() : Arrays.asList(performers.split("\n"));
    }
}
