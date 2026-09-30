package com.musicclubapp.service;

import com.musicclubapp.dto.ClanMessageReactions;
import com.musicclubapp.dto.ClanMessageResponse;
import com.musicclubapp.dto.ClanReactionCount;
import com.musicclubapp.dto.ClanReplyPreview;
import com.musicclubapp.dto.ClanUnreadResponse;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanEmoji;
import com.musicclubapp.entity.ClanMember;
import com.musicclubapp.entity.ClanMessage;
import com.musicclubapp.entity.ClanMessageReaction;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.ClanMapper;
import com.musicclubapp.repository.ClanMemberRepository;
import com.musicclubapp.repository.ClanMessageReactionRepository;
import com.musicclubapp.repository.ClanMessageRepository;
import com.musicclubapp.repository.ClanReactionRow;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Czat klanu - jedna wspolna rozmowa dla wszystkich czlonkow.
 *
 * <p>Dziala na tej samej zasadzie co czat miedzy znajomymi: przegladarka pyta co kilka sekund
 * o wiadomosci nowsze od ostatniej, ktora ma. Widza go czlonkowie i administrator aplikacji
 * (moderacja - patrz polityka prywatnosci). Wiadomosci osob z blokad ogladajacego sa pomijane.</p>
 *
 * <p>Czlonek ma znacznik "przeczytane do" ({@link ClanMember#getChatReadId()}) - z niego wynika
 * licznik nieprzeczytanych i to, czy nowa wiadomosc ma wyslac powiadomienie na telefon (tylko gdy
 * czlonek nie ma jeszcze zadnej nieprzeczytanej i nie wyciszyl czatu). Wiadomosci mozna
 * opatrywac reakcjami (jedna na osobe) i odpowiadac na konkretna wiadomosc.</p>
 */
@Service
public class ClanChatService {

    private static final Logger log = LoggerFactory.getLogger(ClanChatService.class);

    public static final int DOMYSLNIE = 40;
    public static final int MAKSIMUM = 100;

    /** Dlugosc skrotu cytowanej wiadomosci. */
    static final int SKROT = 120;

    private final ClanMessageRepository messages;
    private final ClanMessageReactionRepository reactions;
    private final ClanMemberRepository members;
    private final UserRepository users;
    private final ClanService clans;
    private final BlockService blocks;
    private final PushService push;
    private final Clock clock;

    public ClanChatService(ClanMessageRepository messages, ClanMessageReactionRepository reactions,
                           ClanMemberRepository members, UserRepository users, ClanService clans,
                           BlockService blocks, PushService push, Clock clock) {
        this.messages = messages;
        this.reactions = reactions;
        this.members = members;
        this.users = users;
        this.clans = clans;
        this.blocks = blocks;
        this.push = push;
        this.clock = clock;
    }

    /**
     * Wiadomosci od najstarszej do najnowszej. Bez parametrow - ostatnie {@code limit};
     * {@code after} - nowsze od podanej (odswiezanie); {@code before} - starsze od podanej
     * ("pokaz wczesniejsze").
     */
    @Transactional(readOnly = true)
    public List<ClanMessageResponse> list(Long clanId, String viewerName, Long after, Long before, int limit) {
        User viewer = user(viewerName);
        Clan clan = clans.requireAccess(viewer, clanId);
        boolean adminBezCzlonkostwa = viewer.getRole() == Role.ADMIN && !clan.hasMember(viewer.getId());
        if (adminBezCzlonkostwa && after == null && before == null) {
            log.info("Audyt: administrator {} czyta czat klanu {} (#{})", viewer.getUsername(), clan.getName(), clan.getId());
        }

        List<Long> ukryci = adminBezCzlonkostwa ? List.of(-1L) : blocks.hiddenForQuery(viewer.getId());
        PageRequest strona = PageRequest.of(0, Math.min(Math.max(limit, 1), MAKSIMUM));

        List<ClanMessage> wiadomosci;
        if (after != null) {
            wiadomosci = new ArrayList<>(messages.after(clanId, after, ukryci, strona));
        } else {
            wiadomosci = new ArrayList<>(before != null
                ? messages.before(clanId, before, ukryci, strona)
                : messages.latest(clanId, ukryci, strona));
            Collections.reverse(wiadomosci);
        }
        if (wiadomosci.isEmpty()) {
            return List.of();
        }

        // Reakcje pod wszystkimi wiadomosciami tej strony - jednym zapytaniem
        long od = wiadomosci.stream().mapToLong(ClanMessage::getId).min().orElse(0);
        long doNumeru = wiadomosci.stream().mapToLong(ClanMessage::getId).max().orElse(0);
        Map<Long, List<ClanReactionCount>> reakcje = groupReactions(
            reactions.summaries(clanId, od, viewer.getId(), ukryci).stream()
                .filter(r -> r.getMessageId() <= doNumeru).toList());
        Collection<Long> ukryciSet = ukryci;
        return wiadomosci.stream()
            .map(m -> toResponse(m, viewer, clan, reakcje.getOrDefault(m.getId(), List.of()), ukryciSet))
            .toList();
    }

    @Transactional
    public ClanMessageResponse send(Long clanId, String username, String content, Long replyToId) {
        Clan clan = clans.requireMember(username, clanId);
        User sender = user(username);
        if (sender.isBanned(BanKind.MESSAGING)) {
            throw OperationNotAllowedException.banned(BanKind.MESSAGING, sender.bannedUntil(BanKind.MESSAGING));
        }
        List<Long> ukryci = blocks.hiddenForQuery(sender.getId());
        ClanMessage replyTo = null;
        if (replyToId != null) {
            // Odpowiedziec mozna na wiadomosc z tego samego klanu, ktora nadawca widzi (nie od osoby z blokady)
            replyTo = messages.findById(replyToId)
                .filter(m -> m.getClan().getId().equals(clanId) && !ukryci.contains(m.getSender().getId()))
                .orElseThrow(() -> new NoSuchElementFoundException("clan message", replyToId));
        }
        ClanMessage message = messages.save(new ClanMessage(clan, sender, content.strip(), replyTo));

        // Kto pisze, ten widzial rozmowe - jego wlasne wiadomosci nie zostawiaja mu nieprzeczytanych
        members.findByUserId(sender.getId()).ifPresent(m -> m.markChatRead(message.getId()));
        powiadom(clan, sender, message);

        return toResponse(message, sender, clan, List.of(), ukryci);
    }

    /**
     * Powiadomienie na telefon dla czlonkow, ktorzy jeszcze niczego na tym czacie nie mieli
     * nieprzeczytanego (i nie wyciszyli klanu, i nie ma miedzy nimi a piszacym blokady).
     * Tresci wiadomosci nie ma w powiadomieniu - ekran blokady nie jest dla klanu.
     */
    private void powiadom(Clan clan, User sender, ClanMessage message) {
        for (ClanMember m : members.toNotify(clan.getId(), sender.getId(), message.getId())) {
            if (blocks.eitherWay(sender.getId(), m.getUser().getId())) {
                continue;
            }
            push.send(m.getUser().getId(), new PushService.Message(
                "push.clanChat.title", new Object[] {clan.getName()},
                "push.clanChat.body", new Object[] {sender.getUsername()},
                "/klan", "clan-chat-" + clan.getId()));
        }
    }

    /** Usuwa autor wiadomosci, zalozyciel i administratorzy klanu oraz administrator aplikacji. */
    @Transactional
    public void delete(Long clanId, Long messageId, String username) {
        User user = user(username);
        Clan clan = clans.requireAccess(user, clanId);
        ClanMessage message = messages.findById(messageId)
            .filter(m -> m.getClan().getId().equals(clanId))
            .orElseThrow(() -> new NoSuchElementFoundException("clan message", messageId));
        if (!canDelete(message, user, clan)) {
            throw OperationNotAllowedException.clanNotManager();
        }
        // Reakcje wprost - nie liczymy na kaskade bazy, gdy w tej samej sesji sa juz zapisane
        reactions.deleteByMessageId(messageId);
        messages.delete(message);
    }

    /* ------------------------------------------------------------------ */
    /*  Przeczytane, nieprzeczytane i wyciszenie                           */
    /* ------------------------------------------------------------------ */

    /** "Przeczytalem do tej wiadomosci wlacznie" - tylko czlonek; nie cofa sie i nie wybiega poza czat. */
    @Transactional
    public void markRead(Long clanId, String username, long upTo) {
        User user = user(username);
        Clan clan = clans.requireAccess(user, clanId);
        if (!clan.hasMember(user.getId())) {
            return; // administrator aplikacji czyta bez znacznika - nie jest czlonkiem
        }
        membership(clanId, user).markChatRead(Math.min(upTo, messages.maxId(clanId)));
    }

    /** Wyciszenie powiadomien z czatu tego klanu (licznik nadal dziala). */
    @Transactional
    public void setMuted(Long clanId, String username, boolean muted) {
        User user = user(username);
        clans.requireMember(username, clanId);
        membership(clanId, user).setChatMuted(muted);
    }

    /** Licznik przy pozycji menu: ile nieprzeczytanych w moim klanie. */
    @Transactional(readOnly = true)
    public ClanUnreadResponse unread(String username) {
        User user = user(username);
        return members.findByUserId(user.getId())
            .map(m -> new ClanUnreadResponse(m.getClan().getId(), unreadFor(m, user)))
            .orElse(new ClanUnreadResponse(null, 0));
    }

    /** Ile wiadomosci od innych osob (bez osob z blokad) jest nowszych niz przeczytana. */
    long unreadFor(ClanMember member, User user) {
        long przeczytane = member.getChatReadId() == null ? 0 : member.getChatReadId();
        return messages.unread(member.getClan().getId(), przeczytane, user.getId(),
            blocks.hiddenForQuery(user.getId()));
    }

    private ClanMember membership(Long clanId, User user) {
        return members.findByUserId(user.getId())
            .filter(m -> m.getClan().getId().equals(clanId))
            .orElseThrow(OperationNotAllowedException::clanNotMember);
    }

    /* ------------------------------------------------------------------ */
    /*  Reakcje                                                            */
    /* ------------------------------------------------------------------ */

    /** Moja reakcja na wiadomosc - ta sama osoba ma jedna; inne emoji podmienia poprzednie. */
    @Transactional
    public List<ClanReactionCount> react(Long clanId, Long messageId, String username, ClanEmoji emoji) {
        User user = user(username);
        clans.requireMember(username, clanId);
        ClanMessage message = visibleMessage(clanId, messageId, user);
        reactions.findMine(messageId, user.getId()).ifPresentOrElse(
            r -> r.setType(emoji),
            () -> reactions.save(new ClanMessageReaction(message, user, emoji, LocalDateTime.now(clock))));
        reactions.flush();
        return summary(messageId, user);
    }

    @Transactional
    public List<ClanReactionCount> unreact(Long clanId, Long messageId, String username) {
        User user = user(username);
        clans.requireMember(username, clanId);
        visibleMessage(clanId, messageId, user);
        reactions.findMine(messageId, user.getId()).ifPresent(reactions::delete);
        reactions.flush();
        return summary(messageId, user);
    }

    /**
     * Reakcje pod wiadomosciami od {@code since} wzwyz - do odswiezania przy odpytywaniu czatu.
     * Wiadomosc bez reakcji po prostu nie wystepuje w odpowiedzi.
     */
    @Transactional(readOnly = true)
    public List<ClanMessageReactions> reactionsSince(Long clanId, String viewerName, long since) {
        User viewer = user(viewerName);
        Clan clan = clans.requireAccess(viewer, clanId);
        List<Long> ukryci = viewer.getRole() == Role.ADMIN && !clan.hasMember(viewer.getId())
            ? List.of(-1L) : blocks.hiddenForQuery(viewer.getId());
        Map<Long, List<ClanReactionCount>> zebrane = groupReactions(
            reactions.summaries(clanId, since, viewer.getId(), ukryci));
        return zebrane.entrySet().stream().map(e -> new ClanMessageReactions(e.getKey(), e.getValue())).toList();
    }

    private List<ClanReactionCount> summary(Long messageId, User viewer) {
        List<Long> ukryci = blocks.hiddenForQuery(viewer.getId());
        return groupReactions(reactions.summaryOf(messageId, viewer.getId(), ukryci))
            .getOrDefault(messageId, List.of());
    }

    private ClanMessage visibleMessage(Long clanId, Long messageId, User viewer) {
        List<Long> ukryci = blocks.hiddenForQuery(viewer.getId());
        return messages.findById(messageId)
            .filter(m -> m.getClan().getId().equals(clanId) && !ukryci.contains(m.getSender().getId()))
            .orElseThrow(() -> new NoSuchElementFoundException("clan message", messageId));
    }

    /** Wiersze zapytania -> po wiadomosci, w kolejnosci emoji z listy (stala, taka sama dla wszystkich). */
    private static Map<Long, List<ClanReactionCount>> groupReactions(List<ClanReactionRow> rows) {
        Map<Long, Map<ClanEmoji, ClanReactionCount>> wg = new LinkedHashMap<>();
        for (ClanReactionRow r : rows) {
            wg.computeIfAbsent(r.getMessageId(), k -> new EnumMap<>(ClanEmoji.class))
                .put(r.getType(), new ClanReactionCount(r.getType(), r.getTotal(), r.getMine() != null && r.getMine() > 0));
        }
        Map<Long, List<ClanReactionCount>> wynik = new HashMap<>();
        wg.forEach((id, mapa) -> wynik.put(id, List.copyOf(mapa.values())));
        return wynik;
    }

    /* ------------------------------------------------------------------ */
    /*  Odpowiedzi                                                         */
    /* ------------------------------------------------------------------ */

    private boolean canDelete(ClanMessage message, User viewer, Clan clan) {
        return message.getSender().getId().equals(viewer.getId())
            || viewer.getRole() == Role.ADMIN
            || clans.canModerate(viewer, clan);
    }

    private ClanMessageResponse toResponse(ClanMessage m, User viewer, Clan clan,
                                           List<ClanReactionCount> reakcje, Collection<Long> ukryci) {
        ClanMessage cel = m.getReplyTo();
        ClanReplyPreview podglad = null;
        if (cel != null && !ukryci.contains(cel.getSender().getId())) {
            String tresc = cel.getContent();
            podglad = new ClanReplyPreview(cel.getId(), cel.getSender().getUsername(),
                tresc.length() > SKROT ? tresc.substring(0, SKROT - 1) + "…" : tresc);
        }
        return new ClanMessageResponse(m.getId(), m.getSender().getUsername(),
            ClanMapper.avatarUrl(m.getSender()), m.getContent(), m.getCreatedAt(),
            m.getSender().getId().equals(viewer.getId()), canDelete(m, viewer, clan),
            cel == null ? null : cel.getId(), podglad, reakcje);
    }

    private User user(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
