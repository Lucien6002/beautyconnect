package com.beautyconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Formulaire de signalement (d'un avis ou d'un profil professionnel).
 * Voir {@link ClientRegistrationForm} pour l'explication generale des DTO.
 */
@Getter
@Setter
public class ReportForm {

    // Meme limite que la colonne reports.reason (VARCHAR(1000)) : sans elle,
    // un motif trop long provoquait une erreur SQL (page 500).
    @NotBlank(message = "Merci de preciser le motif du signalement")
    @Size(max = 1000, message = "Le motif ne doit pas depasser 1000 caracteres")
    private String reason;
}
