package com.beautyconnect.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Formulaire de depot d'un avis par un client.
 * Voir {@link ClientRegistrationForm} pour l'explication generale des DTO.
 */
@Getter
@Setter
public class ReviewForm {

    @NotNull(message = "La note est obligatoire")
    @Min(value = 1, message = "La note minimale est 1")
    @Max(value = 5, message = "La note maximale est 5")
    private Integer rating;

    private String comment;

    /** Optionnel : rendez-vous concerne par l'avis. */
    private Long appointmentId;
}
