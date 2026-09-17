package com.beautyconnect.controller;

import com.beautyconnect.dto.PrestationForm;
import com.beautyconnect.dto.TimeSlotForm;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.ServiceType;
import com.beautyconnect.model.TargetGender;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.AppointmentService;
import com.beautyconnect.service.ProfessionalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Espace professionnel : gestion du profil vitrine, des prestations
 * (avec duree et tarif), des creneaux de disponibilite, et des
 * rendez-vous recus (confirmation = envoi du mail au client).
 *
 * @RequestMapping("/pro") au niveau de la classe : prefixe TOUTES les URL de
 * ce controleur par "/pro" (ex: @GetMapping("/profil") repond en realite a
 * "/pro/profil"). Evite de repeter le prefixe sur chaque methode.
 *
 * Acces reserve au role PROFESSIONAL (voir SecurityConfig : "/pro/**" -> hasRole("PROFESSIONAL")).
 */
@Controller
@RequestMapping("/pro")
@RequiredArgsConstructor
public class ProfessionalDashboardController {

    private final ProfessionalService professionalService;
    private final AppointmentService appointmentService;

    @GetMapping("/tableau-bord")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        // principal.getId() : ID du User connecte ; on en deduit son
        // ProfessionalProfile associe (relation 1-1, voir ProfessionalService.getProfileByUserId).
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        model.addAttribute("professional", profile);
        model.addAttribute("prestationsCount", professionalService.getActivePrestations(profile).size());
        model.addAttribute("upcomingSlots", professionalService.getAvailableTimeSlots(profile).size());
        model.addAttribute("appointments", appointmentService.getAppointmentsForProfessional(profile).stream().limit(5).toList());
        return "professional/dashboard";
    }

    // ---- Profil ----

    @GetMapping("/profil")
    public String editProfileForm(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("professional", professionalService.getProfileByUserId(principal.getId()));
        model.addAttribute("genders", TargetGender.values());
        return "professional/profile-edit";
    }

    // @RequestParam : recupere chaque champ du formulaire HTML individuellement
    // par son attribut "name" (approche alternative a @ModelAttribute qui,
    // elle, remplit un objet DTO entier d'un coup - voir addPrestation plus bas).
    // (required = false) : le champ est optionnel, sinon Spring rejetterait
    // la requete si le champ est absent du formulaire.
    @PostMapping("/profil")
    public String updateProfile(@AuthenticationPrincipal CustomUserDetails principal,
                                 @RequestParam String businessName,
                                 @RequestParam(required = false) String bio,
                                 @RequestParam String city,
                                 @RequestParam(required = false) String address,
                                 @RequestParam TargetGender targetGender,
                                 RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        professionalService.updateProfile(profile, businessName, bio, city, address, targetGender);
        // addFlashAttribute (et pas addAttribute) : la donnee survit UNE seule
        // redirection HTTP (pattern Post/Redirect/Get). Indispensable ici car
        // apres un POST on renvoie un "redirect:" (nouvelle requete GET), et un
        // model.addAttribute classique serait perdu entre les deux requetes.
        redirectAttributes.addFlashAttribute("success", "Votre profil a ete mis a jour.");
        return "redirect:/pro/profil";
    }

    // ---- Prestations ----

    @GetMapping("/prestations")
    public String prestations(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        model.addAttribute("prestations", professionalService.getPrestations(profile));
        model.addAttribute("prestationForm", new PrestationForm());
        model.addAttribute("types", ServiceType.values());
        return "professional/prestations";
    }

    // @Valid : demande a Spring de verifier les annotations de validation
    // presentes sur PrestationForm (ex: @NotBlank, @Positive - voir ce
    // fichier) avant meme d'entrer dans le corps de la methode. Le resultat
    // de cette verification est recupere juste apres via BindingResult
    // (qui DOIT etre le parametre suivant immediatement l'objet valide).
    @PostMapping("/prestations")
    public String addPrestation(@AuthenticationPrincipal CustomUserDetails principal,
                                 @Valid @ModelAttribute PrestationForm prestationForm,
                                 BindingResult bindingResult, Model model,
                                 RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        if (bindingResult.hasErrors()) {
            // En cas d'erreur de validation, on REAFFICHE le meme formulaire
            // (pas de redirect) pour que Thymeleaf puisse montrer les messages
            // d'erreur a cote des champs invalides ; il faut donc repeupler
            // manuellement les autres attributs necessaires a la vue.
            model.addAttribute("prestations", professionalService.getPrestations(profile));
            model.addAttribute("types", ServiceType.values());
            return "professional/prestations";
        }
        professionalService.addPrestation(profile, prestationForm);
        redirectAttributes.addFlashAttribute("success", "Prestation ajoutee.");
        return "redirect:/pro/prestations";
    }

    @PostMapping("/prestations/{id}/supprimer")
    public String removePrestation(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id,
                                    RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        professionalService.removePrestation(profile, id);
        redirectAttributes.addFlashAttribute("success", "Prestation supprimee.");
        return "redirect:/pro/prestations";
    }

    // ---- Creneaux ----

    @GetMapping("/creneaux")
    public String timeSlots(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        model.addAttribute("timeSlots", professionalService.getAllTimeSlots(profile));
        model.addAttribute("timeSlotForm", new TimeSlotForm());
        return "professional/slots";
    }

    @PostMapping("/creneaux")
    public String addTimeSlot(@AuthenticationPrincipal CustomUserDetails principal,
                               @Valid @ModelAttribute TimeSlotForm timeSlotForm,
                               BindingResult bindingResult, Model model,
                               RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        if (bindingResult.hasErrors()) {
            model.addAttribute("timeSlots", professionalService.getAllTimeSlots(profile));
            return "professional/slots";
        }
        professionalService.addTimeSlot(profile, timeSlotForm);
        redirectAttributes.addFlashAttribute("success", "Creneau ajoute.");
        return "redirect:/pro/creneaux";
    }

    @PostMapping("/creneaux/{id}/supprimer")
    public String removeTimeSlot(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        professionalService.removeTimeSlot(profile, id);
        redirectAttributes.addFlashAttribute("success", "Creneau supprime.");
        return "redirect:/pro/creneaux";
    }

    // ---- Rendez-vous ----

    @GetMapping("/rendez-vous")
    public String appointments(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        model.addAttribute("appointments", appointmentService.getAppointmentsForProfessional(profile));
        return "professional/appointments";
    }

    // Les 3 methodes suivantes suivent toutes le meme schema : recuperer le
    // profil du pro connecte, appeler le service (qui verifie en interne que
    // le rendez-vous appartient bien a ce professionnel), puis rediriger avec
    // un message flash de confirmation.
    @PostMapping("/rendez-vous/{id}/confirmer")
    public String confirmAppointment(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id,
                                      RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        appointmentService.confirm(profile, id);
        redirectAttributes.addFlashAttribute("success", "Rendez-vous confirme. Un mail a ete envoye au client.");
        return "redirect:/pro/rendez-vous";
    }

    @PostMapping("/rendez-vous/{id}/refuser")
    public String refuseAppointment(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id,
                                     RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        appointmentService.refuse(profile, id);
        redirectAttributes.addFlashAttribute("success", "Rendez-vous refuse.");
        return "redirect:/pro/rendez-vous";
    }

    @PostMapping("/rendez-vous/{id}/terminer")
    public String completeAppointment(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id,
                                       RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        appointmentService.markCompleted(profile, id);
        redirectAttributes.addFlashAttribute("success", "Rendez-vous marque comme termine.");
        return "redirect:/pro/rendez-vous";
    }
}
