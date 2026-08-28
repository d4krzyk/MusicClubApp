package com.musicclubapp.error;

import com.musicclubapp.storage.InvalidFileException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Jedno miejsce na obsluge wszystkich wyjatkow w aplikacji - wymaganie nr 11.
 *
 * <p>Zbudowane wedlug wykladu 3 (slajdy 68-72):</p>
 * <ul>
 *   <li>jedna klasa {@code @ControllerAdvice} na caly projekt,</li>
 *   <li>dziedziczy z {@link ResponseEntityExceptionHandler}, ktory ma juz
 *       gotowe metody na wyjatki Springa,</li>
 *   <li>kazda metoda ma {@code @ResponseStatus}, loguje blad i zwraca
 *       {@link ResponseEntity} z wlasnym typem {@link ErrorResponse}.</li>
 * </ul>
 *
 * <p><b>Uwaga do slajdu 71:</b> sygnatura {@code handleMethodArgumentNotValid}
 * na wykladzie pochodzi ze starszej wersji Springa. W Spring Framework 6
 * (czyli Spring Boot 3, ktorego uzywamy) metoda w klasie nadrzednej wyglada
 * tak jak nizej - z {@code HttpHeaders} i {@code HttpStatusCode}. Ze starej
 * sygnatury {@code @Override} po prostu by sie nie skompilowal.</p>
 *
 * <p>Komunikaty pobieramy z {@link MessageSource}, czyli z plikow
 * {@code lang/messages*.properties} - dzieki temu bledy sa po polsku albo po
 * angielsku, zaleznie od naglowka {@code Accept-Language} (wymaganie nr 2).</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Blad walidacji {@code @Valid} na ciele zapytania (wymagania nr 9 i 10).
     * Zwracamy 422 wraz z lista pol, ktore nie przeszly walidacji - frontend
     * moze podswietlic konkretne pola formularza.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        logger.error("Blad walidacji: ", ex);

        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            translate("error.validation"));

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errorResponse.addValidationError(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.unprocessableEntity().body(errorResponse);
    }

    /**
     * Walidacja, ktora nie zadzialala na argumencie metody kontrolera,
     * tylko np. na encji przy zapisie (wyklad 3, slajd 65).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {

        logger.error("Naruszenie ograniczen: ", ex);

        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            translate("error.validation"));

        ex.getConstraintViolations().forEach(violation ->
            errorResponse.addValidationError(
                violation.getPropertyPath().toString(),
                violation.getMessage()));

        return ResponseEntity.unprocessableEntity().body(errorResponse);
    }

    /** Brak elementu w bazie - wymaganie nr 11. Zwracamy 404 (wyklad 4, slajd 32). */
    @ExceptionHandler(NoSuchElementFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNoSuchElementFound(
            NoSuchElementFoundException ex, WebRequest request) {

        logger.error("Nie znaleziono szukanego elementu", ex);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                translate(ex.getMessageKey(), ex.getArguments())));
    }

    /** Zle haslo albo nieistniejacy login. Zwracamy 401. */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {

        logger.warn("Nieudana proba logowania");

        // Celowo NIE zdradzamy, czy zly byl login czy haslo - inaczej dalibysmy
        // podpowiedz komus, kto zgaduje, ktore konta istnieja.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                translate("error.badcredentials")));
    }

    /**
     * Zle obecne haslo przy zmianie hasla w ustawieniach.
     * Zwracamy 422 z bledem przypietym do konkretnego pola, zeby frontend
     * podswietlil je tak samo jak kazdy inny blad walidacji.
     */
    @ExceptionHandler(InvalidCurrentPasswordException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<ErrorResponse> handleInvalidCurrentPassword(
            InvalidCurrentPasswordException ex, WebRequest request) {

        logger.warn("Nieudana proba zmiany hasla - bledne obecne haslo");

        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            translate("error.validation"));

        errorResponse.addValidationError(
            InvalidCurrentPasswordException.POLE,
            translate("error.password.current.invalid"));

        return ResponseEntity.unprocessableEntity().body(errorResponse);
    }

    /** Proba zalozenia konta na zajety login lub e-mail. Zwracamy 409 (wyklad 4, slajd 32). */
    @ExceptionHandler(DuplicateResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex, WebRequest request) {

        logger.warn("Proba utworzenia duplikatu: " + ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                translate(ex.getMessageKey())));
    }

    /**
     * Wgrany plik nie przeszedl kontroli (pusty albo nie jest obrazkiem).
     * 422 z bledem przypietym do pola, zeby frontend podswietlil wybor pliku.
     */
    @ExceptionHandler(InvalidFileException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<ErrorResponse> handleInvalidFile(
            InvalidFileException ex, WebRequest request) {

        logger.warn("Odrzucony plik: " + ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            translate("error.validation"));

        errorResponse.addValidationError("images", translate(ex.getMessageKey()));

        return ResponseEntity.unprocessableEntity().body(errorResponse);
    }

    /**
     * Wgrany plik przekroczyl limit rozmiaru z application.properties.
     *
     * <p>Bez tego uzytkownik dostawalby domyslna odpowiedz Springa, ktora nie
     * ma naszego formatu bledu - frontend nie umialby jej pokazac przy polu.</p>
     *
     * <p><b>Uwaga:</b> to musi byc {@code @Override}, a NIE nowa metoda
     * z {@code @ExceptionHandler}. Klasa nadrzedna obsluguje juz ten wyjatek,
     * wiec dolozenie drugiej metody konczy sie bledem przy starcie:
     * "Ambiguous @ExceptionHandler method mapped for MaxUploadSizeExceededException".</p>
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        logger.warn("Plik przekroczyl dozwolony rozmiar");

        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.PAYLOAD_TOO_LARGE.value(), translate("error.file.toolarge"));
        errorResponse.addValidationError("images", translate("error.file.toolarge"));

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
    }

    /** Operacja zabroniona regula biznesowa (np. zmiana wlasnej roli). 409 wg wykladu 4, slajd 32. */
    @ExceptionHandler(OperationNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ErrorResponse> handleOperationNotAllowed(
            OperationNotAllowedException ex, WebRequest request) {

        logger.warn("Zablokowana operacja: " + ex.getMessage());

        ErrorResponse odpowiedz = new ErrorResponse(
            HttpStatus.CONFLICT.value(), translate(ex.getMessageKey()));

        /*
         * Termin konca kary dokladamy jako OSOBNE POLE, a nie wklejamy
         * w komunikat. Serwer liczy czas w UTC i nie wie, w jakiej strefie
         * siedzi uzytkownik - wklejona przez niego godzina bywala wiec
         * cofnieta (zakaz nalozony o 16:55 pokazywal sie jako "do 15:55").
         * Zegar uzytkownika zna tylko jego przegladarka i to ona sklada
         * z tej chwili czytelny napis.
         */
        odpowiedz.setDeadline(ex.getDeadline());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(odpowiedz);
    }

    /**
     * Siatka bezpieczenstwa na wszystko, czego nie przewidzielismy
     * (wyklad 3, slajd 72). Bez tego uzytkownik dostalby domyslna strone bledu
     * Springa ze szczegolami dzialania serwera.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleAllUncaughtException(
            Exception ex, WebRequest request) {

        logger.error("Wystapil nieznany blad", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                translate("error.internal")));
    }

    /**
     * Pobiera tekst z pliku messages dla jezyka biezacego zapytania.
     * {@link LocaleContextHolder} zwraca jezyk ustalony przez LocaleResolver
     * (u nas: na podstawie naglowka Accept-Language albo parametru ?lang=).
     */
    private String translate(String key, Object... arguments) {
        return messageSource.getMessage(key, arguments, LocaleContextHolder.getLocale());
    }
}
