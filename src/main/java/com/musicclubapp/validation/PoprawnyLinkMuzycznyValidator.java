package com.musicclubapp.validation;

import com.musicclubapp.music.MusicKind;
import com.musicclubapp.music.MusicLinkParser;
import com.musicclubapp.music.ParsedMusicLink;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Optional;

/**
 * Walidator dla {@link PoprawnyLinkMuzyczny}.
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
public class PoprawnyLinkMuzycznyValidator
    implements ConstraintValidator<PoprawnyLinkMuzyczny, LinkMuzycznyDoSprawdzenia> {

    @Override
    public boolean isValid(LinkMuzycznyDoSprawdzenia dane, ConstraintValidatorContext context) {
        if (dane == null) {
            return true;
        }

        boolean maAdres = dane.musicUrl() != null && !dane.musicUrl().isBlank();
        boolean maRodzaj = dane.musicKind() != null;

        // Post bez muzyki - calkowicie w porzadku, nie ma czego sprawdzac
        if (!maAdres && !maRodzaj) {
            return brakMomentuStartu(dane, context);
        }

        if (maAdres && !maRodzaj) {
            return blad(context, "musicKind", "{validation.music.kind.required}");
        }
        if (!maAdres) {
            return blad(context, "musicUrl", "{validation.music.url.required}");
        }

        Optional<ParsedMusicLink> rozpoznany = MusicLinkParser.rozpoznaj(dane.musicUrl());
        if (rozpoznany.isEmpty()) {
            return blad(context, "musicUrl", "{validation.music.url.invalid}");
        }

        MusicKind zLinku = rozpoznany.get().kind();
        if (zLinku != dane.musicKind()) {
            /*
             * Komunikat mowi, CO uzytkownik wkleil - "to jest link do utworu"
             * jest o wiele bardziej pomocne niz "zly link". Klucz skladamy
             * z nazwy rodzaju, wiec kazdy przypadek ma wlasne tlumaczenie.
             */
            return blad(context, "musicUrl",
                "{validation.music.kind.mismatch." + zLinku.name().toLowerCase(java.util.Locale.ROOT) + "}");
        }

        if (!dane.musicKind().obslugujeMomentStartu() && dane.musicStartSeconds() != null) {
            return blad(context, "musicStartSeconds", "{validation.music.start.onlytrack}");
        }

        return true;
    }

    /** Bez linku moment startu nie ma do czego sie odnosic. */
    private boolean brakMomentuStartu(LinkMuzycznyDoSprawdzenia dane,
                                      ConstraintValidatorContext context) {
        if (dane.musicStartSeconds() == null) {
            return true;
        }
        return blad(context, "musicStartSeconds", "{validation.music.start.nolink}");
    }

    /**
     * Przypina komunikat do KONKRETNEGO pola.
     *
     * <p>Bez tego blad z poziomu klasy trafia do odpowiedzi z pustym polem
     * {@code field} i frontend nie wie, co podswietlic - dokladnie tak samo
     * jak przy {@link PasswordsMatchValidator}.</p>
     */
    private boolean blad(ConstraintValidatorContext context, String pole, String komunikat) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(komunikat)
            .addPropertyNode(pole)
            .addConstraintViolation();
        return false;
    }
}
