package com.beautyconnect.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Formulaire d'ajout d'un creneau de disponibilite par un professionnel.
 * Voir {@link ClientRegistrationForm} pour l'explication generale des DTO.
 */
@Getter
@Setter
public class TimeSlotForm {

    @NotNull(message = "La date et l'heure sont obligatoires")
    @Future(message = "Le creneau doit etre dans le futur") // empeche de creer un creneau dans le passe
    private LocalDateTime startDateTime;
}
