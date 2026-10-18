package com.beautyconnect.controller;

import com.beautyconnect.dto.ReviewForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.ProfessionalService;
import com.beautyconnect.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Un client laisse un avis sur un professionnel apres une prestation.
 */
@Controller
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ProfessionalService professionalService;

    @PostMapping("/avis/professionnels/{professionalId}")
    public String addReview(@PathVariable Long professionalId,
                             @Valid @ModelAttribute ReviewForm reviewForm,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal CustomUserDetails principal,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // Premier message de validation du formulaire (note hors 1-5,
            // commentaire trop long...), voir les annotations de ReviewForm.
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/professionnels/" + professionalId;
        }
        ProfessionalProfile professional = professionalService.getProfileOrThrow(professionalId);
        // Les regles metier (doublon, rendez-vous non termine ou d'un autre
        // client...) sont verifiees dans ReviewService : on les affiche en
        // message flash sur la fiche du professionnel plutot qu'en page 400.
        try {
            reviewService.addReview(principal.getUser(), professional, reviewForm);
            redirectAttributes.addFlashAttribute("success", "Merci pour votre avis !");
        } catch (IllegalOperationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/professionnels/" + professionalId;
    }
}
