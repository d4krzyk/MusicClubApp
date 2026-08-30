package com.musicclubapp.config;

import com.musicclubapp.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Wystawia wgrane pliki pod adresem /uploads/**. */
@Configuration
public class UploadsWebConfig implements WebMvcConfigurer {

    private final Path directory;

    public UploadsWebConfig(@Value("${app.uploads.dir:uploads}") String uploadsDirectory) {
        this.directory = Paths.get(uploadsDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler(PostMapper.UPLOADS_PATH + "**")
            .addResourceLocations("file:" + directory + "/");
    }
}
