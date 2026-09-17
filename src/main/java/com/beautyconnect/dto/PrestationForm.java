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
    private String name;

    private String description;

    @NotNull(message = "Le type de prestation est obligatoire")
    private ServiceType type;

    @NotNull(message = "Le tarif est obligatoire")
    @DecimalMin(value = "0.0", inclusive = true, message = "Le tarif doit etre positif") // inclusive = true : 0 est autorise (prestation gratuite possible)
    private BigDecimal price;

    @NotNull(message = "La duree est obligatoire")
    @Min(value = 5, message = "La duree minimale est de 5 minutes")
    private Integer durationMinutes;
}
