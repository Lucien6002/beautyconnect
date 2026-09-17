package com.beautyconnect.controller;

import com.beautyconnect.model.Report;
import com.beautyconnect.model.ReportStatus;
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

    @GetMapping("/professionnels")
    public String professionals(Model model) {
        model.addAttribute("professionals", adminService.getAllProfessionals());
        return "admin/professionals";
    }

    @PostMapping("/professionnels/{id}/valider")
    public String validateProfessional(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        adminService.validateProfessional(id);
        redirectAttributes.addFlashAttribute("success", "Profil professionnel valide.");
        return "redirect:/admin/professionnels";
    }

    @PostMapping("/utilisateurs/{id}/desactiver")
    public String disableUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        adminService.setUserEnabled(id, false);
        redirectAttributes.addFlashAttribute("success", "Compte desactive.");
        return "redirect:/admin/professionnels";
    }

    @PostMapping("/utilisateurs/{id}/activer")
    public String enableUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        adminService.setUserEnabled(id, true);
        redirectAttributes.addFlashAttribute("success", "Compte reactive.");
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
