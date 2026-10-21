package com.beautyconnect.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter
public class ForgotPasswordForm {
    @NotBlank(message = "Indiquez votre adresse e-mail")
    @Email(message = "Indiquez une adresse e-mail valide")
    @Size(max = 254, message = "L’adresse e-mail est trop longue")
    private String email;
}
