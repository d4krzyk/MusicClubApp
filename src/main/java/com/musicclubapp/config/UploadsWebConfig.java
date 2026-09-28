package com.musicclubapp.config;

import com.musicclubapp.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/** Wystawia wgrane pliki pod adresem /uploads/**. */
@Configuration
public class UploadsWebConfig implements WebMvcConfigurer {

    private final Path directory;

    public UploadsWebConfig(@Value("${app.uploads.dir:uploads}") String uploadsDirectory) {
        this.directory = Paths.get(uploadsDirectory).toAbsolutePath().normalize();
    }

    /**
     * Jak dlugo przegladarka moze trzymac wgrane zdjecie u siebie.
     *
     * Trzydziesci dni, bo te pliki sie NIE ZMIENIAJA: nazwa to losowy ciag
     * nadawany przy zapisie, a podmiana zdjecia tworzy nowy plik pod nowa
     * nazwa. Nie ma wiec czego uniewazniac.
     *
     * Bez tego naglowka przegladarka przy kazdym powrocie na tablice pytala
     * serwer o kazde zdjecie osobno. Odpowiedz bywala krotka ("nic sie nie
     * zmienilo"), ale to i tak osobne zapytanie po sieci komorkowej.
     */
    private static final Duration WAZNOSC_ZDJEC = Duration.ofDays(30);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler(PostMapper.UPLOADS_PATH + "**")
            .addResourceLocations("file:" + directory + "/")
            /* "immutable" mowi przegladarce, zeby nie pytala nawet przy
               odswiezeniu strony - plik pod tym adresem juz sie nie zmieni. */
            .setCacheControl(CacheControl.maxAge(WAZNOSC_ZDJEC).cachePublic().immutable());
    }
}
