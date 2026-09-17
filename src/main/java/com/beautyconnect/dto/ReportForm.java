package com.beautyconnect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Formulaire de signalement (d'un avis ou d'un profil professionnel).
 * Voir {@link ClientRegistrationForm} pour l'explication generale des DTO.
 */
@Getter
@Setter
public class ReportForm {

    @NotBlank(message = "Merci de preciser le motif du signalement")
    private String reason;
}
