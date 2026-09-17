package com.beautyconnect.controller;

import com.beautyconnect.dto.ClientRegistrationForm;
import com.beautyconnect.dto.ProfessionalRegistrationForm;
import com.beautyconnect.exception.EmailAlreadyUsedException;
import com.beautyconnect.model.TargetGender;
import com.beautyconnect.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Pages de connexion et d'inscription (client / professionnel).
 * L'authentification elle-meme (POST /connexion) est geree par Spring
 * Security (voir SecurityConfig.formLogin()) ; ce controleur n'affiche que
 * la vue du formulaire de connexion en GET, jamais le POST qui la soumet.
 * (Voir ProfessionalDashboardController pour l'explication du pattern
 * @Valid + BindingResult utilise dans les methodes d'inscription ci-dessous.)
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/connexion")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/inscription/client")
    public String clientRegistrationForm(Model model) {
        model.addAttribute("clientRegistrationForm", new ClientRegistrationForm());
        return "auth/register-client";
    }

    @PostMapping("/inscription/client")
    public String registerClient(@Valid @ModelAttribute ClientRegistrationForm clientRegistrationForm,
                                  BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/register-client";
        }
        try {
            userService.registerClient(clientRegistrationForm);
        } catch (EmailAlreadyUsedException ex) {
            bindingResult.rejectValue("email", "email.exists", ex.getMessage());
            return "auth/register-client";
        }
        redirectAttributes.addFlashAttribute("success",
                "Votre compte a ete cree avec succes. Vous pouvez maintenant vous connecter.");
        return "redirect:/connexion";
    }

    @GetMapping("/inscription/professionnel")
    public String professionalRegistrationForm(Model model) {
        model.addAttribute("professionalRegistrationForm", new ProfessionalRegistrationForm());
        model.addAttribute("genders", TargetGender.values());
        return "auth/register-professional";
    }

    @PostMapping("/inscription/professionnel")
    public String registerProfessional(@Valid @ModelAttribute ProfessionalRegistrationForm professionalRegistrationForm,
                                        BindingResult bindingResult, Model model,
                                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("genders", TargetGender.values());
            return "auth/register-professional";
        }
        try {
            userService.registerProfessional(professionalRegistrationForm);
        } catch (EmailAlreadyUsedException ex) {
            bindingResult.rejectValue("email", "email.exists", ex.getMessage());
            model.addAttribute("genders", TargetGender.values());
            return "auth/register-professional";
        }
        redirectAttributes.addFlashAttribute("success",
                "Votre compte professionnel a ete cree. Il sera visible publiquement des sa validation par un administrateur. Vous pouvez vous connecter des maintenant.");
        return "redirect:/connexion";
    }
}
