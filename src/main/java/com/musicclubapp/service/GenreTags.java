package com.musicclubapp.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Porownywanie gatunkow z dwoch zrodel.
 *
 * Twoje gatunki to tagi Last.fm przy ulubionych artystach: "indie rock",
 * "polish hip-hop", "alternative". Gatunki koncertu to z jednej strony
 * ogolne etykiety Ticketmastera ("Hip-Hop/Rap", "Rock"), a z drugiej - tagi
 * Last.fm jego wykonawcow. Dokladnie te same tagi to mocny sygnal; do tego
 * ta sama RODZINA ("polish hip-hop" i "Hip-Hop/Rap" to obie hip-hop) -
 * slabszy, ale bez niego ogolne etykiety Ticketmastera nie pasowalyby do
 * niczego.
 */
final class GenreTags {

    private GenreTags() {
    }

    /**
     * Tagi Last.fm, ktore gatunkiem nie sa. "polish" pasowaloby do kazdego
     * polskiego wykonawcy, niezaleznie od tego, czy gra metal, czy disco polo.
     */
    private static final Set<String> NIE_GATUNKI = Set.of(
        "polish", "polska", "british", "american", "german", "french", "swedish",
        "norwegian", "canadian", "australian", "irish", "italian", "spanish",
        "femalevocalists", "malevocalists", "femalevocalist", "malevocalist", "singer",
        "50s", "60s", "70s", "80s", "90s", "00s", "2000s", "2010s", "2020s",
        "seenlive", "favorites", "favourites", "love", "beautiful", "awesome",
        "undefined", "other", "music");

    /**
     * Rodziny gatunkow i slowa, po ktorych je rozpoznajemy. Tag moze nalezec
     * do kilku rodzin ("pop rock" to i pop, i rock). Slowa sa szukane jako
     * fragmenty tagu, wiec krotkie trzeba dobierac ostroznie: "dance" lapal
     * "dancehall" (reggae) jako elektronike, dlatego go tu nie ma - Ticketmaster
     * i tak pisze "Dance/Electronic", wiec "electronic" wystarcza.
     */
    private static final Map<String, List<String>> RODZINY = new LinkedHashMap<>();

    static {
        RODZINY.put("hip-hop", List.of("hiphop", "rap", "trap", "grime", "drill"));
        RODZINY.put("metal", List.of("metal", "metalcore", "deathcore", "grindcore"));
        RODZINY.put("punk", List.of("punk", "hardcore", "emo", "screamo"));
        RODZINY.put("rock", List.of("rock", "grunge", "shoegaze"));
        RODZINY.put("indie", List.of("indie", "alternative", "altpop", "lofi"));
        RODZINY.put("pop", List.of("pop", "discopolo", "schlager"));
        RODZINY.put("electronic", List.of("electronic", "electro", "techno", "house", "trance",
            "edm", "dubstep", "drumandbass", "dnb", "ambient", "synth", "idm", "breakbeat"));
        RODZINY.put("jazz", List.of("jazz", "swing", "bebop", "fusion"));
        RODZINY.put("classical", List.of("classical", "opera", "baroque", "orchestral",
            "neoclassical", "chamber", "symphon", "piano"));
        RODZINY.put("blues", List.of("blues"));
        RODZINY.put("soul", List.of("soul", "rnb", "funk", "gospel", "motown"));
        RODZINY.put("folk", List.of("folk", "country", "singersongwriter", "acoustic", "americana"));
        /* Bez samego "dub" - lapalby "dubstep", ktory jest elektronika, nie reggae. */
        RODZINY.put("reggae", List.of("reggae", "ska", "dancehall"));
        RODZINY.put("latin", List.of("latin", "reggaeton", "salsa", "bachata", "tango", "flamenco"));
    }

    /**
     * Tag w jednej postaci: male litery, bez spacji, myslnikow i znakow.
     * "Hip-Hop", "hip hop" i "hiphop" to ma byc to samo; "R&B" to "rnb".
     */
    static String normalize(String tag) {
        if (tag == null) {
            return "";
        }
        return tag.toLowerCase(Locale.ROOT)
            .replace("&", "n")
            .replace("drum and bass", "drumandbass")
            .replaceAll("[^a-z0-9]", "");
    }

    /**
     * Rozbija etykiete na tagi i odsiewa te, ktore gatunkami nie sa.
     * "Hip-Hop/Rap" z Ticketmastera to dwa tagi: hiphop i rap.
     */
    static Set<String> tags(String label) {
        Set<String> result = new LinkedHashSet<>();
        if (label == null) {
            return result;
        }
        for (String part : label.split("/")) {
            String tag = normalize(part);
            if (!tag.isEmpty() && !NIE_GATUNKI.contains(tag)) {
                result.add(tag);
            }
        }
        return result;
    }

    /** Rodziny, do ktorych nalezy tag. */
    static Set<String> families(String normalizedTag) {
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> rodzina : RODZINY.entrySet()) {
            for (String slowo : rodzina.getValue()) {
                if (normalizedTag.contains(slowo)) {
                    result.add(rodzina.getKey());
                    break;
                }
            }
        }
        return result;
    }

    static Set<String> families(Set<String> normalizedTags) {
        Set<String> result = new LinkedHashSet<>();
        for (String tag : normalizedTags) {
            result.addAll(families(tag));
        }
        return result;
    }
}
