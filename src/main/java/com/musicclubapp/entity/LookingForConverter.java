package com.musicclubapp.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code Set<LookingFor>} w jednej kolumnie: "CONCERT_BUDDIES,JAMMING". Kolejnosc to kolejnosc stalych w
 * wyliczeniu (EnumSet), wiec ten sam wybor zawsze daje ten sam napis. Nieznana nazwa (np. po usunieciu stalej)
 * jest pomijana, zamiast wywracac odczyt calego konta. Pusta kolumna = pusty zbior.
 */
@Converter
public class LookingForConverter implements AttributeConverter<Set<LookingFor>, String> {

    @Override
    public String convertToDatabaseColumn(Set<LookingFor> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return EnumSet.copyOf(value).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<LookingFor> convertToEntityAttribute(String column) {
        Set<LookingFor> wynik = EnumSet.noneOf(LookingFor.class);
        if (column == null || column.isBlank()) {
            return wynik;
        }
        Arrays.stream(column.split(",")).map(String::strip).forEach(nazwa -> {
            for (LookingFor l : LookingFor.values()) {
                if (l.name().equals(nazwa)) {
                    wynik.add(l);
                }
            }
        });
        return wynik;
    }
}
