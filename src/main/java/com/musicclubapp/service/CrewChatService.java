package com.musicclubapp.service;

import com.musicclubapp.dto.CrewMessageResponse;
import com.musicclubapp.dto.MeetingRequest;
import com.musicclubapp.dto.MeetingResponse;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.Crew;
import com.musicclubapp.entity.CrewMember;
import com.musicclubapp.entity.CrewMessage;
import com.musicclubapp.entity.CrewRole;
import com.musicclubapp.entity.Meeting;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.CrewMemberRepository;
import com.musicclubapp.repository.CrewMessageRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Czat ekipy - jak czat klanu, tylko dla kilku osob jadacych na jeden koncert: tekst i spotkania (miejsce zbiorki).
 * Odpytywanie ({@code after} / {@code before}), usuwanie zostawia slad, zmiany (usuniete, spotkania) przez
 * {@code /changes}, push dla tych, ktorzy nie maja jeszcze nic nieprzeczytanego (bez tresci). Widza i pisza tylko
 * czlonkowie; dwa dni po koncercie czat jest juz tylko do czytania.
 */
@Service
public class CrewChatService {

    public static final int DOMYSLNIE = 40;
    public static final int MAKSIMUM = 100;
    static final Duration ZAPAS = Duration.ofSeconds(30);

    private final CrewService crews;
    private final CrewMessageRepository messages;
    private final CrewMemberRepository members;
    private final UserRepository users;
    private final BlockService blocks;
    private final MeetingService meetings;
    private final PushService push;
    private final Clock clock;

