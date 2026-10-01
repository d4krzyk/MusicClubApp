package com.musicclubapp.service;

import com.musicclubapp.dto.ClanActivityLevel;
import com.musicclubapp.dto.ClanDirectoryEntry;
import com.musicclubapp.dto.ClanDirectoryResponse;
import com.musicclubapp.dto.ClanDirectorySort;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanJoinPolicy;
import com.musicclubapp.entity.ClanJoinRequest;
import com.musicclubapp.entity.InvitationStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.mapper.ClanMapper;
import com.musicclubapp.repository.ClanCountRow;
import com.musicclubapp.repository.ClanGenreRow;
import com.musicclubapp.repository.ClanInvitationRepository;
import com.musicclubapp.repository.ClanJoinRequestRepository;
import com.musicclubapp.repository.ClanMemberRepository;
import com.musicclubapp.repository.ClanMessageRepository;
import com.musicclubapp.repository.ClanRepository;
import com.musicclubapp.repository.ClanTasteRow;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Przegladarka klanow: lista klanow z przegladarki z szukaniem, filtrami i sortowaniem.
 *
 * <p>Pokazuje tylko to, co pozwala sie rozeznac, "co to za klan": nazwe, haslo, miasto, gatunki
 * (podane przez klan i wspolne dla co najmniej dwoch czlonkow), liczbe osob, date zalozenia i
 * poziom aktywnosci czatu. Nigdy nie pokazuje postow, czatu ani listy czlonkow z ich gustami.</p>
 *
 * <p>Lista klanow liczy sie w pamieci (kilka zapytan grupujacych na calosc, bez zapytania na klan),
 * wiec filtrowanie i sortowanie po dowolnej cesze kosztuje tyle samo.</p>
 */
@Service
public class ClanDirectoryService {

    public static final int DOMYSLNIE = 12;
    static final int MAKSIMUM = 50;
    private static final int FACETY = 40;
    private static final int OPIS = 140;

    private final ClanRepository clans;
    private final ClanMemberRepository members;
    private final ClanMessageRepository messages;
    private final ClanInvitationRepository invitations;
    private final ClanJoinRequestRepository requests;
    private final UserRepository users;
    private final BlockService blocks;
    private final Clock clock;

