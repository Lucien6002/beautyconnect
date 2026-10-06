package com.beautyconnect.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Formulaire de prise de rendez-vous par un client.
 * Voir {@link ClientRegistrationForm} pour l'explication generale des DTO.
 */
@Getter
@Setter
public class AppointmentForm {

    @NotNull(message = "Veuillez choisir une prestation")
    private Long prestationId;

    @NotNull(message = "Veuillez choisir un creneau")
    private Long timeSlotId;

    @Size(max = 1000, message = "Le message ne doit pas dépasser 1 000 caractères")
    private String notes;
}
