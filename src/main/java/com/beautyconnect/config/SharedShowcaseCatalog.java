package com.beautyconnect.config;

import com.beautyconnect.model.ServiceType;
import java.math.BigDecimal;
import java.util.List;

/** Catalogue public versionné : identifiants stables et URL, sans secret Cloudinary ni ID de base locale. */
public record SharedShowcaseCatalog(List<Salon> salons) {
    public record Salon(String key, String email, String firstName, String businessName,
                        String cityCode, String cover, List<Offer> offers) {}
    public record Offer(String key, String name, List<String> aliases, ServiceType type,
                        BigDecimal price, int durationMinutes, List<String> photos) {}
}
