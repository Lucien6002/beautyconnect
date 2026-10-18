package com.beautyconnect.controller;

import com.beautyconnect.dto.ProfessionalLocationForm;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/pro/localisation")
public class ProfessionalLocationController {
    private final ProfessionalService professionals;
    private final ProfessionalLocationService locations;
    @GetMapping
    public String form(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        var profile = professionals.getProfileByUserId(principal.getId());
        var form = new ProfessionalLocationForm();
        form.setLatitude(profile.getLatitude()); form.setLongitude(profile.getLongitude());
        form.setCoordinatesPublic(profile.isCoordinatesPublic());
        model.addAttribute("professionalLocationForm", form);
        return "professional/location";
    }
    @PostMapping
    public String save(@AuthenticationPrincipal CustomUserDetails principal,
                       @Valid @ModelAttribute ProfessionalLocationForm professionalLocationForm,
                       BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) return "professional/location";
        locations.update(principal.getId(), professionalLocationForm);
        flash.addFlashAttribute("success", "Votre choix de localisation publique a été enregistré.");
        return "redirect:/pro/localisation";
    }
}
