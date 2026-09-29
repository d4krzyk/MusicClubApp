package com.musicclubapp.service;

import com.musicclubapp.dto.PostResponse;
import com.musicclubapp.dto.ReactionSummary;
import com.musicclubapp.entity.Post;
import com.musicclubapp.entity.PostVisibility;
import com.musicclubapp.entity.Reaction;
import com.musicclubapp.entity.ReactionType;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.ReactionCount;
import com.musicclubapp.repository.ReactionRepository;
import com.musicclubapp.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Testy jednostkowe reakcji - wymaganie nr 13. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReactionService - ogien, mid i meh")
class ReactionServiceTest {

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostMapper postMapper;

    @Mock
    private NotificationService notifications;

    /** Bez blokad - mock zwraca "nikt nikogo nie zablokowal". */
    @Mock
    private BlockService blocks;

    @InjectMocks
    private ReactionService reactionService;

    private User anna() {
        return new User("anna", "anna@example.com", "hash");
    }

    private Post post() {
        return new Post(new User("bartek", "bartek@example.com", "hash"), "tresc");
    }

    /** Wspolne przygotowanie dla testow, ktore koncza sie zwroceniem posta. */
    private void setUpPostAndUser() {
        given(postRepository.findByIdWithAuthor(5L)).willReturn(Optional.of(post()));
        given(userRepository.findByUsername("anna")).willReturn(Optional.of(anna()));
        given(postMapper.toResponse(any(Post.class), any(), any())).willReturn(
            new PostResponse(5L, "bartek", null, "tresc", List.of(),
                null, null, null, null, null, null, null,
                LocalDateTime.now(), false, false, ReactionSummary.empty(), PostVisibility.PUBLIC, false));
    }

    @Test
    @DisplayName("pierwsza reakcja tworzy nowy wpis")
    void firstReactionCreatesRow() {
        setUpPostAndUser();
        given(reactionRepository.find(5L, "anna")).willReturn(Optional.empty());

        reactionService.set(5L, "anna", ReactionType.FIRE);

        ArgumentCaptor<Reaction> zapisana = ArgumentCaptor.forClass(Reaction.class);
        verify(reactionRepository).save(zapisana.capture());
        assertThat(zapisana.getValue().getType()).isEqualTo(ReactionType.FIRE);
        assertThat(zapisana.getValue().getUser().getUsername()).isEqualTo("anna");
    }

    @Test
    @DisplayName("zmiana zdania PODMIENIA reakcje, zamiast dodawac druga")
    void changingMindDoesNotAddSecondRow() {
        setUpPostAndUser();
        Reaction existing = new Reaction(post(), anna(), ReactionType.MEH);
        given(reactionRepository.find(5L, "anna")).willReturn(Optional.of(existing));

        reactionService.set(5L, "anna", ReactionType.FIRE);

        assertThat(existing.getType()).isEqualTo(ReactionType.FIRE);
        // kluczowe: zaden NOWY wiersz nie powstaje
        verify(reactionRepository, never()).save(any(Reaction.class));
    }

    @Test
    @DisplayName("cofniecie reakcji kasuje wpis")
    void undoDeletesRow() {
        setUpPostAndUser();
        Reaction existing = new Reaction(post(), anna(), ReactionType.FIRE);
        given(reactionRepository.find(5L, "anna")).willReturn(Optional.of(existing));

        reactionService.revert(5L, "anna");

        verify(reactionRepository).delete(existing);
    }

    @Test
    @DisplayName("cofniecie nieistniejacej reakcji niczego nie psuje")
    void undoWithoutReactionIsSafe() {
        setUpPostAndUser();
        given(reactionRepository.find(5L, "anna")).willReturn(Optional.empty());

        reactionService.revert(5L, "anna");

        verify(reactionRepository, never()).delete(any(Reaction.class));
    }

    @Test
    @DisplayName("reakcja na nieistniejacy post konczy sie wyjatkiem 'nie znaleziono'")
    void unknownPostThrows() {
        given(postRepository.findByIdWithAuthor(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reactionService.set(999L, "anna", ReactionType.FIRE))
            .isInstanceOf(NoSuchElementFoundException.class);

        verify(reactionRepository, never()).save(any(Reaction.class));
    }

    @Test
    @DisplayName("podsumowanie uzupelnia zerami rodzaje, ktorych nikt nie wybral")
    void summaryFillsInZeros() {
        given(reactionRepository.countForPosts(anyCollection())).willReturn(List.of(
            new ReactionCount(5L, ReactionType.FIRE, 3L),
            new ReactionCount(5L, ReactionType.MEH, 1L)));
        given(reactionRepository.findOwn(anyCollection(), anyString())).willReturn(List.of());

        Map<Long, ReactionSummary> score = reactionService.summaries(List.of(5L), "anna");

        ReactionSummary summary = score.get(5L);
        assertThat(summary.counts()).containsEntry(ReactionType.FIRE, 3L);
        // MID nie padl ani razu, ale i tak musi byc w mapie - inaczej front sie wysypie
        assertThat(summary.counts()).containsEntry(ReactionType.MID, 0L);
        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.mine()).isNull();
    }

    @Test
    @DisplayName("post bez zadnych reakcji dostaje same zera, a nie pusta mape")
    void postWithoutReactions() {
        given(reactionRepository.countForPosts(anyCollection())).willReturn(List.of());
        given(reactionRepository.findOwn(anyCollection(), anyString())).willReturn(List.of());

        ReactionSummary summary =
            reactionService.summaries(List.of(7L), "anna").get(7L);

        assertThat(summary.total()).isZero();
        assertThat(summary.counts()).hasSize(ReactionType.values().length);
    }

    @Test
    @DisplayName("pusta lista postow nie generuje zadnego zapytania do bazy")
    void emptyListSkipsDatabase() {
        Map<Long, ReactionSummary> score = reactionService.summaries(List.of(), "anna");

        assertThat(score).isEmpty();
        verify(reactionRepository, never()).countForPosts(anyCollection());
    }
}
