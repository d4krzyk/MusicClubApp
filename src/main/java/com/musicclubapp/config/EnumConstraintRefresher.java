package com.musicclubapp.config;

import com.musicclubapp.entity.ClanColor;
import com.musicclubapp.entity.ClanInvitePolicy;
import com.musicclubapp.entity.ClanRole;
import com.musicclubapp.entity.EventStatus;
import com.musicclubapp.entity.InvitationStatus;
import com.musicclubapp.entity.NotificationType;
import com.musicclubapp.entity.ParticipationStatus;
import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.ReportContext;
import com.musicclubapp.entity.ReportReason;
import com.musicclubapp.entity.ReportStatus;
import com.musicclubapp.entity.InvitePolicy;
import com.musicclubapp.entity.ProfileVisibility;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.TokenPurpose;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicProvider;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Uzgadnia bazodanowe ograniczenia CHECK z aktualna trescia wyliczen (enumow). */
@Component
public class EnumConstraintRefresher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumConstraintRefresher.class);

    /** Kolumna trzymajaca nazwy stalych wyliczenia. */
    private record EnumColumn(String table, String column, Class<? extends Enum<?>> type) { }

    /*
     * Lista obejmuje KAZDA kolumne wyliczeniowa w bazie - i to jest zmiana wzgledem pierwszej
     * wersji.
     */
    private static final List<EnumColumn> COLUMNS = List.of(
        new EnumColumn("posts", "music_kind", MusicKind.class),
        new EnumColumn("posts", "music_provider", MusicProvider.class),
        /*
         * Widocznosc dopisujemy tu od razu, choc kolumna dopiero powstaje - czyli dzis
         * ograniczenie i tak jest poprawne.
         */
        new EnumColumn("posts", "visibility", PostVisibility.class),

        /*
         * Wiadomosci na czacie maja te same dwie kolumny co posty, bo moga niesc to samo nagranie.
         */
        new EnumColumn("messages", "music_kind", MusicKind.class),
        new EnumColumn("messages", "music_provider", MusicProvider.class),

        /*
         * Zgloszenia maja az trzy kolumny wyliczeniowe, a lista powodow jest najbardziej
         * prawdopodobna do rozszerzenia w calej aplikacji - wystarczy, ze pojawi sie rodzaj
         * naruszenia, ktorego dzis nie ma na liscie.
         */
        new EnumColumn("reports", "reason", ReportReason.class),
        new EnumColumn("reports", "context", ReportContext.class),
        new EnumColumn("reports", "status", ReportStatus.class),

        /* TA kolumna jest powodem, dla ktorego lista przestala byc wybiorcza. */
        new EnumColumn("notifications", "type", NotificationType.class),
        new EnumColumn("notifications", "reaction_type", ReactionType.class),

        new EnumColumn("reactions", "type", ReactionType.class),
        new EnumColumn("favorite_playlists", "provider", MusicProvider.class),
        new EnumColumn("users", "role", Role.class),
        new EnumColumn("users", "profile_visibility", ProfileVisibility.class),
        new EnumColumn("users", "friend_requests_from", InvitePolicy.class),

        /* Ticketmaster moze kiedys wprowadzic nowy stan wydarzenia - wtedy przybedzie stala. */
        new EnumColumn("music_events", "status", EventStatus.class),
        new EnumColumn("event_participations", "status", ParticipationStatus.class),
        new EnumColumn("email_tokens", "purpose", TokenPurpose.class),
        new EnumColumn("users", "clan_invites_from", ClanInvitePolicy.class),
        new EnumColumn("clans", "color", ClanColor.class),
        new EnumColumn("clan_members", "role", ClanRole.class),
        new EnumColumn("clan_members", "color_vote", ClanColor.class),
        new EnumColumn("clan_invitations", "status", InvitationStatus.class)
    );

    /** Kolumny objete odswiezaniem, jako "tabela.kolumna". */
    static Set<String> coveredColumns() {
        return COLUMNS.stream()
            .map(column -> column.table() + "." + column.column())
            .collect(Collectors.toSet());
    }

    private final EntityManager entityManager;

    public EnumConstraintRefresher(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /*
     * Kolumny, ktore kiedys byly NOT NULL, a teraz moga byc puste. ddl-auto=update
     * dodaje nowe kolumny, ale nigdy nie zmienia istniejacych - bez tego baza
     * z pracy lokalnej odrzucalaby przypomnienia (nie maja sprawcy). Na
     * produkcji robi to migracja; tam to polecenie niczego juz nie zmienia.
     */
    private static final List<String> NULLABLE = List.of("notifications.actor_id");

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (EnumColumn column : COLUMNS) {
            refresh(column);
        }
        for (String kolumna : NULLABLE) {
            String[] czesci = kolumna.split("\\.");
            try {
                entityManager.createNativeQuery(
                    "ALTER TABLE " + czesci[0] + " ALTER COLUMN " + czesci[1] + " DROP NOT NULL"
                ).executeUpdate();
            } catch (Exception e) {
                log.warn("Nie udalo sie zdjac NOT NULL z {} ({}) - wykonaj recznie: "
                    + "ALTER TABLE {} ALTER COLUMN {} DROP NOT NULL", kolumna, e.getMessage(), czesci[0], czesci[1]);
            }
        }
    }

    private void refresh(EnumColumn column) {
        // Nazwa, ktora nadaje Hibernate: <tabela>_<kolumna>_check
        String name = column.table() + "_" + column.column() + "_check";

        /*
         * Wartosci pochodza z klasy enuma, a nie od uzytkownika - to nazwy stalych w Javie, wiec
         * moga zawierac wylacznie litery, cyfry i podkreslenie.
         */
        String list = Arrays.stream(column.type().getEnumConstants())
            .map(constant -> "'" + constant.name() + "'")
            .collect(Collectors.joining(", "));

        try {
            entityManager.createNativeQuery(
                "ALTER TABLE " + column.table() + " DROP CONSTRAINT IF EXISTS " + name
            ).executeUpdate();

            entityManager.createNativeQuery(
                "ALTER TABLE " + column.table() + " ADD CONSTRAINT " + name
                    + " CHECK (" + column.column() + " IN (" + list + "))"
            ).executeUpdate();

            log.debug("Ograniczenie {} obejmuje teraz wartosci: {}", name, list);
        } catch (Exception e) {
            log.warn("Nie udalo sie odswiezyc ograniczenia {} - aplikacja startuje dalej. "
                + "Jesli dodawanie nowych rodzajow nagran konczy sie bledem 500, "
                + "wykonaj recznie: ALTER TABLE {} DROP CONSTRAINT {}; ({})",
                name, column.table(), name, e.getMessage());
        }
    }
}
