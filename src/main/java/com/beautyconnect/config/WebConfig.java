package com.beautyconnect.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration pour servir les fichiers statiques (images uploadées).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Photos de profil des professionnels
        Path profilePhotosPath = Paths.get("uploads/profile-photos").toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/profile-photos/**")
                .addResourceLocations("file:///" + profilePhotosPath + "/");

        // Photos des clients
        Path userPhotosPath = Paths.get("uploads/user-photos").toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/user-photos/**")
                .addResourceLocations("file:///" + userPhotosPath + "/");

        // Photos des prestations
        Path prestationPhotosPath = Paths.get("uploads/prestation-photos").toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/prestation-photos/**")
                .addResourceLocations("file:///" + prestationPhotosPath + "/");

        // Images des prestations (PrestationImage)
        Path prestationImagesPath = Paths.get("uploads").toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:///" + prestationImagesPath + "/");
    }
}
