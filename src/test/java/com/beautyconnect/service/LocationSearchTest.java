package com.beautyconnect.service;

import com.beautyconnect.utils.LocationUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationSearchTest {

    @Test
    void calculateDistance_ShouldBeAccurateBetweenParisAndLyon() {
        // Paris : 48.8566, 2.3522
        // Lyon : 45.7640, 4.8357
        double distance = LocationUtils.calculateDistance(48.8566, 2.3522, 45.7640, 4.8357);

        // La distance à vol d'oiseau entre Paris et Lyon est d'environ 392 km
        assertTrue(distance > 380 && distance < 410, "La distance entre Paris et Lyon doit être d'environ 390 km");
    }
}