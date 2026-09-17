package com.beautyconnect.exception;

/** Leve lors d'une tentative d'inscription avec un email deja utilise. */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Un compte existe deja avec l'email : " + email);
    }
}
