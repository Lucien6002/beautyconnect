package com.beautyconnect.controller;

import com.beautyconnect.dto.ReportForm;
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
        // () -> reportService.create(...) est une expression lambda : une
        // "action a executer plus tard", passee en parametre a
        // handleReport() sous forme d'un Runnable (interface fonctionnelle
        // Java qui represente "une methode sans parametre ni retour").
        // Interet ici : reportReview() et reportProfile() ne different que
        // par CE QUI doit etre execute en cas de succes ; handleReport()
        // factorise tout le reste (verification des erreurs, message flash,
        // redirection) en un seul endroit au lieu de le dupliquer.
        return handleReport(bindingResult, redirectAttributes, () ->
                reportService.create(principal.getUser(), ReportTargetType.AVIS, reviewId, reportForm), null);
    }

    @PostMapping("/signalements/profils/{professionalId}")
    public String reportProfile(@PathVariable Long professionalId,
                                 @Valid @ModelAttribute ReportForm reportForm,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal CustomUserDetails principal,
                                 RedirectAttributes redirectAttributes) {
        return handleReport(bindingResult, redirectAttributes, () ->
                reportService.create(principal.getUser(), ReportTargetType.PROFIL_PROFESSIONNEL, professionalId, reportForm),
                "/professionnels/" + professionalId);
    }

    // Methode privee commune factorisant la logique partagee par les deux
    // endpoints ci-dessus. redirectOverride permet a chaque appelant de
    // choisir sa propre page de redirection (null -> valeur par defaut).
    private String handleReport(BindingResult bindingResult, RedirectAttributes redirectAttributes,
                                 Runnable action, String redirectOverride) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Merci de preciser le motif du signalement.");
        } else {
            action.run(); // execute la lambda recue : cree effectivement le signalement en base.
            redirectAttributes.addFlashAttribute("success", "Votre signalement a ete transmis a l'administrateur.");
        }
        return "redirect:" + (redirectOverride != null ? redirectOverride : "/client/tableau-bord");
    }
}
