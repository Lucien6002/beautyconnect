package com.beautyconnect.model;

/**
 * Cycle de vie d'un rendez-vous.
 *
 * EN_ATTENTE  -> cree par le client, en attente de confirmation du pro
 * CONFIRME    -> accepte par le professionnel (declenche le mail de confirmation)
 * REFUSE      -> refuse par le professionnel
 * ANNULE      -> annule par le client
 * TERMINE     -> prestation realisee (permet de laisser un avis)
 *
 * Les transitions autorisees entre ces statuts sont controlees dans
 * AppointmentService (pas ici : un enum ne fait que lister des valeurs,
 * il ne contient pas de logique metier).
 */
public enum AppointmentStatus {
    EN_ATTENTE,
    CONFIRME,
    REFUSE,
    ANNULE,
    TERMINE
}
