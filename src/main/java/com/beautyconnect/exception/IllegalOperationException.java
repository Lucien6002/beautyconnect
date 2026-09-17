package com.beautyconnect.exception;

/** Leve lorsqu'une operation n'est pas autorisee dans l'etat courant (ex: creneau deja pris). */
public class IllegalOperationException extends RuntimeException {

    public IllegalOperationException(String message) {
        super(message);
    }
}
