package com.beautyconnect.controller;

import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Report;
import com.beautyconnect.model.ReportStatus;
import com.beautyconnect.model.User;
import com.beautyconnect.service.AdminService;
import com.beautyconnect.service.ReportService;
import com.beautyconnect.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Espace administrateur : validation des comptes professionnels,
 * moderation des avis et traitement des signalements, metriques d'usage.
 *
 * Acces reserve au role ADMIN (voir SecurityConfig : "/admin/**" -> hasRole("ADMIN")).
 * (Voir ProfessionalDashboardController pour l'explication de @RequestMapping
 * au niveau classe, et AppointmentController pour le pattern
 * addFlashAttribute + "redirect:" utilise partout ci-dessous.)
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ReviewService reviewService;
    private final ReportService reportService;

    @GetMapping("/tableau-bord")
    public String dashboard(Model model) {
        model.addAttribute("metrics", adminService.getMetrics());
        model.addAttribute("pendingProfessionals", adminService.getPendingProfessionals());
        model.addAttribute("pendingReports", reportService.getPending());
        return "admin/dashboard";
    }

    // ---- Professionnels ----

    // ?statut=en-attente | valides (absent = tous, demandes en attente en premier).
    @GetMapping("/professionnels")
    public String professionals(@RequestParam(required = false) String statut, Model model) {
        Boolean validated = switch (statut == null ? "" : statut) {
            case "en-attente" -> false;
            case "valides" -> true;
            default -> null;
        };
        model.addAttribute("professionals", adminService.getProfessionals(validated));
        model.addAttribute("statut", validated == null ? "tous" : statut);
        model.addAttribute("metrics", adminService.getMetrics());
        return "admin/professionals";
    }

    // Fiche detaillee d'un professionnel : l'admin examine le profil
    // (coordonnees, bio, prestations) avant de le valider.
    @GetMapping("/professionnels/{id}")
    public String professionalDetail(@PathVariable Long id, Model model) {
        ProfessionalProfile professional = adminService.getProfessionalForReview(id);
        model.addAttribute("professional", professional);
        model.addAttribute("prestations", adminService.getPrestationsForReview(professional));
        return "admin/professional-detail";
    }

    // Les regles metier (deja valide, compte desactive...) sont verifiees dans
    // AdminService : on les transforme ici en message flash plutot qu'en page
    // d'erreur 400, l'admin reste sur la page d'ou il vient.
    @PostMapping("/professionnels/{id}/valider")
    public String validateProfessional(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ProfessionalProfile profile = adminService.validateProfessional(id);
            redirectAttributes.addFlashAttribute("success",
                    "Profil \"" + profile.getBusinessName() + "\" valide : il est desormais visible publiquement.");
        } catch (IllegalOperationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/professionnels/" + id;
    }

    @PostMapping("/professionnels/{id}/retirer-validation")
    public String revokeValidation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ProfessionalProfile profile = adminService.revokeValidation(id);
            redirectAttributes.addFlashAttribute("success",
                    "Validation retiree : \"" + profile.getBusinessName() + "\" n'apparait plus dans la recherche.");
        } catch (IllegalOperationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/professionnels/" + id;
    }

    @PostMapping("/utilisateurs/{id}/desactiver")
    public String disableUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            User user = adminService.setUserEnabled(id, false);
            redirectAttributes.addFlashAttribute("success", "Compte de " + user.getFullName()
                    + " desactive : il est deconnecte et son profil n'est plus visible publiquement.");
        } catch (IllegalOperationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/professionnels";
    }

    @PostMapping("/utilisateurs/{id}/activer")
    public String enableUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = adminService.setUserEnabled(id, true);
        redirectAttributes.addFlashAttribute("success", "Compte de " + user.getFullName() + " reactive.");
        return "redirect:/admin/professionnels";
    }

    // ---- Avis ----

    @GetMapping("/avis")
    public String reviews(Model model) {
        model.addAttribute("reviews", reviewService.getAllReviews());
        return "admin/reviews";
    }

    @PostMapping("/avis/{id}/masquer")
    public String hideReview(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        reviewService.hide(id);
        redirectAttributes.addFlashAttribute("success", "Avis masque.");
        return "redirect:/admin/avis";
    }

    // ---- Signalements ----

    @GetMapping("/signalements")
    public String reports(Model model) {
        model.addAttribute("reports", reportService.getAll());
        return "admin/reports";
    }

    @PostMapping("/signalements/{id}/traiter")
    public String treatReport(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Report report = reportService.updateStatus(id, ReportStatus.TRAITE);
        redirectAttributes.addFlashAttribute("success", "Signalement #" + report.getId() + " marque comme traite.");
        return "redirect:/admin/signalements";
    }

    @PostMapping("/signalements/{id}/rejeter")
    public String rejectReport(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        reportService.updateStatus(id, ReportStatus.REJETE);
        return "redirect:/admin/signalements";
    }
}
