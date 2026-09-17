package com.beautyconnect.controller;

import com.beautyconnect.dto.ReviewForm;
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
            redirectAttributes.addFlashAttribute("error", "Merci de renseigner une note valide (1 a 5).");
            return "redirect:/professionnels/" + professionalId;
        }
        ProfessionalProfile professional = professionalService.getProfileOrThrow(professionalId);
        reviewService.addReview(principal.getUser(), professional, reviewForm);
        redirectAttributes.addFlashAttribute("success", "Merci pour votre avis !");
        return "redirect:/professionnels/" + professionalId;
    }
}
