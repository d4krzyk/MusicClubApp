package com.musicclubapp.service;

import com.musicclubapp.dto.MeetingRequest;
import com.musicclubapp.entity.Meeting;
import com.musicclubapp.entity.MeetingAttendee;
import com.musicclubapp.entity.MeetingStatus;
import com.musicclubapp.entity.User;
import com.musicclubapp.repository.MeetingAttendeeRepository;
import com.musicclubapp.repository.MeetingRepository;
import com.musicclubapp.repository.NotificationRepository;
import com.musicclubapp.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Przypomnienia o spotkaniach wolane tak, jak woła je harmonogram ({@code scheduled()}), bez transakcji testu: "wyslane"
 * musi trafic do bazy, inaczej kazda minuta wysylalaby to samo przypomnienie od nowa.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Spotkania - przypomnienia z harmonogramu")
class MeetingReminderSchedulingTest {

    @Autowired private UserRepository users;
    @Autowired private MeetingRepository meetings;
    @Autowired private MeetingAttendeeRepository attendees;
    @Autowired private NotificationRepository notifications;
    @Autowired private MeetingService meetingService;
    @Autowired private MeetingReminderService reminders;
    @Autowired private PlatformTransactionManager tx;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private PushService push;

    private final List<Long> konta = new ArrayList<>();

    @AfterEach
    void tearDown() {
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            konta.forEach(id -> {
                meetings.deleteAllOf(id);
                notifications.deleteByUserId(id);
            });
            users.findAllById(konta).forEach(u -> {
                u.getFriends().clear();
                users.save(u);
            });
        });
        new TransactionTemplate(tx).executeWithoutResult(s -> konta.forEach(users::deleteById));
    }

    @Test
    @DisplayName("scheduled(): przypomnienie raz - 'wyslane' zapisane w bazie, nastepna minuta niczego nie wysyla")
    void scheduledRunPersists() {
        Long[] ids = new TransactionTemplate(tx).execute(s -> {
            User ala = users.save(new User("mh_ala", "mh_ala@example.com", "x"));
            User bob = users.save(new User("mh_bob", "mh_bob@example.com", "x"));
            ala.addFriend(bob);
            users.save(ala);
            konta.add(ala.getId());
            konta.add(bob.getId());
            Instant start = Instant.now().plus(Duration.ofMinutes(40)).truncatedTo(ChronoUnit.MINUTES);
            Meeting m = meetingService.create(ala, bob, null, new MeetingRequest("Hala", null, null, null,
                start, start.plus(Duration.ofHours(1)), 30));
            attendees.save(new MeetingAttendee(m, bob, MeetingStatus.GOING, Instant.now()));
            return new Long[] {m.getId(), ala.getId(), bob.getId()};
        });
        // Pora przypomnienia minela (bez czekania 10 minut)
        jdbc.update("UPDATE meetings SET remind_at = ? WHERE id = ?",
            java.sql.Timestamp.from(Instant.now().minusSeconds(60)), ids[0]);

        reminders.scheduled();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meeting_attendees WHERE meeting_id = ? AND reminded_at IS NOT NULL",
            Integer.class, ids[0])).isEqualTo(2);
        reminders.scheduled();
        verify(push, times(1)).send(eq(ids[1]), any());
        verify(push, times(1)).send(eq(ids[2]), any());
    }
}
