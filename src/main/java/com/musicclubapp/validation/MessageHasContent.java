package com.musicclubapp.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Wiadomosc musi niesc <b>cokolwiek</b>: tekst albo nagranie.
 *
 * <p><b>Dlaczego nie zwykle {@code @NotBlank} na tresci.</b> Bo wyslanie
 * samego utworu, bez ani jednego slowa, jest zupelnie normalne - i wlasnie
 * po to ten czat powstal. {@code @NotBlank} zmuszaloby do dopisywania
 * czegokolwiek obok linku, czyli do udawania, ze sie cos napisalo.</p>
 *
 * <p>Zabraniamy tylko przypadku, w ktorym nie ma NICZEGO. Taka wiadomosc
 * nie jest cisza w rozmowie - jest pustym dymkiem, ktorego odbiorca nie ma
 * jak zinterpretowac.</p>
 */
@Documented
@Constraint(validatedBy = MessageHasContentValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MessageHasContent {

    String message() default "{validation.message.empty}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
