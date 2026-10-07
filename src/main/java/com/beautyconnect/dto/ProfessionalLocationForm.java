package com.beautyconnect.dto;

import com.beautyconnect.utils.LocationUtils;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfessionalLocationForm {
    private Double latitude;
    private Double longitude;
    private boolean coordinatesPublic;

    @AssertTrue(message = "Fournissez deux coordonnées valides ; un point public exige une latitude et une longitude.")
    public boolean isPositionValid() {
        if (latitude == null && longitude == null) return !coordinatesPublic;
        return LocationUtils.isValidPosition(latitude, longitude);
    }
}
