package com.musicclubapp.validation;

import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicLinkParser;
import com.musicclubapp.music.ParsedMusicLink;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Optional;

/**
 * Walidator dla {@link ValidMusicLink}.
 *
 * <p>Pilnuje czterech rzeczy - i kazda odpowiada innej pomylce, ktora
 * uzytkownik faktycznie popelni:</p>
 *
 * <ol>
 *   <li><b>Adres w ogole nie jest linkiem muzycznym.</b> Wczesniej taki tekst
 *       byl po cichu polykany, post powstawal bez odtwarzacza i nikt nie
 *       wiedzial dlaczego.</li>
 *   <li><b>Rodzaj nie zgadza sie z linkiem</b> - wybral "Album", a wkleil
 *       utwor. Mowimy wprost, co wkleil, zamiast ogolnego "zly link".</li>
 *   <li><b>Wybrano rodzaj, ale nie podano adresu</b> (albo odwrotnie).</li>
 *   <li><b>Moment startu przy albumie lub artyscie.</b> Formularz chowa wtedy
 *       to pole, ale ktos moze wyslac zapytanie z pominieciem przegladarki -
 *       a serwer nie moze ufac temu, co przyjdzie.</li>
 * </ol>
 */
public class ValidMusicLinkValidator
    implements ConstraintValidator<ValidMusicLink, MusicLinkToValidate> {

    @Override
    public boolean isValid(MusicLinkToValidate data, ConstraintValidatorContext context) {
        if (data == null) {
            return true;
        }

        boolean hasUrl = data.musicUrl() != null && !data.musicUrl().isBlank();
        boolean hasKind = data.musicKind() != null;

        // Post bez muzyki - calkowicie w porzadku, nie ma czego sprawdzac
        if (!hasUrl && !hasKind) {
            return noStartSeconds(data, context);
        }

        if (hasUrl && !hasKind) {
            return error(context, "musicKind", "{validation.music.kind.required}");
        }
        if (!hasUrl) {
            return error(context, "musicUrl", "{validation.music.url.required}");
        }

        Optional<ParsedMusicLink> parsed = MusicLinkParser.parse(data.musicUrl());
        if (parsed.isEmpty()) {
            /*
             * Zwykly YouTube dostaje WLASNY komunikat. Ogolne "to nie jest
             * link do zadnego znanego serwisu" wyglada przy adresie z YouTube'a
             * jak blad aplikacji - przeciez YouTube kazdy zna. Tutaj mowimy
             * wprost, co zrobic: otworzyc to samo w YouTube Music.
             */
            String message = MusicLinkParser.isPlainYouTube(data.musicUrl())
                ? "{validation.music.url.youtubeNotMusic}"
                : "{validation.music.url.invalid}";
            return error(context, "musicUrl", message);
        }

        MusicKind fromLink = parsed.get().kind();
        if (fromLink != data.musicKind()) {
            /*
             * Komunikat mowi, CO uzytkownik wkleil - "to jest link do utworu"
             * jest o wiele bardziej pomocne niz "zly link". Klucz skladamy
             * z nazwy rodzaju, wiec kazdy przypadek ma wlasne tlumaczenie.
             */
            return error(context, "musicUrl",
                "{validation.music.kind.mismatch." + fromLink.name().toLowerCase(java.util.Locale.ROOT) + "}");
        }

        if (!data.musicKind().supportsStartSeconds() && data.musicStartSeconds() != null) {
            return error(context, "musicStartSeconds", "{validation.music.start.onlytrack}");
        }

        return true;
    }

    /** Bez linku moment startu nie ma do czego sie odnosic. */
    private boolean noStartSeconds(MusicLinkToValidate data,
                                      ConstraintValidatorContext context) {
        if (data.musicStartSeconds() == null) {
            return true;
        }
        return error(context, "musicStartSeconds", "{validation.music.start.nolink}");
    }

    /**
     * Przypina komunikat do KONKRETNEGO pola.
     *
     * <p>Bez tego blad z poziomu klasy trafia do odpowiedzi z pustym polem
     * {@code field} i frontend nie wie, co podswietlic - dokladnie tak samo
     * jak przy {@link PasswordsMatchValidator}.</p>
     */
    private boolean error(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
            .addPropertyNode(field)
            .addConstraintViolation();
        return false;
    }
}
