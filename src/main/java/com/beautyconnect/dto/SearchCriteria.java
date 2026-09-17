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

    private String city;

    private TargetGender gender;

    private ServiceType type;
}
