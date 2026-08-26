package com.musicclubapp.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Zapisuje wgrane obrazki na dysku i usuwa je, gdy przestaja byc potrzebne.
 *
 * <p><b>Dlaczego pliki na dysku, a nie w bazie?</b> Do bazy da sie wrzucic
 * obrazek jako {@code byte[]}, ale wtedy kazde zapytanie o tablice postow
 * ciagnie megabajty danych, kopie zapasowe puchna, a baza robi sie wolna.
 * Standardem jest trzymanie w bazie samej NAZWY pliku.</p>
 *
 * <p><b>Bezpieczenstwo - trzy rzeczy, ktore tu pilnujemy:</b></p>
 * <ol>
 *   <li><b>Nigdy nie uzywamy nazwy pliku podanej przez klienta.</b> Nazwa
 *       w stylu {@code ../../etc/passwd} pozwolilaby nadpisac pliki poza
 *       katalogiem uploadow. Zamiast tego generujemy losowa nazwe (UUID),
 *       a rozszerzenie bierzemy z typu MIME, nie z tego, co przyszlo.</li>
 *   <li><b>Przepuszczamy tylko obrazki</b> z listy dozwolonych typow -
 *       inaczej ktos wgralby plik wykonywalny albo skrypt.</li>
 *   <li><b>Limit rozmiaru</b> ustawiony w application.properties, zeby
 *       jeden uzytkownik nie zapchal dysku.</li>
 * </ol>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /**
     * Dozwolone typy obrazkow wraz z rozszerzeniem, ktore im nadajemy.
     * Klucz to typ MIME zgloszony przez przegladarke.
     */
    private static final Map<String, String> DOZWOLONE_TYPY = Map.of(
        "image/jpeg", ".jpg",
        "image/png", ".png",
        "image/webp", ".webp",
        "image/gif", ".gif");

    private final Path catalog;

    public FileStorageService(@Value("${app.uploads.dir:uploads}") String uploadsDirectory) {
        this.catalog = Paths.get(uploadsDirectory).toAbsolutePath().normalize();
    }

    /** Tworzy katalog na pliki przy starcie aplikacji, jesli go jeszcze nie ma. */
    @PostConstruct
    void prepareDirectory() {
        try {
            Files.createDirectories(catalog);
            log.info("Katalog na wgrane pliki: {}", catalog);
        } catch (IOException e) {
            throw new UncheckedIOException("Nie udalo sie utworzyc katalogu na pliki: " + catalog, e);
        }
    }

    /**
     * Zapisuje wgrany obrazek i zwraca nadana mu nazwe.
     *
     * @throws InvalidFileException gdy plik jest pusty albo nie jest obrazkiem
     */
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw InvalidFileException.empty();
        }

        String type = file.getContentType();
        String extension = DOZWOLONE_TYPY.get(type == null ? "" : type.toLowerCase());
        if (extension == null) {
            throw InvalidFileException.wrongType(type);
        }

        // Nazwe nadajemy sami - nazwa od klienta NIGDY nie trafia na dysk
        String name = UUID.randomUUID().toString().replace("-", "") + extension;

        try {
            Files.copy(file.getInputStream(), catalog.resolve(name),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Nie udalo sie zapisac pliku " + name, e);
        }

        return name;
    }

    /**
     * Kasuje plik, ignorujac jego brak.
     *
     * <p>Brak pliku nie jest bledem: mogl zostac skasowany recznie albo
     * przy poprzedniej probie. Wywalanie sie z tego powodu tylko blokowaloby
     * usuniecie posta.</p>
     */
    public void remove(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        /*
         * Dodatkowe zabezpieczenie: bierzemy sama nazwe pliku, odcinajac
         * ewentualne sciezki. Nawet gdyby do bazy trafilo kiedys "../coś",
         * nie skasujemy niczego poza katalogiem uploadow.
         */
        Path target = catalog.resolve(Paths.get(fileName).getFileName());

        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Nie udalo sie usunac pliku {}: {}", fileName, e.getMessage());
        }
    }

    public Path getDirectory() {
        return catalog;
    }
}
