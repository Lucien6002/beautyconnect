package com.beautyconnect.controller;

import com.beautyconnect.dto.AppointmentForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.AppointmentService;
import com.beautyconnect.service.ProfessionalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Prise de rendez-vous par un client (depuis la fiche publique d'un
 * professionnel) et annulation d'un rendez-vous existant.
 */
@Controller
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final ProfessionalService professionalService;

    @PostMapping("/rendez-vous/reserver/{professionalId}")
    public String book(@PathVariable Long professionalId,
                        @Valid @ModelAttribute AppointmentForm appointmentForm,
                        BindingResult bindingResult,
                        @AuthenticationPrincipal CustomUserDetails principal,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Merci de choisir une prestation et un creneau valides.");
            return "redirect:/professionnels/" + professionalId;
        }
        try {
            ProfessionalProfile professional = professionalService.getPublicProfileOrThrow(professionalId);
            appointmentService.book(principal.getUser(), professional, appointmentForm);
            redirectAttributes.addFlashAttribute("success",
                    "Votre rendez-vous est confirme. Un mail de confirmation vous a ete envoye. "
                            + "Vous pouvez l'annuler depuis \"Mes rendez-vous\" en cas d'empechement.");
            return "redirect:/client/rendez-vous";
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "Ce creneau vient d'etre reserve, merci d'en choisir un autre.");
        } catch (IllegalOperationException ex) {
            // Erreurs "metier" detectees plus tard, au niveau du service (ex:
            // creneau deja pris entre le chargement de la page et la
            // soumission du formulaire) : contrairement aux erreurs de
            // BindingResult (format des champs), celles-ci ne peuvent etre
            // detectees qu'en verifiant l'etat reel de la base de donnees.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/professionnels/" + professionalId;
    }

    @PostMapping("/rendez-vous/{id}/annuler")
    public String cancel(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                          RedirectAttributes redirectAttributes) {
        appointmentService.cancelByClient(principal.getUser(), id);
        redirectAttributes.addFlashAttribute("success", "Le rendez-vous a ete annule.");
        return "redirect:/client/rendez-vous";
    }
}
