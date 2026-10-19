package com.beautyconnect.controller;

import com.beautyconnect.dto.ReportForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.ReportTargetType;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.ReportService;
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
 * Signalement d'un avis ou d'un profil professionnel par un client
 * (moderation ensuite geree par l'administrateur).
 */
@Controller
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/signalements/avis/{reviewId}")
    public String reportReview(@PathVariable Long reviewId,
                                @Valid @ModelAttribute ReportForm reportForm,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal CustomUserDetails principal,
                                RedirectAttributes redirectAttributes) {
        return handleReport(principal, ReportTargetType.AVIS, reviewId, reportForm, bindingResult, redirectAttributes);
    }

    @PostMapping("/signalements/profils/{professionalId}")
    public String reportProfile(@PathVariable Long professionalId,
                                 @Valid @ModelAttribute ReportForm reportForm,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal CustomUserDetails principal,
                                 RedirectAttributes redirectAttributes) {
        return handleReport(principal, ReportTargetType.PROFIL_PROFESSIONNEL, professionalId, reportForm,
                bindingResult, redirectAttributes);
    }

    // Methode privee commune aux deux endpoints ci-dessus, qui ne different
    // que par le type de cible signalee.
    //  - Erreur de formulaire (motif vide ou trop long) : premier message de
    //    validation de ReportForm.
    //  - Regle metier (cible inexistante ou invisible, signalement deja en
    //    attente) : verifiee dans ReportService, affichee en message flash
    //    plutot qu'en page 400.
    // Redirection : la fiche du professionnel concerne si elle est visible
    // (le client y etait), sinon son espace client.
    private String handleReport(CustomUserDetails principal, ReportTargetType targetType, Long targetId,
                                ReportForm reportForm, BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
        } else {
            try {
                reportService.create(principal.getUser(), targetType, targetId, reportForm);
                redirectAttributes.addFlashAttribute("success", "Votre signalement a ete transmis a l'administrateur.");
            } catch (IllegalOperationException ex) {
                redirectAttributes.addFlashAttribute("error", ex.getMessage());
            }
        }
        return reportService.findPublicProfessionalIdOf(targetType, targetId)
                .map(id -> "redirect:/professionnels/" + id)
                .orElse("redirect:/client/tableau-bord");
    }
}
