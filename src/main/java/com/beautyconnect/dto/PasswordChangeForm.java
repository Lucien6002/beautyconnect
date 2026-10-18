package com.beautyconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Changement de mot de passe par l'utilisateur connecte. Le mot de passe
 * actuel est exige pour qu'une session laissee ouverte ne suffise pas a
 * prendre le controle du compte. Taille max 72 : limite de BCrypt.
 */
@Getter
@Setter
public class PasswordChangeForm {

    @NotBlank(message = "Le mot de passe actuel est obligatoire")
    private String currentPassword;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caracteres")
    private String newPassword;

    @NotBlank(message = "Merci de confirmer le nouveau mot de passe")
    private String confirmPassword;
}
