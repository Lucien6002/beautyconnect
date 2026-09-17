package com.beautyconnect.exception;

/** Leve lorsque une entite (profil, prestation, creneau, rendez-vous...) est introuvable. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
