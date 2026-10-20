package com.beautyconnect.dto;

import com.beautyconnect.model.ServiceType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Formulaire d'ajout d'une prestation par un professionnel.
 * Voir {@link ClientRegistrationForm} pour l'explication generale des DTO.
 */
@Getter
@Setter
public class PrestationForm {

    @NotBlank(message = "Le nom de la prestation est obligatoire")
    @Size(max = 255, message = "Le nom doit contenir au maximum 255 caractères")
    private String name;

    @Size(max = 1000, message = "La description doit contenir au maximum 1000 caractères")
    private String description;

    @NotNull(message = "Le type de prestation est obligatoire")
    private ServiceType type;

    @NotNull(message = "Le tarif est obligatoire")
    @DecimalMin(value = "0.0", inclusive = true, message = "Le tarif doit etre positif") // inclusive = true : 0 est autorise (prestation gratuite possible)
    @Digits(integer = 6, fraction = 2, message = "Le tarif accepte au maximum 6 chiffres et 2 décimales")
    private BigDecimal price;

    @NotNull(message = "La duree est obligatoire")
    @Min(value = 5, message = "La duree minimale est de 5 minutes")
    @Max(value = 1440, message = "La durée ne peut pas dépasser 24 heures")
    private Integer durationMinutes;
}
