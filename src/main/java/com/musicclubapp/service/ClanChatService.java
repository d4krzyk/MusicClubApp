package com.musicclubapp.service;

import com.musicclubapp.dto.ClanMessageResponse;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanMessage;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.ClanMapper;
import com.musicclubapp.repository.ClanMessageRepository;
import com.musicclubapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Czat klanu - jedna wspolna rozmowa dla wszystkich czlonkow.
 *
 * <p>Dziala na tej samej zasadzie co czat miedzy znajomymi: przegladarka pyta co kilka sekund
 * o wiadomosci nowsze od ostatniej, ktora ma. Widza go czlonkowie i administrator aplikacji
 * (moderacja - patrz polityka prywatnosci). Wiadomosci osob z blokad ogladajacego sa pomijane.</p>
 */
@Service
public class ClanChatService {

    private static final Logger log = LoggerFactory.getLogger(ClanChatService.class);

    public static final int DOMYSLNIE = 40;
    public static final int MAKSIMUM = 100;

    private final ClanMessageRepository messages;
    private final UserRepository users;
    private final ClanService clans;
    private final BlockService blocks;

    public ClanChatService(ClanMessageRepository messages, UserRepository users, ClanService clans,
                           BlockService blocks) {
        this.messages = messages;
        this.users = users;
        this.clans = clans;
        this.blocks = blocks;
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
        return wiadomosci.stream().map(m -> toResponse(m, viewer, clan)).toList();
    }

    @Transactional
    public ClanMessageResponse send(Long clanId, String username, String content) {
        Clan clan = clans.requireMember(username, clanId);
        User sender = user(username);
        if (sender.isBanned(BanKind.MESSAGING)) {
            throw OperationNotAllowedException.banned(BanKind.MESSAGING, sender.bannedUntil(BanKind.MESSAGING));
        }
        ClanMessage message = messages.save(new ClanMessage(clan, sender, content.strip()));
        return toResponse(message, sender, clan);
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
        messages.delete(message);
    }

    private boolean canDelete(ClanMessage message, User viewer, Clan clan) {
        return message.getSender().getId().equals(viewer.getId())
            || viewer.getRole() == Role.ADMIN
            || clans.canModerate(viewer, clan);
    }

    private ClanMessageResponse toResponse(ClanMessage m, User viewer, Clan clan) {
        return new ClanMessageResponse(m.getId(), m.getSender().getUsername(),
            ClanMapper.avatarUrl(m.getSender()), m.getContent(), m.getCreatedAt(),
            m.getSender().getId().equals(viewer.getId()), canDelete(m, viewer, clan));
    }

    private User user(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
