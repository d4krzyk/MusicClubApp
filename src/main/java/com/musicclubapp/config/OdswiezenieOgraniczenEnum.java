package com.musicclubapp.config;

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
import java.util.stream.Collectors;

/**
 * Uzgadnia bazodanowe ograniczenia CHECK z aktualna trescia wyliczen (enumow).
 *
 * <p><b>Blad, ktory to wymusil.</b> Do wyliczenia {@link MusicKind} doszła
 * wartość {@code PLAYLIST}, a do {@link MusicProvider} — {@code APPLE_MUSIC}.
 * Wszystkie 125 testów przechodziło, a mimo to <b>każda</b> próba wrzucenia
 * playlisty albo linku z Apple Music kończyła się błędem 500:</p>
 *
 * <pre>
 * ERROR: new row for relation "posts" violates check constraint "posts_music_kind_check"
 * </pre>
 *
 * <p><b>Skąd ta rozbieżność.</b> Przy kolumnie oznaczonej
 * {@code @Enumerated(EnumType.STRING)} Hibernate zakłada w bazie ograniczenie
 * {@code CHECK (music_kind IN ('TRACK','ALBUM','ARTIST'))} — wypisuje tam
 * wartości, które enum miał <i>w chwili zakładania kolumny</i>. Tryb
 * {@code ddl-auto=update} dokłada nowe kolumny i poszerza istniejące, ale
 * <b>nigdy nie rusza raz założonego ograniczenia</b>. Baza zostaje więc przy
 * starej liście na zawsze.</p>
 *
 * <p><b>Dlaczego testy tego nie złapały.</b> Testy idą na H2, gdzie schemat
 * powstaje od zera przy każdym uruchomieniu — czyli od razu z pełną, nową
 * listą wartości. Problem widać wyłącznie na bazie, która <i>już istniała</i>
 * przed zmianą. To dokładnie ta klasa błędów, których nie da się znaleźć
 * inaczej niż uruchomieniem aplikacji na prawdziwych danych, i dlatego
 * został znaleziony dopiero na żywym Postgresie.</p>
 *
 * <p><b>Jak to naprawiamy.</b> Przy starcie zrzucamy stare ograniczenie
 * i zakładamy je na nowo, a listę wartości bierzemy <b>z samej klasy
 * enuma</b>. Dzięki temu nie ma tu żadnej listy do ręcznego pilnowania:
 * dopisanie kolejnego rodzaju nagrania automatycznie trafi też do bazy.</p>
 *
 * <p><b>Można to uruchamiać w kółko.</b> {@code DROP ... IF EXISTS} plus
 * ponowne założenie dają za każdym razem ten sam wynik, więc kolejne starty
 * aplikacji nic nie psują.</p>
 *
 * <p><b>Awaria tego kroku nie zatrzymuje aplikacji.</b> Gdyby baza nie
 * pozwoliła zmienić ograniczenia (np. użytkownik bez uprawnień do ALTER),
 * lepiej wystartować i zapisać ostrzeżenie w logu niż nie wstać w ogóle —
 * reszta aplikacji działa wtedy normalnie.</p>
 */
@Component
public class OdswiezenieOgraniczenEnum implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OdswiezenieOgraniczenEnum.class);

    /** Kolumna trzymajaca nazwy stalych wyliczenia. */
    private record KolumnaEnum(String tabela, String kolumna, Class<? extends Enum<?>> typ) { }

    /*
     * Lista jest krotka celowo - sa tu tylko te kolumny, ktore juz sie
     * rozjechaly albo moga sie rozjechac. Reszta enumow (role, rodzaje
     * reakcji) nie zmieniala sie od zalozenia bazy.
     */
    private static final List<KolumnaEnum> KOLUMNY = List.of(
        new KolumnaEnum("posts", "music_kind", MusicKind.class),
        new KolumnaEnum("posts", "music_provider", MusicProvider.class)
    );

    private final EntityManager entityManager;

    public OdswiezenieOgraniczenEnum(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (KolumnaEnum kolumna : KOLUMNY) {
            odswiez(kolumna);
        }
    }

    private void odswiez(KolumnaEnum kolumna) {
        // Nazwa, ktora nadaje Hibernate: <tabela>_<kolumna>_check
        String nazwa = kolumna.tabela() + "_" + kolumna.kolumna() + "_check";

        /*
         * Wartosci pochodza z klasy enuma, a nie od uzytkownika - to nazwy
         * stalych w Javie, wiec moga zawierac wylacznie litery, cyfry
         * i podkreslenie. Sklejanie ich w tekst zapytania jest tu bezpieczne.
         */
        String lista = Arrays.stream(kolumna.typ().getEnumConstants())
            .map(stala -> "'" + stala.name() + "'")
            .collect(Collectors.joining(", "));

        try {
            entityManager.createNativeQuery(
                "ALTER TABLE " + kolumna.tabela() + " DROP CONSTRAINT IF EXISTS " + nazwa
            ).executeUpdate();

            entityManager.createNativeQuery(
                "ALTER TABLE " + kolumna.tabela() + " ADD CONSTRAINT " + nazwa
                    + " CHECK (" + kolumna.kolumna() + " IN (" + lista + "))"
            ).executeUpdate();

            log.debug("Ograniczenie {} obejmuje teraz wartosci: {}", nazwa, lista);
        } catch (Exception e) {
            log.warn("Nie udalo sie odswiezyc ograniczenia {} - aplikacja startuje dalej. "
                + "Jesli dodawanie nowych rodzajow nagran konczy sie bledem 500, "
                + "wykonaj recznie: ALTER TABLE {} DROP CONSTRAINT {}; ({})",
                nazwa, kolumna.tabela(), nazwa, e.getMessage());
        }
    }
}
