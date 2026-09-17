package com.beautyconnect.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Gestionnaire d'exceptions centralise pour TOUS les controleurs de
 * l'application (@ControllerAdvice = version "globale" de @Controller,
 * appliquee automatiquement partout sans avoir a l'ajouter a chaque
 * controleur individuellement).
 *
 * Interet : sans cette classe, une exception ResourceNotFoundException levee
 * n'importe ou dans un service (voir par exemple ProfessionalService.getProfileOrThrow)
 * remonterait telle quelle jusqu'a Tomcat et afficherait une page d'erreur
 * generique et technique. Ici, on intercepte ces exceptions "metier" pour
 * afficher une vraie page HTML personnalisee a la place, sans avoir a
 * entourer chaque methode de controleur d'un try/catch.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    // @ExceptionHandler(...) : cette methode est appelee automatiquement des
    // qu'une ResourceNotFoundException (ou une sous-classe) est levee
    // n'importe ou pendant le traitement d'une requete.
    // @ResponseStatus(HttpStatus.NOT_FOUND) : force le code de statut HTTP
    // de la reponse a 404, meme si on retourne une vue HTML normale.
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler(IllegalOperationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalOperation(IllegalOperationException ex, Model model, HttpServletRequest request) {
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        return "error/400";
    }
}