    public CrewChatService(CrewService crews, CrewMessageRepository messages, CrewMemberRepository members,
                           UserRepository users, BlockService blocks, MeetingService meetings, PushService push,
                           Clock clock) {
        this.crews = crews;
        this.messages = messages;
        this.members = members;
        this.users = users;
        this.blocks = blocks;
        this.meetings = meetings;
        this.push = push;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CrewMessageResponse> list(Long crewId, String username, Long after, Long before, int limit) {
        User viewer = user(username);
        CrewMember ja = crews.requireMember(crewId, viewer);
        List<Long> ukryci = blocks.hiddenForQuery(viewer.getId());
        PageRequest strona = PageRequest.of(0, Math.min(Math.max(limit, 1), MAKSIMUM));
        List<CrewMessage> lista;
        if (after != null) {
            lista = new ArrayList<>(messages.after(crewId, after, ukryci, strona));
        } else {
            lista = new ArrayList<>(before != null ? messages.before(crewId, before, ukryci, strona)
                : messages.latest(crewId, ukryci, strona));
            Collections.reverse(lista);
        }
        boolean pisze = crews.chatOpen(ja.getCrew().getEvent());
        Map<Long, MeetingResponse> spotkania = meetings.toResponses(
            lista.stream().map(CrewMessage::getMeeting).filter(Objects::nonNull).distinct().toList(), viewer, pisze);
        return lista.stream().map(m -> toResponse(m, viewer, ja,
            m.getMeeting() == null ? null : spotkania.get(m.getMeeting().getId()))).toList();
    }

    @Transactional
    public CrewMessageResponse send(Long crewId, String username, String content) {
        User sender = user(username);
        CrewMember ja = writable(crewId, sender);
        CrewMessage m = messages.save(new CrewMessage(ja.getCrew(), sender, content.strip()));
        ja.markChatRead(m.getId());
        powiadom(ja.getCrew(), sender, m);
        return toResponse(m, sender, ja, null);
    }

    /** Spotkanie na czacie ekipy (miejsce zbiorki, godzina, przypomnienie). */
    @Transactional
    public CrewMessageResponse sendMeeting(Long crewId, String username, MeetingRequest request) {
        User sender = user(username);
        CrewMember ja = writable(crewId, sender);
        Meeting spotkanie = meetings.create(sender, ja.getCrew(), request);
        CrewMessage m = new CrewMessage(ja.getCrew(), sender, "");
        m.attachMeeting(spotkanie);
        m = messages.save(m);
        ja.markChatRead(m.getId());
        powiadom(ja.getCrew(), sender, m);
        return toResponse(m, sender, ja, meetings.toResponse(spotkanie, sender, true));
    }

    /** Usuwa autor albo zakladajacy ekipy; zostaje slad, spotkanie z wiadomosci znika. */
    @Transactional
    public void delete(Long crewId, Long messageId, String username) {
        User viewer = user(username);
        CrewMember ja = crews.requireMember(crewId, viewer);
        CrewMessage m = messages.findById(messageId)
            .filter(x -> x.getCrew().getId().equals(crewId) && !x.isDeleted())
            .orElseThrow(() -> new NoSuchElementFoundException("crew message", messageId));
        if (!canDelete(m, viewer, ja)) {
            throw OperationNotAllowedException.crewNotFounder();
        }
        Meeting spotkanie = m.getMeeting();
        m.delete(LocalDateTime.now(clock));
        if (spotkanie != null) {
            messages.flush();
            meetings.deleteWithMessage(spotkanie);
        }
    }

    /** Usuniete i zmienione spotkania od podanej chwili (czas serwera z poprzedniej odpowiedzi). */
    @Transactional(readOnly = true)
    public CrewChatChanges changes(Long crewId, String username, LocalDateTime since) {
        User viewer = user(username);
        CrewMember ja = crews.requireMember(crewId, viewer);
        LocalDateTime teraz = LocalDateTime.now(clock);
        if (since == null) {
            return new CrewChatChanges(List.of(), teraz, List.of());
        }
        LocalDateTime od = since.minus(ZAPAS);
        return new CrewChatChanges(messages.deletedSince(crewId, od), teraz,
            meetings.changedInCrew(crewId, viewer, od.atZone(clock.getZone()).toInstant(),
                crews.chatOpen(ja.getCrew().getEvent())));
    }

    public record CrewChatChanges(List<Long> deletedIds, LocalDateTime serverTime, List<MeetingResponse> meetings) {
    }

    /** "Przeczytalem do" - nie cofa sie i nie wybiega poza czat. */
    @Transactional
    public void markRead(Long crewId, String username, long upTo) {
        User viewer = user(username);
        CrewMember ja = crews.requireMember(crewId, viewer);
        ja.markChatRead(Math.min(upTo, messages.maxId(crewId)));
    }

    private CrewMember writable(Long crewId, User sender) {
        if (sender.isBanned(BanKind.MESSAGING)) {
            throw OperationNotAllowedException.banned(BanKind.MESSAGING, sender.bannedUntil(BanKind.MESSAGING));
        }
        CrewMember ja = crews.requireMember(crewId, sender);
        if (!crews.chatOpen(ja.getCrew().getEvent())) {
            throw OperationNotAllowedException.crewChatClosed();
        }
        return ja;
    }

    /** Push dla czlonkow bez nieprzeczytanych (jedno do czasu zajrzenia), bez tresci, bez osob z blokada. */
    private void powiadom(Crew crew, User sender, CrewMessage m) {
        for (CrewMember x : members.toNotify(crew.getId(), sender.getId(), m.getId())) {
            if (blocks.eitherWay(sender.getId(), x.getUser().getId())) {
                continue;
            }
            push.send(x.getUser().getId(), new PushService.Message(
                "push.crewChat.title", new Object[] {crew.getEvent().getName()},
                "push.crewChat.body", new Object[] {sender.getUsername()},
                "/ekipy/" + crew.getId(), "crew-chat-" + crew.getId()));
        }
    }

    private boolean canDelete(CrewMessage m, User viewer, CrewMember ja) {
        return !m.isDeleted() && (m.getSender().getId().equals(viewer.getId()) || ja.getRole() == CrewRole.FOUNDER);
    }

    private CrewMessageResponse toResponse(CrewMessage m, User viewer, CrewMember ja, MeetingResponse spotkanie) {
        User s = m.getSender();
        return new CrewMessageResponse(m.getId(), s.getUsername(),
            s.getAvatarFileName() == null ? null : PostMapper.UPLOADS_PATH + s.getAvatarFileName(),
            m.getContent(), m.getCreatedAt(), s.getId().equals(viewer.getId()), canDelete(m, viewer, ja), m.isDeleted(),
            spotkanie);
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
