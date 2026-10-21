package com.beautyconnect.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.nio.charset.StandardCharsets;

@Getter @Setter
public class ResetPasswordForm {
    @NotBlank(message = "Le lien est invalide ou expiré")
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "Le lien est invalide ou expiré")
    private String token;
    @NotBlank(message = "Indiquez votre nouveau mot de passe")
    @Size(min = 8, max = 72, message = "Utilisez entre 8 et 72 caractères")
    private String newPassword;
    @NotBlank(message = "Confirmez votre nouveau mot de passe")
    @Size(max = 72, message = "La confirmation est trop longue")
    private String confirmPassword;

    @AssertTrue(message = "Le mot de passe dépasse la limite de 72 octets ; réduisez sa longueur")
    public boolean isPasswordWithinByteLimit() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
