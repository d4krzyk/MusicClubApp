package com.musicclubapp.service;

import com.musicclubapp.dto.ClanPollRequest;
import com.musicclubapp.dto.ClanPollResponse;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanPoll;
import com.musicclubapp.entity.ClanPollOption;
import com.musicclubapp.entity.ClanPollVote;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.ClanPollRepository;
import com.musicclubapp.repository.ClanPollVoteRepository;
import com.musicclubapp.repository.ClanPollVoteRow;
import com.musicclubapp.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ankiety w klanie ("jaki koncert wybieramy?"): pytanie, 2-6 odpowiedzi, jeden glos na osobe
 * (mozna go zmienic, dopoki ankieta jest otwarta), termin zamkniecia 1/3/7/14 dni albo wczesniejsze
 * zamkniecie przez autora i zarzad. Wyniki sa na biezaco, bez wskazywania, kto na co glosowal.
 */
@Service
public class ClanPollService {

    static final int NA_OSOBE = 2;
    static final int NA_KLAN = 5;
    static final Set<Integer> DNI = Set.of(1, 3, 7, 14);
    static final int DOMYSLNIE_DNI = 7;

    /** Tyle ostatnich ankiet widac na liscie (otwarte i zamkniete razem). */
    private static final int NA_LISCIE = 15;

    private final ClanService clans;
    private final ClanPollRepository polls;
    private final ClanPollVoteRepository votes;
    private final UserRepository users;
    private final BlockService blocks;
    private final Clock clock;

