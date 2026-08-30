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

/** Zapisuje wgrane obrazki na dysku i usuwa je, gdy przestaja byc potrzebne. */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** Dozwolone typy obrazkow wraz z rozszerzeniem, ktore im nadajemy. */
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

    /** Zapisuje wgrany obrazek i zwraca nadana mu nazwe. */
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

    /** Kasuje plik, ignorujac jego brak. */
    public void remove(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        /* Dodatkowe zabezpieczenie: bierzemy sama nazwe pliku, odcinajac ewentualne sciezki. */
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
