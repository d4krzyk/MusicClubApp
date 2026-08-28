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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Zgloszenie uzytkownika do administratora.
 *
 * <p><b>Czego dotyczy, mowi {@link #context}</b> - profil, konkretny post
 * albo rozmowa. To rozroznienie nie jest ozdoba: przy poscie administrator
 * potrzebuje odnosnika, przy rozmowie - migawki tresci, a przy profilu ani
 * jednego, ani drugiego. Jedno pole "opis" na wszystkie trzy przypadki
 * zmuszaloby zglaszajacego do wklejania linkow recznie.</p>
 *
 * <p><b>Realizuje kolejna relacje {@code ManyToOne}</b> - i to trzy naraz:
 * zglaszajacy, zglaszany i (opcjonalnie) post. Dochodzi do tego kolekcja
 * elementow ({@code @ElementCollection}) z migawka dowodow.</p>
 */
@Entity
@Table(
    name = "reports",
    indexes = {
        /*
         * Panel otwiera sie zawsze na tym samym pytaniu: "co czeka na moja
         * decyzje, od najnowszego". Bez indeksu baza przegladalaby przy kazdym
         * wejsciu cala tabele - a ta nie jest kasowana, tylko rosnie.
         */
        @Index(name = "idx_reports_status", columnList = "status, created_at"),
        // Drugie pytanie: "ile razy ta osoba byla juz zglaszana"
        @Index(name = "idx_reports_reported", columnList = "reported_id")
    })
public class Report {

    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    /** Ile ostatnich wiadomosci zapisujemy jako dowod przy zgloszeniu rozmowy. */
    public static final int EVIDENCE_LIMIT = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kto zglosil. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    /** Kogo zgloszono. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_id", nullable = false)
    private User reported;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportContext context = ReportContext.PROFILE;

    /**
     * Post, ktorego dotyczy zgloszenie - tylko przy {@link ReportContext#POST}.
     *
     * <p>Prawdziwy klucz obcy, tak samo jak przy powiadomieniach: baza pilnuje,
     * ze zgloszenie nie wskaze posta, ktorego juz nie ma.</p>
     *
     * <p><b>Uwaga na skutek uboczny:</b> skasowanie posta zabiera zgloszeniu
     * odnosnik. Dlatego przy zgloszeniu posta zapisujemy jego tresc TAKZE
     * jako dowod ({@link #evidence}) - inaczej administrator, ktory najpierw
     * skasowal post, a potem otworzyl zgloszenie, nie wiedzialby juz, o co
     * chodzilo.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    /** Co zglaszajacy napisal od siebie. */
    @Column(length = MAX_DESCRIPTION_LENGTH, columnDefinition = "TEXT")
    private String description;

    /**
     * Migawka dowodow - patrz {@link ReportEvidence}.
     *
     * <p>{@code FetchType.LAZY}, bo lista zgloszen w panelu pokazuje same
     * naglowki. Dowody sa potrzebne dopiero po otwarciu jednego zgloszenia -
     * przy dwudziestu wierszach na stronie pobieranie ich od razu oznaczaloby
     * czterysta linijek tekstu na jedno wejscie.</p>
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "report_evidence",
        joinColumns = @JoinColumn(name = "report_id"))
    @OrderColumn(name = "position")
    private List<ReportEvidence> evidence = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** Login administratora, ktory zamknal - nie klucz obcy, jak przy blokadzie IP. */
    @Column(name = "resolved_by", length = 50)
    private String resolvedBy;

    /**
     * Co administrator zrobil - albo dlaczego uznal zgloszenie za bezpodstawne.
     *
     * <p>Pole obowiazkowe przy zamykaniu. Zgloszenie zamkniete bez slowa
     * wyjasnienia jest dla nastepnej osoby patrzacej na historie tego konta
     * bezuzyteczne: widac, ze cos bylo, ale nie wiadomo co.</p>
     */
    @Column(name = "resolution_note", length = MAX_DESCRIPTION_LENGTH, columnDefinition = "TEXT")
    private String resolutionNote;

    protected Report() {
    }

    public Report(User reporter, User reported, ReportReason reason,
                  ReportContext context, String description) {
        this.reporter = reporter;
        this.reported = reported;
        this.reason = reason;
        this.context = context;
        this.description = description;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ReportStatus.OPEN;
        }
        if (context == null) {
            context = ReportContext.PROFILE;
        }
    }

    /**
     * Zamyka zgloszenie.
     *
     * <p>Powtorne zamkniecie nie zmienia juz niczego - pierwsza decyzja jest
     * ta wiazaca. Bez tego dwoch administratorow klikajacych jednoczesnie
     * nadpisaloby sobie nawzajem notatki, a data zamkniecia przesunelaby sie
     * na pozniejsza.</p>
     *
     * @return czy cokolwiek sie zmienilo
     */
    public boolean close(ReportStatus decision, String adminUsername, String note) {
        if (status != ReportStatus.OPEN) {
            return false;
        }
        this.status = decision;
        this.resolvedBy = adminUsername;
        this.resolutionNote = note;
        this.resolvedAt = LocalDateTime.now();
        return true;
    }

    public void addEvidence(ReportEvidence line) {
        evidence.add(line);
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public Long getId() {
        return id;
    }

    public User getReporter() {
        return reporter;
    }

    public User getReported() {
        return reported;
    }

    public ReportReason getReason() {
        return reason;
    }

    public ReportContext getContext() {
        return context;
    }

    public Post getPost() {
        return post;
    }

    public String getDescription() {
        return description;
    }

    public List<ReportEvidence> getEvidence() {
        return evidence;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }
}
