package com.musicclubapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Klan: grupa osob z wlasnym czatem i wlasnymi postami, ktorych nie widzi nikt spoza klanu
 * (poza administratorem aplikacji - patrz docs i polityka prywatnosci).
 *
 * <p>Nazwa i skrot sa unikalne bez wzgledu na wielkosc liter i polskie znaki. Kolor nie jest
 * wybierany przez zalozyciela - wynika z glosow czlonkow ({@link ClanMember#getColorVote()}),
 * a tu leci tylko jego wynik, zeby plakietki nie liczyly glosow przy kazdym poscie.</p>
 */
@Entity
@Table(name = "clans", uniqueConstraints = {
    @UniqueConstraint(name = "uk_clans_name_key", columnNames = "name_key"),
    @UniqueConstraint(name = "uk_clans_tag", columnNames = "tag")
})
public class Clan {

    public static final int NAME_MIN = 3;
    public static final int NAME_MAX = 32;
    public static final int TAG_MIN = 2;
    public static final int TAG_MAX = 5;
    public static final int DESCRIPTION_MAX = 300;
    public static final int ANNOUNCEMENT_MAX = 500;
    public static final int RULES_MAX = 600;

    /** Tylu czlonkow najwyzej. */
    public static final int MAX_MEMBERS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX)
    private String name;

    /** Nazwa sprowadzona do malych liter bez znakow diakrytycznych - do sprawdzania unikalnosci. */
    @Column(name = "name_key", nullable = false, length = 64)
    private String nameKey;

    /** Skrot z plakietki, wielkimi literami: [MC]. */
    @Column(nullable = false, length = TAG_MAX)
    private String tag;

    @Column(length = DESCRIPTION_MAX)
    private String description;

    /** Ikona: maly obrazek widoczny na plakietkach. */
    @Column(name = "icon_file_name", length = 100)
    private String iconFileName;

    /** Zdjecie klanu: duzy obrazek u gory strony klanu. */
    @Column(name = "photo_file_name", length = 100)
    private String photoFileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClanColor color = ClanColor.DEFAULT;

    /** Przypiete ogloszenie zarzadu - u gory strony klanu, widoczne dla czlonkow. */
    @Column(length = ANNOUNCEMENT_MAX)
    private String announcement;

    @Column(name = "announcement_at")
    private LocalDateTime announcementAt;

    /** Krotkie zasady klanu. Widza je czlonkowie i - przed przyjeciem - osoby zaproszone. */
    @Column(length = RULES_MAX)
    private String rules;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "clan")
    @OrderBy("joinedAt ASC, id ASC")
    private List<ClanMember> members = new ArrayList<>();

    protected Clan() {
        // wymagany przez JPA
    }

    public Clan(String name, String nameKey, String tag, String description) {
        this.name = name;
        this.nameKey = nameKey;
        this.tag = tag;
        this.description = description;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** Czy osoba o tym numerze jest czlonkiem. */
    public boolean hasMember(Long userId) {
        return userId != null && members.stream().anyMatch(m -> m.getUser().getId().equals(userId));
    }

    /** Rola tej osoby w klanie albo null, gdy nie jest czlonkiem. */
    public ClanRole roleOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return members.stream()
            .filter(m -> m.getUser().getId().equals(userId))
            .map(ClanMember::getRole)
            .findFirst().orElse(null);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public void rename(String name, String nameKey, String tag) {
        this.name = name;
        this.nameKey = nameKey;
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAnnouncement() {
        return announcement;
    }

    public LocalDateTime getAnnouncementAt() {
        return announcementAt;
    }

    /** Puste ogloszenie = brak ogloszenia; czas zapisuje tylko prawdziwa zmiana tresci. */
    public void setAnnouncement(String announcement, LocalDateTime now) {
        if (java.util.Objects.equals(this.announcement, announcement)) {
            return;
        }
        this.announcement = announcement;
        this.announcementAt = announcement == null ? null : now;
    }

    public String getRules() {
        return rules;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }

    public String getIconFileName() {
        return iconFileName;
    }

    public void setIconFileName(String iconFileName) {
        this.iconFileName = iconFileName;
    }

    public String getPhotoFileName() {
        return photoFileName;
    }

    public void setPhotoFileName(String photoFileName) {
        this.photoFileName = photoFileName;
    }

    public ClanColor getColor() {
        return color == null ? ClanColor.DEFAULT : color;
    }

    public void setColor(ClanColor color) {
        this.color = color;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<ClanMember> getMembers() {
        return members;
    }
}
