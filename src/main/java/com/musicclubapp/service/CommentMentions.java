package com.musicclubapp.service;

import com.musicclubapp.entity.Comment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wyszukiwanie oznaczen (@login) w tresci komentarza. Login ma te same znaki co przy rejestracji
 * (litery, cyfry, _ . -, 3-50 znakow); malpa musi stac na poczatku albo po znaku, ktory nie jest czescia
 * loginu - "ala@example.com" nie oznacza nikogo. Kropka lub myslnik na koncu to interpunkcja
 * ("dzieki @ala."), a nie koniec loginu.
 */
final class CommentMentions {

    private static final Pattern OZNACZENIE = Pattern.compile("(?<![A-Za-z0-9_.@-])@([A-Za-z0-9_.-]{3,50})");

    private CommentMentions() {
    }

    /** Loginy w kolejnosci pierwszego wystapienia, bez powtorzen, najwyzej {@link Comment#MAX_MENTIONS}. */
    static List<String> logins(String tresc) {
        if (tresc == null || tresc.isEmpty()) {
            return List.of();
        }
        Set<String> wynik = new LinkedHashSet<>();
        Matcher m = OZNACZENIE.matcher(tresc);
        while (m.find() && wynik.size() < Comment.MAX_MENTIONS) {
            String login = m.group(1);
            // "@ala." i "@ala-" - kropka i myslnik na koncu to znak zdania
            while (!login.isEmpty() && (login.endsWith(".") || login.endsWith("-"))) {
                login = login.substring(0, login.length() - 1);
            }
            if (login.length() >= 3) {
                wynik.add(login);
            }
        }
        return new ArrayList<>(wynik);
    }
}
