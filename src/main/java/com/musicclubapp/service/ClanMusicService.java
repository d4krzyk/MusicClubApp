package com.musicclubapp.service;

import com.musicclubapp.dto.ClanTasteResponse;
import com.musicclubapp.dto.ClanTrackRequest;
import com.musicclubapp.dto.ClanTrackResponse;
import com.musicclubapp.dto.ClanTracksResponse;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanMember;
import com.musicclubapp.entity.ClanTrack;
import com.musicclubapp.entity.ClanTrackVote;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.ClanMapper;
import com.musicclubapp.music.MusicEmbed;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicLinkParser;
import com.musicclubapp.music.ParsedMusicLink;
import com.musicclubapp.repository.ClanMemberRepository;
import com.musicclubapp.repository.ClanTrackRepository;
import com.musicclubapp.repository.ClanTrackVoteRepository;
import com.musicclubapp.repository.ClanVoteRow;
import com.musicclubapp.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Muzyka klanu: zbiorcze gusta czlonkow i "utwor tygodnia".
 *
 * <p>Gust to wykonawcy i gatunki, ktore lubia co najmniej dwie osoby - bez wskazywania, kto. Do
 * zestawienia nie wchodza osoby z ograniczonym profilem ani te z blokad ogladajacego.</p>
 *
 * <p>Utwor tygodnia: czlonkowie dodaja linki (kazdy najwyzej {@value #NA_OSOBE} w tygodniu),
 * reszta glosuje jednym glosem na propozycje. Tydzien zaczyna sie w poniedzialek (czas polski);
 * po jego koncu glosowanie sie zamyka, a wygrana propozycja zostaje w historii klanu.</p>
 */
@Service
public class ClanMusicService {

    /** Od ilu osob wykonawca albo gatunek trafia do zestawienia gustow. */
    public static final int MINIMUM = 2;

    /** Tyle propozycji na osobe w tygodniu. */
    public static final int NA_OSOBE = 3;

    /** Ile minionych tygodni pokazuje historia. */
    static final int HISTORIA_TYGODNI = 8;

    private static final int ARTYSCI = 12;
    private static final int GATUNKI = 10;

    private final ClanService clans;
    private final ClanMemberRepository members;
    private final UserRepository users;
    private final ClanTrackRepository tracks;
    private final ClanTrackVoteRepository votes;
    private final BlockService blocks;
    private final MusicMetadataService metadata;
    private final Clock clock;

    public ClanMusicService(ClanService clans, ClanMemberRepository members, UserRepository users,
                            ClanTrackRepository tracks, ClanTrackVoteRepository votes, BlockService blocks,
                            MusicMetadataService metadata, Clock clock) {
        this.clans = clans;
        this.members = members;
        this.users = users;
        this.tracks = tracks;
        this.votes = votes;
        this.blocks = blocks;
        this.metadata = metadata;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ */
    /*  Gust klanu                                                         */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public ClanTasteResponse taste(Long clanId, String viewerName) {
        User viewer = user(viewerName);
        Clan clan = clans.requireAccess(viewer, clanId);
        List<Long> ids = visibleMemberIds(clan, viewer);
        long counted = ids.isEmpty() ? 0 : users.countOpenProfiles(ids);
        if (counted < MINIMUM) {
            // Zestawienie z jednej osoby to po prostu jej ulubieni - nie pokazujemy
            return new ClanTasteResponse(ids.size(), (int) counted, MINIMUM, List.of(), List.of());
        }
        List<ClanTasteResponse.Artist> artysci = users.commonArtists(ids, MINIMUM, PageRequest.of(0, ARTYSCI)).stream()
            .map(a -> new ClanTasteResponse.Artist(a.getExternalId(), a.getName(), a.getImageUrl(), a.getTotal()))
            .toList();
        List<ClanTasteResponse.Genre> gatunki = users.commonGenres(ids, MINIMUM, PageRequest.of(0, GATUNKI)).stream()
            .map(g -> new ClanTasteResponse.Genre(g.getName(), g.getTotal()))
            .toList();
        return new ClanTasteResponse(ids.size(), (int) counted, MINIMUM, artysci, gatunki);
    }

    /** Czlonkowie widoczni dla ogladajacego: bez osob z blokad (administrator aplikacji widzi wszystkich). */
    private List<Long> visibleMemberIds(Clan clan, User viewer) {
        List<Long> ukryci = viewer.getRole() == Role.ADMIN && !clan.hasMember(viewer.getId())
            ? List.of() : blocks.hiddenForQuery(viewer.getId());
        return members.ofClan(clan.getId()).stream()
            .map(m -> m.getUser().getId())
            .filter(id -> !ukryci.contains(id))
            .toList();
    }

    /* ------------------------------------------------------------------ */
    /*  Utwor tygodnia                                                     */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public ClanTracksResponse weekly(Long clanId, String viewerName) {
        User viewer = user(viewerName);
        Clan clan = clans.requireAccess(viewer, clanId);
        List<Long> ukryci = hidden(clan, viewer);
        LocalDate tydzien = weekStart();

        List<ClanTrack> biezace = tracks.ofWeek(clanId, tydzien).stream()
            .filter(t -> !ukryci.contains(t.getProposer().getId())).toList();
        List<ClanTrack> minione = tracks.ofWeeks(clanId, tydzien.minusWeeks(HISTORIA_TYGODNI), tydzien).stream()
            .filter(t -> !ukryci.contains(t.getProposer().getId())).toList();

        List<Long> wszystkie = new ArrayList<>();
        biezace.forEach(t -> wszystkie.add(t.getId()));
        minione.forEach(t -> wszystkie.add(t.getId()));
        Map<Long, ClanVoteRow> glosy = counts(wszystkie, viewer, ukryci);

        // Prowadzi ta z najwiekszym wynikiem (co najmniej jeden glos); przy remisie wczesniej dodana
        List<ClanTrack> posortowane = biezace.stream()
            .sorted(Comparator.<ClanTrack>comparingLong(t -> -votesOf(glosy, t.getId()))
                .thenComparing(ClanTrack::getCreatedAt).thenComparing(ClanTrack::getId))
            .toList();
        Long prowadzi = posortowane.isEmpty() || votesOf(glosy, posortowane.get(0).getId()) < 1
            ? null : posortowane.get(0).getId();
        List<ClanTrackResponse> lista = posortowane.stream()
            .map(t -> toResponse(t, glosy, viewer, clan, t.getId().equals(prowadzi)))
            .toList();

        // Zwyciezca kazdego minionego tygodnia (tygodnie bez glosow pomijamy)
        Map<LocalDate, ClanTrack> zwyciezcy = new LinkedHashMap<>();
        for (ClanTrack t : minione) {
            if (votesOf(glosy, t.getId()) < 1) {
                continue;
            }
            ClanTrack dotychczasowy = zwyciezcy.get(t.getWeekStart());
            if (dotychczasowy == null || votesOf(glosy, t.getId()) > votesOf(glosy, dotychczasowy.getId())) {
                zwyciezcy.put(t.getWeekStart(), t);   // przy remisie zostaje wczesniej dodana (lista jest po dacie)
            }
        }
        List<ClanTrackResponse> historia = zwyciezcy.values().stream()
            .map(t -> toResponse(t, glosy, viewer, clan, true)).toList();

        int zostalo = clan.hasMember(viewer.getId())
            ? Math.max(0, NA_OSOBE - (int) tracks.countByClanIdAndProposerIdAndWeekStart(clanId, viewer.getId(), tydzien))
            : 0;
        return new ClanTracksResponse(tydzien, tydzien.plusDays(6), NA_OSOBE, zostalo, lista, historia);
    }

    @Transactional
    public ClanTrackResponse propose(Long clanId, String username, ClanTrackRequest request) {
        Clan clan = clans.requireMember(username, clanId);
        User user = user(username);
        ParsedMusicLink link = MusicLinkParser.parse(request.url()).orElse(null);
        if (link == null || link.kind() != MusicKind.TRACK) {
            throw OperationNotAllowedException.clanTrackInvalid();
        }
        LocalDate tydzien = weekStart();
        if (tracks.countByClanIdAndProposerIdAndWeekStart(clanId, user.getId(), tydzien) >= NA_OSOBE) {
            throw OperationNotAllowedException.clanTrackLimit(NA_OSOBE);
        }
        if (tracks.existsByClanIdAndWeekStartAndMusicProviderAndMusicExternalId(
                clanId, tydzien, link.provider(), link.externalId())) {
            throw OperationNotAllowedException.clanTrackDuplicate();
        }
        MusicMetadataService.Metadata opis = metadata.fetch(link);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        ClanTrack track = tracks.save(new ClanTrack(clan, user, link.provider(), link.kind(), link.externalId(),
            opis.title(), opis.thumbnailUrl(), note, tydzien, LocalDateTime.now(clock)));
        return toResponse(track, Map.of(), user, clan, false);
    }

    /** Glos na propozycje z biezacego tygodnia; drugi raz ten sam glos niczego nie zmienia. */
    @Transactional
    public void vote(Long clanId, Long trackId, String username) {
        User user = user(username);
        ClanTrack track = openTrack(clanId, trackId, user, username);
        if (votes.findMine(trackId, user.getId()).isEmpty()) {
            votes.save(new ClanTrackVote(track, user, LocalDateTime.now(clock)));
        }
    }

    @Transactional
    public void unvote(Long clanId, Long trackId, String username) {
        User user = user(username);
        openTrack(clanId, trackId, user, username);
        votes.findMine(trackId, user.getId()).ifPresent(votes::delete);
    }

    /** Usuwa proponujacy, zarzad klanu albo administrator aplikacji. */
    @Transactional
    public void delete(Long clanId, Long trackId, String username) {
        User user = user(username);
        Clan clan = clans.requireAccess(user, clanId);
        ClanTrack track = tracks.findById(trackId)
            .filter(t -> t.getClan().getId().equals(clanId))
            .orElseThrow(() -> new NoSuchElementFoundException("clan track", trackId));
        if (!canDelete(track, user, clan)) {
            throw OperationNotAllowedException.clanNotManager();
        }
        votes.deleteByTrackId(trackId);
        tracks.delete(track);
    }

    /** Propozycja z tego klanu, ktora ogladajacy widzi i ktora nalezy do biezacego tygodnia. */
    private ClanTrack openTrack(Long clanId, Long trackId, User user, String username) {
        Clan clan = clans.requireMember(username, clanId);
        ClanTrack track = tracks.findById(trackId)
            .filter(t -> t.getClan().getId().equals(clan.getId()))
            .filter(t -> !blocks.hiddenForQuery(user.getId()).contains(t.getProposer().getId()))
            .orElseThrow(() -> new NoSuchElementFoundException("clan track", trackId));
        if (!track.getWeekStart().equals(weekStart())) {
            throw OperationNotAllowedException.clanTrackClosed();
        }
        return track;
    }

    private boolean canDelete(ClanTrack track, User viewer, Clan clan) {
        return track.getProposer().getId().equals(viewer.getId())
            || viewer.getRole() == Role.ADMIN
            || clans.canModerate(viewer, clan);
    }

    private List<Long> hidden(Clan clan, User viewer) {
        return viewer.getRole() == Role.ADMIN && !clan.hasMember(viewer.getId())
            ? List.of(-1L) : blocks.hiddenForQuery(viewer.getId());
    }

    private Map<Long, ClanVoteRow> counts(List<Long> ids, User viewer, List<Long> ukryci) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ClanVoteRow> wynik = new HashMap<>();
        votes.counts(ids, viewer.getId(), ukryci).forEach(r -> wynik.put(r.getTrackId(), r));
        return wynik;
    }

    private static long votesOf(Map<Long, ClanVoteRow> glosy, Long trackId) {
        ClanVoteRow r = glosy.get(trackId);
        return r == null ? 0 : r.getTotal();
    }

    private ClanTrackResponse toResponse(ClanTrack t, Map<Long, ClanVoteRow> glosy, User viewer, Clan clan,
                                         boolean leader) {
        ClanVoteRow r = glosy.get(t.getId());
        return new ClanTrackResponse(t.getId(), t.getProposer().getUsername(),
            ClanMapper.avatarUrl(t.getProposer()), t.getMusicTitle(), t.getMusicThumbnailUrl(), t.getMusicProvider(),
            MusicEmbed.embedUrl(t.getMusicProvider(), t.getMusicKind(), t.getMusicExternalId(), null),
            MusicEmbed.canonicalUrl(t.getMusicProvider(), t.getMusicKind(), t.getMusicExternalId()),
            t.getNote(), r == null ? 0 : r.getTotal(), r != null && r.getMine() != null && r.getMine() > 0,
            leader, canDelete(t, viewer, clan), t.getWeekStart(), t.getCreatedAt());
    }

    /** Poniedzialek biezacego tygodnia wedlug czasu polskiego. */
    LocalDate weekStart() {
        return LocalDate.now(clock.withZone(EventImportService.STREFA))
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private User user(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
