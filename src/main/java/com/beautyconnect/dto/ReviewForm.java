package com.beautyconnect.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    // Meme limite que la colonne reviews.comment (VARCHAR(1000)) : sans elle,
    // un commentaire trop long provoquait une erreur SQL (page 500).
    @Size(max = 1000, message = "Le commentaire ne doit pas depasser 1000 caracteres")
    private String comment;

    /**
     * Optionnel : rendez-vous concerne par l'avis. Renseigne, il fait de
     * l'avis un "avis verifie" (rendez-vous TERMINE du client, chez ce
     * professionnel, voir ReviewService.addReview). Absent, l'avis est libre.
     */
    private Long appointmentId;
}