    public ClanDirectoryService(ClanRepository clans, ClanMemberRepository members, ClanMessageRepository messages,
                                ClanInvitationRepository invitations, ClanJoinRequestRepository requests,
                                UserRepository users, BlockService blocks, Clock clock) {
        this.clans = clans;
        this.members = members;
        this.messages = messages;
        this.invitations = invitations;
        this.requests = requests;
        this.users = users;
        this.blocks = blocks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClanDirectoryResponse list(String viewerName, String q, String genre, String city, boolean joinableOnly,
                                      ClanDirectorySort sort, int page, int size) {
        User viewer = users.findByUsername(viewerName)
            .orElseThrow(() -> new NoSuchElementFoundException("user", viewerName));
        LocalDateTime teraz = LocalDateTime.now(clock);

        Set<Long> ukryci = blocks.hiddenFor(viewer.getId());
        Map<Long, Long> liczby = members.memberCounts().stream()
            .collect(Collectors.toMap(ClanCountRow::getClanId, ClanCountRow::getTotal));
        Map<Long, Long> zalozyciele = new HashMap<>();
        members.founders().forEach(r -> zalozyciele.put(r.getClanId(), r.getUserId()));
        Map<Long, Long> aktywnosc = messages.countsSince(teraz.minusDays(7)).stream()
            .collect(Collectors.toMap(ClanCountRow::getClanId, ClanCountRow::getTotal));

        Map<Long, List<String>> deklarowane = new HashMap<>();
        for (ClanGenreRow r : clans.listedGenres()) {
            deklarowane.computeIfAbsent(r.getClanId(), k -> new ArrayList<>()).add(r.getGenre());
        }
        // Gust zbiorczy: gatunki i wykonawcy wspolne dla co najmniej dwoch czlonkow z jawnym profilem
        Map<Long, Map<String, Long>> gatunkiGustu = new HashMap<>();
        for (ClanTasteRow r : members.listedGenres()) {
            gatunkiGustu.computeIfAbsent(r.getClanId(), k -> new HashMap<>())
                .merge(r.getName().toLowerCase(Locale.ROOT), r.getTotal(), Math::max);
        }
        Map<Long, Set<String>> artysciGustu = new HashMap<>();
        for (ClanTasteRow r : members.listedArtists()) {
            artysciGustu.computeIfAbsent(r.getClanId(), k -> new HashSet<>()).add(r.getName());
        }

        // Moj gust i moje sprawy z klanami
        Set<String> mojeGatunki = users.genresOf(viewerName).stream()
            .map(g -> g.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> mojiArtysci = viewer.getFavoriteArtists().stream()
            .map(a -> a.getExternalId()).collect(Collectors.toSet());
        Map<Long, ClanJoinRequest> mojeProsby = new HashMap<>();
        for (ClanJoinRequest r : requests.ofUser(viewer.getId())) {
            mojeProsby.put(r.getClan().getId(), r);
        }
        Set<Long> zaproszenia = invitations.pendingFor(viewer.getId()).stream()
            .map(i -> i.getClan().getId()).collect(Collectors.toSet());
        boolean mamKlan = members.findByUserId(viewer.getId()).isPresent();

        List<Entry> wszystkie = new ArrayList<>();
        for (Clan c : clans.listed()) {
            Long zalozyciel = zalozyciele.get(c.getId());
            if (zalozyciel != null && ukryci.contains(zalozyciel)) {
                continue; // zalozyciel jest z ogladajacym w blokadzie (w ktoras strone) - klanu "nie ma"
            }
            int osoby = liczby.getOrDefault(c.getId(), 0L).intValue();
            List<String> wlasne = deklarowane.getOrDefault(c.getId(), List.of());
            Map<String, Long> gust = gatunkiGustu.getOrDefault(c.getId(), Map.of());
            List<String> top = gust.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3).map(Map.Entry::getKey).toList();
            Set<String> wszystkieGatunki = new LinkedHashSet<>(wlasne);
            wszystkieGatunki.addAll(gust.keySet());
            List<String> wspolne = wszystkieGatunki.stream().filter(mojeGatunki::contains).sorted().toList();
            long wspolniArtysci = artysciGustu.getOrDefault(c.getId(), Set.of()).stream()
                .filter(mojiArtysci::contains).count();
            int dopasowanie = (int) (3 * wspolniArtysci + wspolne.size());

            ClanJoinRequest prosba = mojeProsby.get(c.getId());
            InvitationStatus statusProsby = prosba == null ? null
                : prosba.getStatus() == InvitationStatus.PENDING
                    || (prosba.getAnsweredAt() != null
                        && prosba.getAnsweredAt().plus(ClanService.PROSBA_PO_ODMOWIE).isAfter(teraz))
                    ? prosba.getStatus() : null;
            long wiadomosci = aktywnosc.getOrDefault(c.getId(), 0L);

            ClanDirectoryEntry wpis = new ClanDirectoryEntry(c.getId(), c.getName(), c.getTag(),
                c.getColor().name(), c.getColor().hex(), ClanMapper.uploadUrl(c.getIconFileName()),
                skroc(c.getDescription()), c.getMotto(), c.getCity(), c.getCreatedAt(), osoby, Clan.MAX_MEMBERS,
                List.copyOf(wlasne), top, ClanActivityLevel.of(wiadomosci), c.getJoinPolicy(),
                osoby >= Clan.MAX_MEMBERS, dopasowanie, wspolne, statusProsby, zaproszenia.contains(c.getId()));
            wszystkie.add(new Entry(wpis, wszystkieGatunki, wiadomosci, c.getNameKey()));
        }

        // Gatunki do filtra - z calej przegladarki, zeby lista nie zmieniala sie po wybraniu gatunku
        Map<String, Integer> facety = new TreeMap<>();
        for (Entry e : wszystkie) {
            e.gatunki.forEach(g -> facety.merge(g, 1, Integer::sum));
        }
        List<ClanDirectoryResponse.GenreFacet> gatunkiDoFiltra = facety.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(FACETY).map(e -> new ClanDirectoryResponse.GenreFacet(e.getKey(), e.getValue())).toList();

        String szukane = q == null || q.isBlank() ? null : NameKeys.of(q);
        String szukaneMiasto = city == null || city.isBlank() ? null : NameKeys.of(city);
        String szukanyGatunek = genre == null || genre.isBlank() ? null : genre.strip().toLowerCase(Locale.ROOT);
        List<Entry> wynik = wszystkie.stream()
            .filter(e -> szukane == null || pasuje(e, szukane, q))
            .filter(e -> szukaneMiasto == null || (e.wpis.city() != null && NameKeys.of(e.wpis.city()).contains(szukaneMiasto)))
            .filter(e -> szukanyGatunek == null || e.gatunki.contains(szukanyGatunek))
            .filter(e -> !joinableOnly || (e.wpis.joinPolicy() == ClanJoinPolicy.REQUESTS && !e.wpis.full() && !mamKlan))
            .sorted(porzadek(sort))
            .toList();

        int rozmiar = Math.min(Math.max(size, 1), MAKSIMUM);
        int strona = Math.max(page, 0);
        int od = Math.min(strona * rozmiar, wynik.size());
        int doIndeksu = Math.min(od + rozmiar, wynik.size());
        return new ClanDirectoryResponse(wynik.subList(od, doIndeksu).stream().map(e -> e.wpis).toList(),
            wynik.size(), strona, rozmiar, doIndeksu >= wynik.size(), gatunkiDoFiltra);
    }

    /** Wpis razem z tym, czego potrzeba do filtrowania i sortowania, a czego na zewnatrz nie widac. */
    private record Entry(ClanDirectoryEntry wpis, Set<String> gatunki, long wiadomosci, String nameKey) {
    }

    private static boolean pasuje(Entry e, String klucz, String surowe) {
        ClanDirectoryEntry w = e.wpis;
        return e.nameKey.contains(klucz)
            || w.tag().toLowerCase(Locale.ROOT).contains(surowe.strip().toLowerCase(Locale.ROOT))
            || (w.motto() != null && NameKeys.of(w.motto()).contains(klucz))
            || (w.city() != null && NameKeys.of(w.city()).contains(klucz));
    }

    private static Comparator<Entry> porzadek(ClanDirectorySort sort) {
        Comparator<Entry> nazwa = Comparator.comparing(Entry::nameKey);
        ClanDirectorySort s = sort == null ? ClanDirectorySort.MATCH : sort;
        return switch (s) {
            case MATCH -> Comparator.<Entry>comparingInt(e -> e.wpis.match()).reversed()
                .thenComparing(Comparator.<Entry>comparingLong(e -> e.wiadomosci).reversed()).thenComparing(nazwa);
            case MEMBERS -> Comparator.<Entry>comparingInt(e -> e.wpis.memberCount()).reversed().thenComparing(nazwa);
            case NEWEST -> Comparator.<Entry, LocalDateTime>comparing(e -> e.wpis.createdAt()).reversed().thenComparing(nazwa);
            case OLDEST -> Comparator.<Entry, LocalDateTime>comparing(e -> e.wpis.createdAt()).thenComparing(nazwa);
            case ACTIVE -> Comparator.<Entry>comparingLong(e -> e.wiadomosci).reversed()
                .thenComparing(Comparator.<Entry>comparingInt(e -> e.wpis.memberCount()).reversed()).thenComparing(nazwa);
            case NAME -> nazwa;
        };
    }

    private static String skroc(String opis) {
        if (opis == null) {
            return null;
        }
        return opis.length() <= OPIS ? opis : opis.substring(0, OPIS - 1).stripTrailing() + "…";
    }
}
