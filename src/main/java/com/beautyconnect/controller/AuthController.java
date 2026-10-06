package com.beautyconnect.controller;

import com.beautyconnect.dto.ClientRegistrationForm;
import com.beautyconnect.dto.ProfessionalRegistrationForm;
import com.beautyconnect.exception.EmailAlreadyUsedException;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.TargetGender;
import com.beautyconnect.service.AuthTokenService;
import com.beautyconnect.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthTokenService authTokenService;

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
                "Compte créé avec succès ! Consultez votre e-mail pour activer votre compte. Si vous ne recevez rien, demandez un nouveau lien sur cette page. Cliquez sur le lien pour activer votre compte (pensez à vérifier vos courriers indésirables / spams).");
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
                "Compte professionnel créé ! Consultez votre e-mail pour activer votre compte. Si vous ne recevez rien, demandez un nouveau lien sur cette page. Cliquez sur le lien pour activer votre compte (pensez à vérifier vos courriers indésirables / spams).");
        return "redirect:/connexion";
    }

    @PostMapping("/inscription/renvoyer-activation")
    public String resendActivation(@RequestParam String email, jakarta.servlet.http.HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        if (email.length() <= 254 && email.contains("@")) {
            synchronized (session) {
                Long last = (Long) session.getAttribute("activationResentAt");
                long now = System.currentTimeMillis();
                if (last == null || now - last >= 60_000) {
                    session.setAttribute("activationResentAt", now);
                    authTokenService.resendActivation(email);
                }
            }
        }
        redirectAttributes.addFlashAttribute("success", "Si un compte attend son activation, un nouveau lien sera envoyé. Vérifiez vos spams. Vous pouvez réessayer après une minute.");
        return "redirect:/connexion";
    }

    // Route appelée quand l'utilisateur clique sur le lien dans son e-mail
    @GetMapping("/activer-compte")
    public String activateAccount(@RequestParam("token") String token, RedirectAttributes redirectAttributes) {
        try {
            authTokenService.activateAccount(token);
            redirectAttributes.addFlashAttribute("success", "Votre compte est maintenant activé ! Vous pouvez vous connecter.");
        } catch (IllegalOperationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/connexion";
    }
}