    public ClanPollService(ClanService clans, ClanPollRepository polls, ClanPollVoteRepository votes,
                           UserRepository users, BlockService blocks, Clock clock) {
        this.clans = clans;
        this.polls = polls;
        this.votes = votes;
        this.users = users;
        this.blocks = blocks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ClanPollResponse> list(Long clanId, String viewerName) {
        User viewer = user(viewerName);
        Clan clan = clans.requireAccess(viewer, clanId);
        List<Long> ukryci = hidden(viewer, clan);
        LocalDateTime teraz = LocalDateTime.now(clock);
        List<ClanPoll> ankiety = polls.latest(clanId, PageRequest.of(0, NA_LISCIE)).stream()
            .filter(p -> !ukryci.contains(p.getAuthor().getId())).toList();
        Map<Long, ClanPollVoteRow> wyniki = counts(ankiety, viewer, ukryci);
        // Otwarte na gorze, w srodku od najnowszej
        return ankiety.stream()
            .sorted(Comparator.<ClanPoll>comparingInt(p -> p.isOpen(teraz) ? 0 : 1)
                .thenComparing(Comparator.comparing(ClanPoll::getId).reversed()))
            .map(p -> toResponse(p, wyniki, viewer, clan, teraz)).toList();
    }

    @Transactional
    public ClanPollResponse create(Long clanId, String username, ClanPollRequest request) {
        Clan clan = clans.requireMember(username, clanId);
        User author = user(username);
        List<String> odpowiedzi = checkOptions(request.options());
        String pytanie = request.question() == null ? "" : request.question().strip().replaceAll("\\s+", " ");
        int dni = request.days() == null ? DOMYSLNIE_DNI : request.days();
        if (pytanie.isEmpty() || !DNI.contains(dni)) {
            throw OperationNotAllowedException.clanPollInvalid();
        }
        LocalDateTime teraz = LocalDateTime.now(clock);
        if (polls.countOpenOf(clanId, author.getId(), teraz) >= NA_OSOBE) {
            throw OperationNotAllowedException.clanPollLimit(NA_OSOBE);
        }
        if (polls.countOpen(clanId, teraz) >= NA_KLAN) {
            throw OperationNotAllowedException.clanPollLimit(NA_KLAN);
        }
        ClanPoll poll = new ClanPoll(clan, author, pytanie, teraz, teraz.plusDays(dni));
        odpowiedzi.forEach(poll::addOption);
        polls.save(poll);
        return toResponse(poll, Map.of(), author, clan, teraz);
    }

    /** Glos albo zmiana glosu - tylko w otwartej ankiecie. */
    @Transactional
    public void vote(Long clanId, Long pollId, String username, Long optionId) {
        User user = user(username);
        ClanPoll poll = openPoll(clanId, pollId, user, username);
        ClanPollOption opcja = poll.getOptions().stream().filter(o -> o.getId().equals(optionId)).findFirst()
            .orElseThrow(() -> new NoSuchElementFoundException("clan poll option", optionId));
        LocalDateTime teraz = LocalDateTime.now(clock);
        votes.findMine(pollId, user.getId()).ifPresentOrElse(
            v -> v.change(opcja, teraz),
            () -> votes.save(new ClanPollVote(poll, opcja, user, teraz)));
    }

    @Transactional
    public void unvote(Long clanId, Long pollId, String username) {
        User user = user(username);
        openPoll(clanId, pollId, user, username);
        votes.findMine(pollId, user.getId()).ifPresent(votes::delete);
    }

    /** Wczesniejsze zamkniecie: autor albo zarzad klanu. */
    @Transactional
    public void close(Long clanId, Long pollId, String username) {
        User user = user(username);
        Clan clan = clans.requireAccess(user, clanId);
        poll(clanId, pollId, user, clan).close();
    }

    @Transactional
    public void delete(Long clanId, Long pollId, String username) {
        User user = user(username);
        Clan clan = clans.requireAccess(user, clanId);
        ClanPoll poll = poll(clanId, pollId, user, clan);
        votes.deleteByPollId(pollId);
        polls.delete(poll);
    }

    /* --- pomocnicze --- */

    private ClanPoll openPoll(Long clanId, Long pollId, User user, String username) {
        Clan clan = clans.requireMember(username, clanId);
        List<Long> ukryci = blocks.hiddenForQuery(user.getId());
        ClanPoll poll = polls.findById(pollId)
            .filter(p -> p.getClan().getId().equals(clan.getId()))
            .filter(p -> !ukryci.contains(p.getAuthor().getId()))
            .orElseThrow(() -> new NoSuchElementFoundException("clan poll", pollId));
        if (!poll.isOpen(LocalDateTime.now(clock))) {
            throw OperationNotAllowedException.clanPollClosed();
        }
        return poll;
    }

    /** Ankieta, ktora ta osoba moze zamknac albo usunac: autor, zarzad klanu albo administrator aplikacji. */
    private ClanPoll poll(Long clanId, Long pollId, User user, Clan clan) {
        ClanPoll poll = polls.findById(pollId)
            .filter(p -> p.getClan().getId().equals(clanId))
            .orElseThrow(() -> new NoSuchElementFoundException("clan poll", pollId));
        if (!canManage(poll, user, clan)) {
            throw OperationNotAllowedException.clanNotManager();
        }
        return poll;
    }

    private boolean canManage(ClanPoll poll, User viewer, Clan clan) {
        return poll.getAuthor().getId().equals(viewer.getId())
            || viewer.getRole() == Role.ADMIN
            || clans.canModerate(viewer, clan);
    }

    private List<String> checkOptions(List<String> raw) {
        List<String> wynik = new ArrayList<>();
        Set<String> widziane = new HashSet<>();
        for (String o : raw == null ? List.<String>of() : raw) {
            String odpowiedz = o == null ? "" : o.strip().replaceAll("\\s+", " ");
            if (odpowiedz.isEmpty() || odpowiedz.length() > ClanPoll.OPTION_MAX
                || !widziane.add(odpowiedz.toLowerCase(Locale.ROOT))) {
                throw OperationNotAllowedException.clanPollInvalid();
            }
            wynik.add(odpowiedz);
        }
        if (wynik.size() < ClanPoll.OPTIONS_MIN || wynik.size() > ClanPoll.OPTIONS_MAX) {
            throw OperationNotAllowedException.clanPollInvalid();
        }
        return wynik;
    }

    private List<Long> hidden(User viewer, Clan clan) {
        return viewer.getRole() == Role.ADMIN && !clan.hasMember(viewer.getId())
            ? List.of(-1L) : blocks.hiddenForQuery(viewer.getId());
    }

    private Map<Long, ClanPollVoteRow> counts(List<ClanPoll> ankiety, User viewer, List<Long> ukryci) {
        if (ankiety.isEmpty()) {
            return Map.of();
        }
        Map<Long, ClanPollVoteRow> wynik = new HashMap<>();
        votes.counts(ankiety.stream().map(ClanPoll::getId).toList(), viewer.getId(), ukryci)
            .forEach(r -> wynik.put(r.getOptionId(), r));
        return wynik;
    }

    private ClanPollResponse toResponse(ClanPoll p, Map<Long, ClanPollVoteRow> wyniki, User viewer, Clan clan,
                                        LocalDateTime teraz) {
        long razem = 0;
        Long moja = null;
        List<ClanPollResponse.Option> odpowiedzi = new ArrayList<>();
        for (ClanPollOption o : p.getOptions()) {
            ClanPollVoteRow r = wyniki.get(o.getId());
            long glosy = r == null ? 0 : r.getTotal();
            razem += glosy;
            if (r != null && r.getMine() != null && r.getMine() > 0) {
                moja = o.getId();
            }
            odpowiedzi.add(new ClanPollResponse.Option(o.getId(), o.getText(), glosy));
        }
        return new ClanPollResponse(p.getId(), p.getQuestion(), p.getAuthor().getUsername(), p.getCreatedAt(),
            p.getClosesAt(), p.isOpen(teraz), odpowiedzi, moja, razem, canManage(p, viewer, clan));
    }

    private User user(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
