package com.beautyconnect.controller;

import com.beautyconnect.model.Appointment;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Espace client : vue d'ensemble et historique des rendez-vous.
 * Acces reserve au role CLIENT (voir SecurityConfig : "/client/**" -> hasRole("CLIENT")).
 */
@Controller
@RequiredArgsConstructor
public class ClientDashboardController {

    private final AppointmentService appointmentService;

    // @AuthenticationPrincipal CustomUserDetails principal : Spring Security
    // injecte directement l'utilisateur actuellement connecte (sans avoir a
    // interroger manuellement le SecurityContext). CustomUserDetails est
    // notre adaptateur maison autour de l'entite User (voir ce fichier pour
    // le detail).
    @GetMapping("/client/tableau-bord")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        List<Appointment> appointments = appointmentService.getAppointmentsForClient(principal.getUser());
        // .stream().limit(5).toList() : n'affiche que les 5 rendez-vous les
        // plus recents sur le tableau de bord (apercu), le total complet est
        // affiche a part et le detail integral est sur la page /client/rendez-vous.
        model.addAttribute("appointments", appointments.stream().limit(5).toList());
        model.addAttribute("totalAppointments", appointments.size());
        return "client/dashboard";
    }

    @GetMapping("/client/rendez-vous")
    public String appointments(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("appointments", appointmentService.getAppointmentsForClient(principal.getUser()));
        return "client/appointments";
    }
}
