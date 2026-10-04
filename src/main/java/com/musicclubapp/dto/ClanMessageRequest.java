package com.musicclubapp.dto;

import com.musicclubapp.entity.ClanMessage;
import com.musicclubapp.validation.MessageHasContent;
import com.musicclubapp.validation.MessageToValidate;
import jakarta.validation.constraints.Size;

/**
 * Nowa wiadomosc na czacie klanu: tekst, GIF albo jedno i drugie. {@code replyTo} - numer wiadomosci, na
 * ktora odpowiada; {@code gif} - podpisany token z przegladarki GIF-ow ({@code GET /api/gifs/search}).
 */
@MessageHasContent(message = "{validation.clanMessage.empty}")
public record ClanMessageRequest(@Size(max = ClanMessage.MAX_CONTENT_LENGTH) String content,
                                 Long replyTo,
                                 @Size(max = 2000, message = "{validation.gif.invalid}") String gif)
    implements MessageToValidate {
}
