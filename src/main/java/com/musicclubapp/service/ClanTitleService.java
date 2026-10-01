package com.musicclubapp.service;

import com.musicclubapp.dto.ClanResponse;
import com.musicclubapp.dto.ClanTitleRequest;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanMemberTitle;
import com.musicclubapp.entity.ClanRole;
import com.musicclubapp.entity.ClanTitle;
import com.musicclubapp.entity.ClanTitleMode;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.ClanMemberTitleRepository;
import com.musicclubapp.repository.ClanTitleRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Tytuly (role) w klanie: definiuje je zarzad, a dostaje sie je na trzy sposoby - nadane przez
 * zarzad (MANUAL), wziete samemu (SELF) albo z progu aktywnosci (AUTO, patrz {@link ClanTitleEngine}).
 * Tytul niczego nie daje poza ozdoba i informacja - uprawnienia zostaja przy rolach zalozyciela
 * i administratora klanu.
 */
@Service
public class ClanTitleService {

    private static final Pattern NAZWA = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} ._'&-]*");

    private final ClanService clans;
    private final ClanTitleRepository titles;
    private final ClanMemberTitleRepository memberTitles;
    private final UserRepository users;
    private final Clock clock;

    public ClanTitleService(ClanService clans, ClanTitleRepository titles, ClanMemberTitleRepository memberTitles,
                            UserRepository users, Clock clock) {
        this.clans = clans;
        this.titles = titles;
        this.memberTitles = memberTitles;
        this.users = users;
        this.clock = clock;
    }

    /* --- definicje (zarzad) --- */

    @Transactional
    public ClanResponse create(Long clanId, String username, ClanTitleRequest request) {
        Clan clan = manage(clanId, username);
        String name = checkName(request.name());
        String key = NameKeys.of(name);
        if (titles.countByClanId(clanId) >= ClanTitle.MAX_PER_CLAN) {
            throw OperationNotAllowedException.clanTitleLimit(ClanTitle.MAX_PER_CLAN);
        }
        if (titles.existsByClanIdAndNameKey(clanId, key)) {
            throw OperationNotAllowedException.clanTitleTaken();
        }
        checkRule(request);
        titles.save(new ClanTitle(clan, name, key, request.color(), request.mode(), request.metric(),
            request.threshold(), LocalDateTime.now(clock)));
        return clans.get(clanId, username);
    }

    @Transactional
    public ClanResponse update(Long clanId, Long titleId, String username, ClanTitleRequest request) {
        manage(clanId, username);
        ClanTitle title = title(clanId, titleId);
        String name = checkName(request.name());
        String key = NameKeys.of(name);
        if (titles.existsByClanIdAndNameKeyAndIdNot(clanId, key, titleId)) {
            throw OperationNotAllowedException.clanTitleTaken();
        }
        checkRule(request);
        // Tytul automatyczny nie ma wierszy z wlascicielami - przejscie na niego zdejmuje nadane i wziete
        if (request.mode() == ClanTitleMode.AUTO && title.getMode() != ClanTitleMode.AUTO) {
            memberTitles.deleteByTitleId(titleId);
        }
        title.edit(name, key, request.color(), request.mode(), request.metric(), request.threshold());
        return clans.get(clanId, username);
    }

    @Transactional
    public ClanResponse delete(Long clanId, Long titleId, String username) {
        manage(clanId, username);
        ClanTitle title = title(clanId, titleId);
        memberTitles.deleteByTitleId(titleId);
        titles.delete(title);
        titles.flush();
        return clans.get(clanId, username);
    }

    /* --- nadawanie przez zarzad --- */

    @Transactional
    public ClanResponse assign(Long clanId, Long titleId, String actorName, String targetName) {
        manage(clanId, actorName);
        ClanTitle title = title(clanId, titleId);
        if (title.getMode() == ClanTitleMode.AUTO) {
            throw OperationNotAllowedException.clanTitleNotAssignable();
        }
        User target = member(clanId, targetName);
        if (memberTitles.find(titleId, target.getId()).isEmpty()) {
            if (memberTitles.countOf(clanId, target.getId()) >= ClanTitleEngine.MAX_PER_MEMBER) {
                throw OperationNotAllowedException.clanTitleMemberLimit(ClanTitleEngine.MAX_PER_MEMBER);
            }
            memberTitles.save(new ClanMemberTitle(title, target, false, LocalDateTime.now(clock)));
        }
        return clans.get(clanId, actorName);
    }

    /** Zdjecie tytulu: zarzad zdejmuje kazdy; osoba - swoj, o ile jest do wziecia samemu. */
    @Transactional
    public ClanResponse unassign(Long clanId, Long titleId, String actorName, String targetName) {
        User actor = clans.user(actorName);
        Clan clan = clans.clan(clanId);
        ClanTitle title = title(clanId, titleId);
        User target = member(clanId, targetName);
        ClanRole rola = clan.roleOf(actor.getId());
        boolean zarzad = rola != null && rola.manages();
        boolean swoj = actor.getId().equals(target.getId()) && title.getMode() == ClanTitleMode.SELF;
        if (!zarzad && !swoj) {
            throw OperationNotAllowedException.clanNotManager();
        }
        memberTitles.find(titleId, target.getId()).ifPresent(memberTitles::delete);
        return clans.get(clanId, actorName);
    }

    /* --- branie samemu --- */

    @Transactional
    public ClanResponse claim(Long clanId, Long titleId, String username) {
        User user = users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
        Clan clan = clans.requireMember(username, clanId);
        ClanTitle title = title(clan.getId(), titleId);
        if (title.getMode() != ClanTitleMode.SELF) {
            throw OperationNotAllowedException.clanTitleNotSelf();
        }
        if (memberTitles.find(titleId, user.getId()).isEmpty()) {
            if (memberTitles.countSelfClaimed(clanId, user.getId()) >= ClanTitleEngine.MAX_SELF) {
                throw OperationNotAllowedException.clanTitleSelfLimit(ClanTitleEngine.MAX_SELF);
            }
            if (memberTitles.countOf(clanId, user.getId()) >= ClanTitleEngine.MAX_PER_MEMBER) {
                throw OperationNotAllowedException.clanTitleMemberLimit(ClanTitleEngine.MAX_PER_MEMBER);
            }
            memberTitles.save(new ClanMemberTitle(title, user, true, LocalDateTime.now(clock)));
        }
        return clans.get(clanId, username);
    }

    @Transactional
    public ClanResponse unclaim(Long clanId, Long titleId, String username) {
        return unassign(clanId, titleId, username, username);
    }

    /* --- pomocnicze --- */

    /** Klan, ktorym ta osoba zarzadza (zalozyciel albo administrator klanu). */
    private Clan manage(Long clanId, String username) {
        User user = clans.user(username);
        Clan clan = clans.clan(clanId);
        if (!clans.canModerate(user, clan)) {
            throw OperationNotAllowedException.clanNotManager();
        }
        return clan;
    }

    private ClanTitle title(Long clanId, Long titleId) {
        return titles.findById(titleId)
            .filter(t -> t.getClan().getId().equals(clanId))
            .orElseThrow(() -> new NoSuchElementFoundException("clan title", titleId));
    }

    private User member(Long clanId, String username) {
        User user = users.findByUsername(username == null ? "" : username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
        if (!clans.clan(clanId).hasMember(user.getId())) {
            throw new NoSuchElementFoundException("clan member", username);
        }
        return user;
    }

    private static String checkName(String raw) {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (name.length() < ClanTitle.NAME_MIN || name.length() > ClanTitle.NAME_MAX
            || !NAZWA.matcher(name).matches() || NameKeys.of(name).isBlank()) {
            throw OperationNotAllowedException.clanTitleInvalid();
        }
        return name;
    }

    private static void checkRule(ClanTitleRequest request) {
        if (request.mode() == ClanTitleMode.AUTO && (request.metric() == null || request.threshold() == null)) {
            throw OperationNotAllowedException.clanTitleAutoRule();
        }
    }
}
