package com.beautyconnect.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${image.storage.upload-dir:uploads}") private String directory;
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uri = Path.of(directory).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(uri.endsWith("/") ? uri : uri + "/");
    }
}
