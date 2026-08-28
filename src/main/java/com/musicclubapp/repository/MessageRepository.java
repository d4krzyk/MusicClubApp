package com.musicclubapp.repository;

import com.musicclubapp.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Zapytania o wiadomosci.
 *
 * <p><b>Wszystko chodzi po identyfikatorach, nie po loginach.</b> Login
 * mozna zmienic, a rozmowa ma przetrwac taka zmiane. Zamiana loginu na
 * identyfikator odbywa sie raz, w serwisie.</p>
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Historia rozmowy dwoch osob, <b>od najnowszej</b>.
     *
     * <p>Kierunek jest odwrotny do tego, co widac na ekranie - i tak ma byc.
     * Czat otwiera sie na koncu rozmowy, wiec pierwsza strona ma zawierac
     * wiadomosci NAJNOWSZE. Odwrocenie ich przed narysowaniem kosztuje jedna
     * linijke w przegladarce; pobieranie od poczatku kosztowaloby sciagniecie
     * calej rozmowy, zeby pokazac jej ostatnie dwadziescia zdan.</p>
     *
     * <p>Sortujemy po {@code id}, a nie po {@code createdAt}. Dwie wiadomosci
     * wyslane w tej samej milisekundzie maja identyczny znacznik czasu i baza
     * moze wtedy zwrocic je w dowolnej kolejnosci - takze innej na kazdej
     * stronie, co przy stronicowaniu potrafi zgubic albo powtorzyc wpis.
     * Identyfikator z sekwencji zawsze rosnie, wiec porzadek jest jeden.</p>
     */
    @Query("""
           SELECT m FROM Message m
           WHERE (m.sender.id = :first  AND m.recipient.id = :second)
              OR (m.sender.id = :second AND m.recipient.id = :first)
           ORDER BY m.id DESC
           """)
    Page<Message> conversation(@Param("first") Long first,
                               @Param("second") Long second,
                               Pageable pageable);

    /**
     * Wiadomosci z rozmowy <b>nowsze niz ta, ktora juz mamy</b>.
     *
     * <p>Tego uzywa odpytywanie otwartego okna czatu. Rosnaco, bo dopisujemy
     * je na koncu listy - tutaj kolejnosc ekranu i kolejnosc zapytania sa
     * te same.</p>
     *
     * <p><b>Po co limit, skoro to tylko "co nowego".</b> Okno moze stac
     * otwarte na karcie, do ktorej nikt nie wraca przez godzine. Bez limitu
     * pierwsze odpytanie po powrocie sciagneloby wszystko, co przez ten czas
     * przyszlo, jednym kawalkiem.</p>
     */
    @Query("""
           SELECT m FROM Message m
           WHERE ((m.sender.id = :first  AND m.recipient.id = :second)
               OR (m.sender.id = :second AND m.recipient.id = :first))
             AND m.id > :afterId
           ORDER BY m.id ASC
           """)
    List<Message> newerThan(@Param("first") Long first,
                            @Param("second") Long second,
                            @Param("afterId") Long afterId,
                            Pageable limit);

    /**
     * <b>Ostatnia wiadomosc kazdej rozmowy</b> - z kim i ktora.
     *
     * <p>To jest ta "rozmowa", ktorej nie ma w bazie jako osobnej tabeli.
     * Grupujemy po drugiej stronie rozmowy: dla kazdej wiadomosci pytamy
     * "kto tu jest tym drugim" i bierzemy najwiekszy identyfikator w grupie.</p>
     *
     * <p><b>{@code MAX(id)}, a nie {@code MAX(createdAt)}</b> - z tego samego
     * powodu co sortowanie wyzej. Po znaczniku czasu dwie wiadomosci wyslane
     * w tej samej milisekundzie daja remis, ktorego zapytanie nie umie
     * rozstrzygnac; po identyfikatorze remisu nie ma.</p>
     *
     * <p><b>Wyrazenie {@code CASE} stoi tu DWA razy - i musi.</b> Pierwsza
     * wersja tego zapytania miala je wylacznie w {@code GROUP BY}, bo tylko
     * tam bylo potrzebne. JPQL to przyjal, Hibernate przetlumaczyl, a baza
     * odmowila: <i>"Invalid use of aggregate function MAX(id)"</i>. Powod jest
     * taki, ze przy grupowaniu po wyrazeniu baza chce widziec to wyrazenie
     * takze na liscie wynikow - inaczej nie ma jak powiazac zagregowanej
     * wartosci z grupa. Kosztuje to jedno pole wiecej w wyniku, a przy okazji
     * oszczedza dochodzenia w Javie, kto jest ta druga strona.</p>
     *
     * <p>Blad wyszedl dopiero w tescie na prawdziwej bazie - z atrapa
     * repozytorium przechodzilby, bo atrapa oddaje to, co jej kazemy,
     * i nigdy nie zaglada do SQL-a.</p>
     */
    @Query("""
           SELECT new com.musicclubapp.repository.ConversationRow(
                  CASE WHEN m.sender.id = :me THEN m.recipient.id ELSE m.sender.id END,
                  MAX(m.id))
           FROM Message m
           WHERE m.sender.id = :me OR m.recipient.id = :me
           GROUP BY CASE WHEN m.sender.id = :me THEN m.recipient.id ELSE m.sender.id END
           """)
    List<ConversationRow> lastMessagePerConversation(@Param("me") Long me);

    /**
     * Ile nieprzeczytanych czeka od kazdej osoby z osobna - <b>jednym zapytaniem</b>.
     *
     * <p>Alternatywa jest pytanie o licznik osobno dla kazdego znajomego, czyli
     * przy trzydziestu znajomych trzydziesci zapytan na jedno otwarcie czatu.</p>
     */
    @Query("""
           SELECT new com.musicclubapp.repository.UnreadRow(m.sender.id, COUNT(m))
           FROM Message m
           WHERE m.recipient.id = :me AND m.readAt IS NULL
           GROUP BY m.sender.id
           """)
    List<UnreadRow> unreadBySender(@Param("me") Long me);

    /**
     * Najwyzszy identyfikator MOJEJ wiadomosci, ktora rozmowca juz przeczytal.
     *
     * <p><b>Po co osobne pytanie.</b> Odpytywanie otwartej rozmowy przynosi
     * wylacznie wiadomosci NOWSZE od tej, ktora przegladarka juz ma - i to
     * jest wlasciwe, bo o to chodzi w odpytywaniu. Ale przeczytanie nie
     * tworzy nowej wiadomosci: zmienia jedna kolumne w starej, ktora dawno
     * zostala wyslana. Bez tego zapytania ptaszek "przeczytane" nie
     * pojawilby sie NIGDY bez odswiezenia calej strony - i dokladnie tak
     * bylo, dopoki nie zauwazylo tego sprawdzenie w przegladarce.</p>
     *
     * <p>Jeden identyfikator wystarcza na cala rozmowe, bo wiadomosci sa
     * czytane po kolei: skoro przeczytana jest ta o numerze 40, to wszystkie
     * wczesniejsze tez. Przegladarka moze wiec oznaczyc u siebie wszystko
     * do tego numeru wlacznie.</p>
     *
     * @return identyfikator albo {@code null}, gdy nic jeszcze nie przeczytano
     */
    @Query("""
           SELECT MAX(m.id) FROM Message m
           WHERE m.sender.id = :me
             AND m.recipient.id = :partner
             AND m.readAt IS NOT NULL
           """)
    Long lastReadOutgoingId(@Param("me") Long me, @Param("partner") Long partner);

    /** Laczna liczba nieprzeczytanych - to ona wisi przy ikonie czatu. */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.recipient.id = :me AND m.readAt IS NULL")
    long countUnread(@Param("me") Long me);

    /**
     * Oznacza cala rozmowe jako przeczytana.
     *
     * <p>Jednym zapytaniem, a nie w petli po encjach - wiadomosci moga byc
     * setki, a interesuje nas wylacznie ustawienie jednej kolumny.</p>
     *
     * <p><b>{@code clearAutomatically} jest tu konieczne.</b> Zapytanie
     * masowe idzie do bazy Z POMINIECIEM pamieci podrecznej Hibernate'a.
     * Gdyby ta pamiec nie zostala wyczyszczona, kod czytajacy w tej samej
     * transakcji te same wiadomosci dostalby wersje sprzed zmiany - czyli
     * dalej nieprzeczytane, mimo ze w bazie sa juz oznaczone.
     * {@code flushAutomatically} pilnuje odwrotnego przypadku: swiezo
     * zapisana wiadomosc musi trafic do bazy ZANIM zadziala ten UPDATE,
     * inaczej by go po prostu nie objal.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE Message m SET m.readAt = :now
           WHERE m.recipient.id = :me
             AND m.sender.id = :partner
             AND m.readAt IS NULL
           """)
    int markConversationRead(@Param("me") Long me,
                             @Param("partner") Long partner,
                             @Param("now") LocalDateTime now);

    /**
     * Kasuje wszystkie wiadomosci danego konta - <b>w obie strony</b>.
     *
     * <p>Uzywane przy usuwaniu konta. Wiadomosci wskazuja na uzytkownika
     * dwoma kluczami obcymi, a encja {@code User} nic o nich nie wie - gdyby
     * zostaly, baza odmowilaby skasowania konta.</p>
     *
     * <p>Bez {@code clearAutomatically}: kasowanie konta dziala na encji
     * {@code User}, ktora zaraz potem jest jeszcze uzywana (czyszczenie
     * znajomych, ulubionych). Wyczyszczenie pamieci podrecznej odczepiloby
     * ja w polowie tej operacji.</p>
     */
    @Modifying
    @Query("DELETE FROM Message m WHERE m.sender.id = :userId OR m.recipient.id = :userId")
    void deleteAllOfUser(@Param("userId") Long userId);
}
