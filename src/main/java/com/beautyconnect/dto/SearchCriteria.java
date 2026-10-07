package com.beautyconnect.dto;

import com.beautyconnect.model.ServiceType;
import com.beautyconnect.model.TargetGender;
import lombok.Getter;
import lombok.Setter;

/**
 * Criteres du moteur de recherche public : ville, sexe de la clientele
 * visee, type de prestation. Tous les champs sont optionnels.
 */
@Getter
@Setter
public class SearchCriteria {

    @jakarta.validation.constraints.Size(max = 100)
    private String city;

    @jakarta.validation.constraints.Size(max = 100)
    private String name;

    private TargetGender gender;

    private ServiceType type;

    private Double clientLatitude;

    private Double clientLongitude;
    @jakarta.validation.constraints.Min(0)
    @jakarta.validation.constraints.Max(10000)
    private int page;

    @jakarta.validation.constraints.AssertTrue(message = "Veuillez fournir une latitude et une longitude valides ensemble")
    public boolean isPositionValid() {
        return clientLatitude == null && clientLongitude == null
                || com.beautyconnect.utils.LocationUtils.isValidPosition(clientLatitude, clientLongitude);
    }
}
