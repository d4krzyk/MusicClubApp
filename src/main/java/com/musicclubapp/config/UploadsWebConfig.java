package com.musicclubapp.config;

import com.musicclubapp.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Wystawia wgrane pliki pod adresem {@code /uploads/**}.
 *
 * <p>Bez tego zdjecia leza na dysku, ale przegladarka nie ma jak ich pobrac -
 * Spring domyslnie serwuje tylko pliki spakowane w aplikacji
 * ({@code src/main/resources/static}), a nasze powstaja dopiero w trakcie
 * dzialania programu.</p>
 *
 * <p>{@code file:} przed sciezka jest konieczne - mowi Springowi, ze chodzi
 * o katalog na dysku, a nie o zasob wewnatrz jara.</p>
 *
 * <p><b>Dlaczego sciezke czytamy z properties, a nie z {@code FileStorageService}?</b>
 * Bo ta klasa implementuje {@link WebMvcConfigurer}, a testy pisane
 * z {@code @WebMvcTest} automatycznie wciagaja wszystkie takie klasy. Gdyby
 * wymagala serwisu plikow, kazdy test kontrolera musialby go podstawiac -
 * inaczej caly kontekst nie wstaje. Tak jest niezalezna i testy pozostaja lekkie.</p>
 */
@Configuration
public class UploadsWebConfig implements WebMvcConfigurer {

    private final Path katalog;

    public UploadsWebConfig(@Value("${app.uploads.dir:uploads}") String katalogUploadow) {
        this.katalog = Paths.get(katalogUploadow).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler(PostMapper.SCIEZKA_PLIKOW + "**")
            .addResourceLocations("file:" + katalog + "/");
    }
}
