package com.musicclubapp.service;

import com.musicclubapp.dto.ClanResponse;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.ClanJoinPolicy;
import com.musicclubapp.entity.ClanJoinRequest;
import com.musicclubapp.entity.ClanMember;
import com.musicclubapp.entity.ClanRole;
import com.musicclubapp.entity.InvitationStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.ClanInvitationRepository;
import com.musicclubapp.repository.ClanJoinRequestRepository;
import com.musicclubapp.repository.ClanMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Prosby o dolaczenie do klanu. To nadal klan decyduje, kto do niego wchodzi: prosbe rozpatruje
 * zarzad (zalozyciel i administratorzy klanu), a kazda osoba moze miec najwyzej
 * {@value #MAX_OCZEKUJACYCH} oczekujacych prosb naraz.
 *
 * <p>Odmowa zostaje jako DECLINED na tydzien (zeby nikt nie naciskal) - klan nie podaje powodu.
 * Blokada miedzy proszacym a zalozycielem daje te sama odpowiedz co klan, ktory prosb nie przyjmuje.</p>
 */
@Service
public class ClanRequestService {

    private static final Logger log = LoggerFactory.getLogger(ClanRequestService.class);

    static final int MAX_OCZEKUJACYCH = 5;

    private final ClanService clans;
    private final ClanMemberRepository members;
    private final ClanInvitationRepository invitations;
    private final ClanJoinRequestRepository requests;
    private final BlockService blocks;
    private final NotificationService notifications;
    private final Clock clock;

    public ClanRequestService(ClanService clans, ClanMemberRepository members, ClanInvitationRepository invitations,
                              ClanJoinRequestRepository requests, BlockService blocks,
                              NotificationService notifications, Clock clock) {
        this.clans = clans;
        this.members = members;
        this.invitations = invitations;
        this.requests = requests;
        this.blocks = blocks;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Prosba o dolaczenie, z opcjonalnym wstepem. Zwraca strone klanu widziana przez proszacego. */
    @Transactional
    public ClanResponse request(Long clanId, String username, String message) {
        User user = clans.user(username);
        Clan clan = clans.clan(clanId);
        if (members.findByUserId(user.getId()).isPresent()) {
            throw OperationNotAllowedException.clanAlreadyMember();
        }
        // Wybor klanu i blokada daja ten sam komunikat - proszacy nie dowiaduje sie, ktore z nich
        List<ClanMember> zarzad = members.managersOf(clanId);
        boolean zablokowany = zarzad.stream().anyMatch(m -> m.getRole() == ClanRole.FOUNDER
            && blocks.eitherWay(user.getId(), m.getUser().getId()));
        if (clan.getJoinPolicy() != ClanJoinPolicy.REQUESTS || zablokowany) {
            throw OperationNotAllowedException.clanNoRequests();
        }
        if (members.countByClanId(clanId) >= Clan.MAX_MEMBERS) {
            throw OperationNotAllowedException.clanFull(Clan.MAX_MEMBERS);
        }
        if (invitations.findByClanIdAndInviteeId(clanId, user.getId())
            .filter(i -> i.getStatus() == InvitationStatus.PENDING).isPresent()) {
            throw OperationNotAllowedException.clanInvitedAlready();
        }

        LocalDateTime teraz = LocalDateTime.now(clock);
        String wstep = message == null || message.isBlank() ? null : message.strip();
        Optional<ClanJoinRequest> istniejaca = requests.findByClanIdAndUserId(clanId, user.getId());
        if (istniejaca.isPresent()) {
            ClanJoinRequest r = istniejaca.get();
            if (r.getStatus() == InvitationStatus.PENDING) {
                throw OperationNotAllowedException.clanRequestAlready();
            }
            if (r.getAnsweredAt() != null && r.getAnsweredAt().plus(ClanService.PROSBA_PO_ODMOWIE).isAfter(teraz)) {
                throw OperationNotAllowedException.clanNoRequests();
            }
        }
        if (requests.countByUserIdAndStatus(user.getId(), InvitationStatus.PENDING) >= MAX_OCZEKUJACYCH) {
            throw OperationNotAllowedException.clanTooManyRequests(MAX_OCZEKUJACYCH);
        }
        if (istniejaca.isPresent()) {
            istniejaca.get().renew(wstep, teraz);
        } else {
            requests.save(new ClanJoinRequest(clan, user, wstep, teraz));
        }

        // Dostaja ci z zarzadu, z ktorymi proszacy nie ma blokady
        List<User> odbiorcy = zarzad.stream().map(ClanMember::getUser)
            .filter(m -> !blocks.eitherWay(user.getId(), m.getId())).toList();
        notifications.clanJoinRequested(odbiorcy, user, clan);
        return clans.get(clanId, username);
    }

    /** Cofniecie oczekujacej prosby. Odrzuconej cofnac sie nie da - tydzien karencji ma sens tylko wtedy. */
    @Transactional
    public ClanResponse withdraw(Long clanId, String username) {
        User user = clans.user(username);
        ClanJoinRequest r = requests.findByClanIdAndUserId(clanId, user.getId())
            .filter(x -> x.getStatus() == InvitationStatus.PENDING)
            .orElseThrow(() -> new NoSuchElementFoundException("clan join request", clanId));
        notifications.clanRequestGone(user.getId(), clanId);
        requests.delete(r);
        return clans.get(clanId, username);
    }

    /** Przyjecie prosby przez zarzad: osoba wchodzi do klanu i dostaje powiadomienie. */
    @Transactional
    public ClanResponse accept(Long clanId, Long requestId, String username) {
        User manager = clans.user(username);
        Clan clan = clans.clan(clanId);
        ClanJoinRequest r = visibleRequest(clan, requestId, manager);
        User target = r.getUser();
        if (members.findByUserId(target.getId()).isPresent()) {
            // Ktos zdazyl dolaczyc inna droga - prosba jest juz bez sensu
            notifications.clanRequestGone(target.getId(), clanId);
            requests.delete(r);
            throw OperationNotAllowedException.clanInviteeInClan();
        }
        if (members.countByClanId(clanId) >= Clan.MAX_MEMBERS) {
            throw OperationNotAllowedException.clanFull(Clan.MAX_MEMBERS);
        }
        // Dolaczenie kasuje tez ta prosbe i dzwonki zarzadu o niej
        clans.enroll(clan, target);
        notifications.clanRequestAccepted(target, clan);
        log.info("Klan {} (#{}) przyjal prosbe o dolaczenie od {}", clan.getName(), clanId, target.getUsername());
        return clans.get(clanId, username);
    }

    /** Odrzucenie: zostaje slad na tydzien, bez powiadomienia i bez podawania powodu. */
    @Transactional
    public ClanResponse decline(Long clanId, Long requestId, String username) {
        User manager = clans.user(username);
        Clan clan = clans.clan(clanId);
        ClanJoinRequest r = visibleRequest(clan, requestId, manager);
        r.decline(LocalDateTime.now(clock));
        notifications.clanRequestGone(r.getUser().getId(), clanId);
        return clans.get(clanId, username);
    }

    /** Oczekujaca prosba do tego klanu, ktora ta osoba z zarzadu moze rozpatrzyc (bez blokady z proszacym). */
    private ClanJoinRequest visibleRequest(Clan clan, Long requestId, User manager) {
        ClanRole rola = clan.roleOf(manager.getId());
        if (rola == null || !rola.manages()) {
            throw OperationNotAllowedException.clanNotManager();
        }
        return requests.findFull(requestId)
            .filter(r -> r.getClan().getId().equals(clan.getId()))
            .filter(r -> r.getStatus() == InvitationStatus.PENDING)
            .filter(r -> !blocks.eitherWay(manager.getId(), r.getUser().getId()))
            .orElseThrow(() -> new NoSuchElementFoundException("clan join request", requestId));
    }
}
