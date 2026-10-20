package com.beautyconnect.controller;

import com.beautyconnect.dto.PrestationForm;
import com.beautyconnect.dto.TimeSlotForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.Appointment;
import com.beautyconnect.model.AppointmentStatus;
import com.beautyconnect.model.Prestation;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
    private final com.beautyconnect.service.PhotoService photoService;

    @GetMapping("/tableau-bord")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        // principal.getId() : ID du User connecte ; on en deduit son
        // ProfessionalProfile associe (relation 1-1, voir ProfessionalService.getProfileByUserId).
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        model.addAttribute("professional", profile);
        model.addAttribute("prestationsCount", professionalService.getActivePrestations(profile).size());
        model.addAttribute("upcomingSlots", professionalService.getAvailableTimeSlots(profile).size());

        List<Appointment> all = appointmentService.getAppointmentsForProfessional(profile);
        List<Appointment> completed = all.stream().filter(a -> a.getStatus() == AppointmentStatus.TERMINE).toList();
        model.addAttribute("completedCount", completed.size());
        // Chiffre d'affaires du mois en cours : somme des tarifs des rendez-vous
        // termines (realises) dont le creneau tombe dans le mois courant.
        YearMonth currentMonth = YearMonth.now();
        BigDecimal monthlyRevenue = completed.stream()
                .filter(a -> YearMonth.from(a.getTimeSlot().getStartDateTime()).equals(currentMonth))
                .map(a -> a.getPrestation().getPrice())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
        model.addAttribute("monthlyRevenue", monthlyRevenue);
        model.addAttribute("currentMonth", currentMonth.atDay(1));

        // Seuls les rendez-vous qui ont abouti (hors annules/refuses) sont listes ici.
        model.addAttribute("appointments", all.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.ANNULE && a.getStatus() != AppointmentStatus.REFUSE)
                .limit(5).toList());
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
        model.addAttribute("prestationForm", new PrestationForm());
        addPrestationsPageAttributes(profile, model);
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
                                 RedirectAttributes redirectAttributes,
                                 @RequestParam(name = "file", required = false) MultipartFile file,
                                 @RequestParam(name = "secondaryPhotos", required = false) List<MultipartFile> secondaryPhotos) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        if (bindingResult.hasErrors()) {
            addPrestationsPageAttributes(profile, model);
            return "professional/prestations";
        }
        try {
            professionalService.addPrestationWithPhotos(profile, prestationForm, file, secondaryPhotos);
        } catch (com.beautyconnect.service.storage.ImageUploadException | IllegalOperationException ex) {
            bindingResult.reject("photos.invalid", ex.getMessage());
            addPrestationsPageAttributes(profile, model);
            return "professional/prestations";
        }

        redirectAttributes.addFlashAttribute("success", "Prestation ajoutee.");
        return "redirect:/pro/prestations";
    }

    @GetMapping("/prestations/{id}/modifier")
    public String editPrestation(@PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        var p = photoService.ownedPrestation(principal.getId(), id);
        var form = new PrestationForm();
        form.setName(p.getName()); form.setDescription(p.getDescription()); form.setType(p.getType());
        form.setPrice(p.getPrice()); form.setDurationMinutes(p.getDurationMinutes());
        model.addAttribute("prestationForm", form);
        model.addAttribute("prestationId", id);
        model.addAttribute("types", ServiceType.values());
        return "professional/prestation-edit";
    }

    @PostMapping("/prestations/{id}/modifier")
    public String updatePrestation(@PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @ModelAttribute PrestationForm prestationForm, BindingResult binding, Model model, RedirectAttributes flash) {
        photoService.ownedPrestation(principal.getId(), id);
        if (binding.hasErrors()) {
            model.addAttribute("prestationId", id); model.addAttribute("types", ServiceType.values());
            return "professional/prestation-edit";
        }
        professionalService.updatePrestation(professionalService.getProfileByUserId(principal.getId()), id, prestationForm);
        flash.addFlashAttribute("success", "La prestation a été mise à jour.");
        return "redirect:/pro/prestations";
    }

    @PostMapping("/prestations/{id}/supprimer")
    public String removePrestation(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable("id") Long id,
                                    RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        professionalService.removePrestation(profile, id);
        redirectAttributes.addFlashAttribute("success",
                "Prestation retiree. Vous pouvez la restaurer depuis \"Prestations supprimees\".");
        return "redirect:/pro/prestations";
    }

    @PostMapping("/prestations/{id}/supprimer-definitivement")
    public String permanentlyDeletePrestation(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable("id") Long id,
                                             RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        try {
            professionalService.permanentlyDeletePrestation(profile, id);
            redirectAttributes.addFlashAttribute("success", "Prestation supprimee definitivement.");
        } catch (IllegalOperationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pro/prestations/supprimees";
    }

    // Prestations retirees par le pro, avec un bouton pour les restaurer.
    @GetMapping("/prestations/supprimees")
    public String removedPrestations(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        model.addAttribute("prestations", professionalService.getRemovedPrestations(profile));
        return "professional/prestations-removed";
    }

    @PostMapping("/prestations/{id}/restaurer")
    public String restorePrestation(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable("id") Long id,
                                     RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        try {
            Prestation prestation = professionalService.restorePrestation(profile, id);
            redirectAttributes.addFlashAttribute("success",
                    "Prestation \"" + prestation.getName() + "\" restauree : elle est de nouveau proposee aux clients.");
        } catch (IllegalOperationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/pro/prestations/supprimees";
    }

    // Attributs communs a l'affichage de la page prestations (premier
    // affichage et reaffichage apres une erreur de validation) : seules les
    // prestations actives sont listees, les retirees sont sur une page a part.
    private void addPrestationsPageAttributes(ProfessionalProfile profile, Model model) {
        model.addAttribute("prestations", professionalService.getActivePrestations(profile));
        model.addAttribute("removedCount", professionalService.countRemovedPrestations(profile));
        model.addAttribute("types", ServiceType.values());
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
        if (!bindingResult.hasErrors()) {
            try {
                professionalService.addTimeSlot(profile, timeSlotForm);
                redirectAttributes.addFlashAttribute("success", "Creneau ajoute.");
                return "redirect:/pro/creneaux";
            } catch (IllegalOperationException ex) {
                // Doublon detecte par le service : on l'attache au champ du
                // formulaire pour l'afficher sous la date, comme une erreur
                // de validation classique (la date saisie reste remplie).
                bindingResult.rejectValue("startDateTime", "duplicate", ex.getMessage());
            }
        }
        model.addAttribute("timeSlots", professionalService.getAllTimeSlots(profile));
        return "professional/slots";
    }

    @PostMapping("/creneaux/{id}/supprimer")
    public String removeTimeSlot(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable("id") Long id,
                                  RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        try {
            professionalService.removeTimeSlot(profile, id);
            redirectAttributes.addFlashAttribute("success", "Creneau supprime.");
        } catch (IllegalOperationException ex) {
            // Message affiche sur la page des creneaux plutot que page d'erreur 400.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/pro/creneaux";
    }

    // ---- Agenda ----

    /** Un jour de la semaine de l'agenda, avec ses rendez-vous tries par heure. */
    public record AgendaDay(LocalDate date, String label, boolean today, List<Appointment> appointments) {}

    // Semaine en cours (lundi a dimanche). Alimente directement par les rendez-vous
    // en base : chaque nouvelle reservation y apparait au chargement suivant.
    // Les rendez-vous annules/refuses n'y figurent pas (le creneau est libere).
    @GetMapping("/agenda")
    public String agenda(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Appointment> active = appointmentService.getAppointmentsForProfessional(profile).stream()
                .filter(a -> a.getStatus() != AppointmentStatus.ANNULE && a.getStatus() != AppointmentStatus.REFUSE)
                .toList();
        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);
        List<AgendaDay> days = monday.datesUntil(monday.plusDays(7)).map(date -> new AgendaDay(
                date, labelFormat.format(date), date.equals(today),
                active.stream().filter(a -> a.getTimeSlot().getStartDateTime().toLocalDate().equals(date))
                        .sorted(Comparator.comparing(a -> a.getTimeSlot().getStartDateTime())).toList())).toList();
        model.addAttribute("days", days);
        model.addAttribute("weekStart", monday);
        model.addAttribute("weekEnd", monday.plusDays(6));
        model.addAttribute("weekCount", days.stream().mapToInt(d -> d.appointments().size()).sum());
        return "professional/agenda";
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
    @PostMapping("/rendez-vous/{id}/terminer")
    public String completeAppointment(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable("id") Long id,
                                       RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        appointmentService.markCompleted(profile, id);
        redirectAttributes.addFlashAttribute("success", "Rendez-vous marque comme termine.");
        return "redirect:/pro/rendez-vous";
    }
}
