package com.musicclubapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Oznaczenia w komentarzach - wyszukiwanie @login w tresci")
class CommentMentionsTest {

    @Test
    @DisplayName("zwykle oznaczenia, w kolejnosci i bez powtorzen")
    void plainMentions() {
        assertThat(CommentMentions.logins("@ala")).containsExactly("ala");
        assertThat(CommentMentions.logins("hej @ala i @bob_2, a potem znowu @ala")).containsExactly("ala", "bob_2");
        assertThat(CommentMentions.logins("(@ala) \n@bob")).containsExactly("ala", "bob");
    }

    @Test
    @DisplayName("kropka i myslnik na koncu to interpunkcja, w srodku - czesc loginu")
    void trailingPunctuation() {
        assertThat(CommentMentions.logins("dzieki @ala.")).containsExactly("ala");
        assertThat(CommentMentions.logins("dzieki @ala-")).containsExactly("ala");
        assertThat(CommentMentions.logins("@jan.kowalski ok")).containsExactly("jan.kowalski");
        assertThat(CommentMentions.logins("@a-b-c")).containsExactly("a-b-c");
    }

    @Test
    @DisplayName("adres e-mail i wielokrotne malpy nie oznaczaja nikogo")
    void emailsAndNoise() {
        assertThat(CommentMentions.logins("pisz na ala@example.com")).isEmpty();
        assertThat(CommentMentions.logins("mail@ala")).isEmpty();
        assertThat(CommentMentions.logins("@@ala")).isEmpty();
        assertThat(CommentMentions.logins("a@@b")).isEmpty();
    }

    @Test
    @DisplayName("login ma od 3 do 50 znakow z liter, cyfr, _ . -")
    void loginShape() {
        assertThat(CommentMentions.logins("@ab")).isEmpty();
        assertThat(CommentMentions.logins("@abc")).containsExactly("abc");
        assertThat(CommentMentions.logins("@" + "x".repeat(50))).containsExactly("x".repeat(50));
        // 51 znakow: bierzemy pierwsze 50 - login dluzszy niz 50 i tak nie istnieje, wiec nikogo nie oznaczy
        assertThat(CommentMentions.logins("@" + "x".repeat(51))).containsExactly("x".repeat(50));
        // polskie litery nie wchodza do loginu: "za" to za malo, a "abc" przed nimi wystarcza
        assertThat(CommentMentions.logins("@zażółć")).isEmpty();
        assertThat(CommentMentions.logins("@abcżółć")).containsExactly("abc");
        // "..a" przechodzi walidacje rejestracji, wiec jest loginem; kropki na koncu to juz interpunkcja
        assertThat(CommentMentions.logins("@..a")).containsExactly("..a");
        assertThat(CommentMentions.logins("@a..")).isEmpty();
    }

    @Test
    @DisplayName("najwyzej piec oznaczen; pusty i pusty tekst - pusta lista")
    void limitAndEmpty() {
        assertThat(CommentMentions.logins("@aaa @bbb @ccc @ddd @eee @fff @ggg"))
            .containsExactlyElementsOf(List.of("aaa", "bbb", "ccc", "ddd", "eee"));
        assertThat(CommentMentions.logins(null)).isEmpty();
        assertThat(CommentMentions.logins("")).isEmpty();
        assertThat(CommentMentions.logins("bez oznaczen")).isEmpty();
    }
}
