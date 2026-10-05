package com.musicclubapp.music;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rozpoznaje adres wklejony przez uzytkownika. */
public final class MusicLinkParser {

    /**
     * Identyfikatory Spotify to 22 znaki Base62. (?:intl-\w+/)? obsluguje adresy z przedrostkiem
     * jezykowym.
     */
    private static final Pattern SPOTIFY_URL = Pattern.compile(
        "open\\.spotify\\.com/(?:intl-[\\w-]+/)?(track|album|artist|playlist)/([A-Za-z0-9]{22})");

    /** Postac "spotify:track:xxx" - kopiowana z aplikacji na komputer. */
    private static final Pattern SPOTIFY_URI = Pattern.compile(
        "spotify:(track|album|artist|playlist):([A-Za-z0-9]{22})");

    /** Nagranie w YouTube Music. */
    private static final Pattern YT_MUSIC_TRACK = Pattern.compile(
        "music\\.youtube\\.com/watch\\?(?:[^\\s]*&)?v=([\\w-]{11})");

    /** Playlista w YouTube Music - a takze KAZDY ALBUM. */
    private static final Pattern YT_MUSIC_PLAYLIST = Pattern.compile(
        "music\\.youtube\\.com/playlist\\?(?:[^\\s]*&)?list=([\\w-]{10,60})");

    /** Apple Music. */
    private static final Pattern APPLE = Pattern.compile(
        "music\\.apple\\.com/([a-z]{2})/(album|playlist|artist|song)/([^?\\s]+)(\\?i=(\\d+))?");

    /**
     * Film ze zwyklego YouTube'a: youtube.com/watch?v=, youtu.be/, /shorts/, /live/ (takze m. i www.). Przyjmujemy go
     * TYLKO jako link wklejony w tresc rozmowy ({@link #findInChat}) - w postach muzyka ma byc muzyka.
     */
    private static final Pattern YT_VIDEO = Pattern.compile(
        "(?:^|[^\\w.])(?:(?:www\\.|m\\.)?youtube\\.com/(?:watch\\?(?:[^\\s]*&)?v=|shorts/|live/)|youtu\\.be/)([\\w-]{11})(?![\\w-])");

    private MusicLinkParser() {
    }

    /**
     * Pierwszy rozpoznany link w tresci wiadomosci na czacie: wszystko to, co {@link #parse}, a do tego zwykly film
     * z YouTube'a (rozmowa to nie post - znajomi wymieniaja sie teledyskami). Odtwarzacz i podglad sa te same.
     */
    public static Optional<ParsedMusicLink> findInChat(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Optional<ParsedMusicLink> muzyka = parse(text);
        if (muzyka.isPresent()) {
            return muzyka;
        }
        Matcher film = YT_VIDEO.matcher(text);
        if (film.find()) {
            return Optional.of(new ParsedMusicLink(MusicProvider.YOUTUBE, MusicKind.TRACK, film.group(1)));
        }
        return Optional.empty();
    }

    public static Optional<ParsedMusicLink> parse(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String text = url.trim();

        Optional<ParsedMusicLink> spotify = spotify(text);
        if (spotify.isPresent()) {
            return spotify;
        }

        Matcher playlist = YT_MUSIC_PLAYLIST.matcher(text);
        if (playlist.find()) {
            return Optional.of(new ParsedMusicLink(
                MusicProvider.YOUTUBE, MusicKind.PLAYLIST, playlist.group(1)));
        }

        Matcher track = YT_MUSIC_TRACK.matcher(text);
        if (track.find()) {
            /*
             * Nagranie to zawsze pojedynczy utwor. "Artysta" bywa na YouTube kanalem - to inny byt
             * i nie udajemy, ze umiemy go rozpoznac.
             */
            return Optional.of(new ParsedMusicLink(
                MusicProvider.YOUTUBE, MusicKind.TRACK, track.group(1)));
        }

        return apple(text);
    }

    /**
     * Czy to adres ze zwyklego YouTube'a (a wiec taki, ktorego NIE przyjmujemy)? Po co osobna
     * metoda, skoro i tak odrzucamy.
     */
    public static boolean isPlainYouTube(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String text = url.toLowerCase(Locale.ROOT);

        /*
         * Kolejnosc ma znaczenie: "music.youtube.com" ZAWIERA "youtube.com", wiec najpierw
         * wykluczamy wersje muzyczna, a dopiero potem pytamy o zwykla.
         */
        if (text.contains("music.youtube.com")) {
            return false;
        }
        return text.contains("youtube.com/") || text.contains("youtu.be/");
    }

    private static Optional<ParsedMusicLink> spotify(String text) {
        for (Pattern pattern : List.of(SPOTIFY_URL, SPOTIFY_URI)) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                return Optional.of(new ParsedMusicLink(
                    MusicProvider.SPOTIFY,
                    /* Locale.ROOT jest tu KONIECZNE. */
                    MusicKind.valueOf(m.group(1).toUpperCase(Locale.ROOT)),
                    m.group(2)));
            }
        }
        return Optional.empty();
    }

    /** Apple Music. */
    private static Optional<ParsedMusicLink> apple(String text) {
        Matcher m = APPLE.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }

        String country = m.group(1);
        String type = m.group(2);
        String rest = m.group(3);
        String trackOnAlbum = m.group(5);

        MusicKind kind;
        String path;

        if ("album".equals(type) && trackOnAlbum != null) {
            kind = MusicKind.TRACK;
            path = country + "/album/" + rest + "?i=" + trackOnAlbum;
        } else {
            kind = switch (type) {
                case "album" -> MusicKind.ALBUM;
                case "playlist" -> MusicKind.PLAYLIST;
                case "artist" -> MusicKind.ARTIST;
                default -> MusicKind.TRACK;      // "song"
            };
            path = country + "/" + type + "/" + rest;
        }

        return Optional.of(new ParsedMusicLink(MusicProvider.APPLE_MUSIC, kind, path));
    }
}
