package com.musicclubapp.dto;

import com.musicclubapp.entity.Message;
import com.musicclubapp.entity.Post;
import com.musicclubapp.music.MusicKind;
import com.musicclubapp.validation.MessageHasContent;
import com.musicclubapp.validation.MessageToValidate;
import com.musicclubapp.validation.MusicLinkToValidate;
import com.musicclubapp.validation.ValidMusicLink;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Nowa wiadomosc na czacie.
 *
 * <p><b>Komu ja wysylamy, NIE ma tego rekordu</b> - odbiorca jest w adresie
 * ({@code POST /api/messages/with/{login}}), a nadawca w sesji. Zadnej z tych
 * dwoch rzeczy nie da sie wiec podmienic trescia zapytania.</p>
 *
 * <p>Dwie adnotacje na calej klasie, bo obie porownuja kilka pol naraz:
 * {@link ValidMusicLink} pilnuje, ze link zgadza sie z wybranym rodzajem
 * (dokladnie te same reguly co przy postach - jeden walidator na oba
 * przypadki), a {@link MessageHasContent} - ze wiadomosc w ogole cos niesie.</p>
 */
@ValidMusicLink
@MessageHasContent
public record SendMessageRequest(

    /**
     * Tresc. <b>Moze byc pusta</b>, gdy wysylamy sam utwor - patrz
     * {@link MessageHasContent}.
     */
    @Size(max = Message.MAX_CONTENT_LENGTH, message = "{validation.message.size}")
    String content,

    /** Adres ze Spotify, YouTube Music albo Apple Music; pole opcjonalne. */
    String musicUrl,

    /** Co to jest: utwor, album, artysta czy playlista. Wymagane, gdy podano adres. */
    MusicKind musicKind,

    /**
     * Sekunda, od ktorej ma zagrac utwor.
     *
     * <p>Zakres jest ten sam co przy postach i celowo siegamy po te sama
     * stala zamiast wpisywac liczbe drugi raz: "od ktorej sekundy wolno
     * zaczac utwor" to jedna decyzja, a nie dwie, ktore moglyby sie
     * rozjechac.</p>
     */
    @Min(value = 0, message = "{validation.post.start.range}")
    @Max(value = Post.MAX_SEKUNDA_STARTU, message = "{validation.post.start.range}")
    Integer musicStartSeconds

) implements MusicLinkToValidate, MessageToValidate {
}
