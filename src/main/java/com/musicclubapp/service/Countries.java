package com.musicclubapp.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Kod kraju (ISO 3166-1 alfa-2) z angielskiej nazwy - Bandsintown i Songkick podaja kraj nazwa ("Poland"). */
final class Countries {

    private static final Map<String, String> PO_NAZWIE = new HashMap<>();

    static {
        for (String kod : Locale.getISOCountries()) {
            PO_NAZWIE.put(new Locale("", kod).getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT), kod);
        }
        // Nazwy, ktorych Java nie zna w tej postaci
        PO_NAZWIE.put("united states of america", "US");
        PO_NAZWIE.put("usa", "US");
        PO_NAZWIE.put("uk", "GB");
        PO_NAZWIE.put("england", "GB");
        PO_NAZWIE.put("scotland", "GB");
        PO_NAZWIE.put("wales", "GB");
        PO_NAZWIE.put("czech republic", "CZ");
        PO_NAZWIE.put("the netherlands", "NL");
    }

    private Countries() {
    }

    static String isoFromEnglishName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.strip();
        if (n.length() == 2) {
            return n.toUpperCase(Locale.ROOT);
        }
        return PO_NAZWIE.get(n.toLowerCase(Locale.ROOT));
    }
}
