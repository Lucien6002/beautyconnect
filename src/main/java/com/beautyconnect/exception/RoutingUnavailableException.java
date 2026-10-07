package com.beautyconnect.exception;

public class RoutingUnavailableException extends RuntimeException {
    public RoutingUnavailableException() {
        super("Le calcul piéton est indisponible. Utilisez le lien Google Maps ou réessayez plus tard.");
    }
}
