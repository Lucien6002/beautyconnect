package com.beautyconnect.controller;

import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.service.ProfessionalService;
import com.beautyconnect.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Consultation publique du profil d'un professionnel : vitrine (bio, ville,
 * prestations avec tarifs et durees), avis clients, et creneaux disponibles
 * pour amorcer une prise de rendez-vous.
 */
@Controller
@RequiredArgsConstructor
public class ProfessionalPublicController {

    private final ProfessionalService professionalService;
    private final ReviewService reviewService;

    // @PathVariable Long id : recupere le segment {id} de l'URL
    // (ex: /professionnels/42 -> id = 42) et le convertit automatiquement en Long.
    @GetMapping("/professionnels/{id}")
    public String viewProfile(@PathVariable Long id, Model model) {
        ProfessionalProfile professional = professionalService.getProfileOrThrow(id);

        model.addAttribute("professional", professional);
        model.addAttribute("prestations", professionalService.getActivePrestations(professional));
        model.addAttribute("timeSlots", professionalService.getAvailableTimeSlots(professional));
        model.addAttribute("reviews", reviewService.getVisibleReviews(professional));
        model.addAttribute("averageRating", reviewService.getAverageRating(professional));
        // Objets vides fournis au template pour que les formulaires de prise
        // de rendez-vous / depot d'avis presents sur cette page (voir
        // AppointmentController / ReviewController) aient un objet a lier
        // (th:object) des le premier affichage.
        model.addAttribute("appointmentForm", new com.beautyconnect.dto.AppointmentForm());
        model.addAttribute("reviewForm", new com.beautyconnect.dto.ReviewForm());
        return "professional/profile";
    }
}
