package com.beautyconnect.controller;

import com.beautyconnect.model.Appointment;
import com.beautyconnect.model.AppointmentStatus;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Espace "Mon compte" du client, onglet "Mes rendez-vous" : rendez-vous a
 * venir puis historique. Acces reserve au role CLIENT
 * (voir SecurityConfig : "/client/**" -> hasRole("CLIENT")).
 */
@Controller
@RequiredArgsConstructor
public class ClientDashboardController {

    private final AppointmentService appointmentService;

    // @AuthenticationPrincipal CustomUserDetails principal : Spring Security
    // injecte directement l'utilisateur actuellement connecte. Les deux URL
    // affichent la meme page : /client/tableau-bord est la destination apres connexion.
    @GetMapping({"/client/tableau-bord", "/client/rendez-vous"})
    public String appointments(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        List<Appointment> all = appointmentService.getAppointmentsForClient(principal.getUser());
        LocalDateTime now = LocalDateTime.now();
        // A venir = en attente ou confirme, dont le creneau n'est pas passe.
        // Tout le reste (passes, termines, annules) va dans l'historique.
        List<Appointment> upcoming = all.stream().filter(a -> isUpcoming(a, now))
                .sorted(Comparator.comparing(a -> a.getTimeSlot().getStartDateTime())).toList();
        List<Appointment> history = all.stream().filter(a -> !isUpcoming(a, now)).toList();
        model.addAttribute("upcoming", upcoming);
        model.addAttribute("history", history);
        return "client/appointments";
    }

    private boolean isUpcoming(Appointment a, LocalDateTime now) {
        return (a.getStatus() == AppointmentStatus.EN_ATTENTE || a.getStatus() == AppointmentStatus.CONFIRME)
                && a.getTimeSlot().getStartDateTime().isAfter(now);
    }
}
