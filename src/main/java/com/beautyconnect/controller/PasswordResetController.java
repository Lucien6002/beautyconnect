package com.beautyconnect.controller;

import com.beautyconnect.dto.*;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.service.PasswordResetService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor
public class PasswordResetController {
    public static final String SENT = "Si un compte actif correspond à cette adresse, vous recevrez un lien pour choisir un nouveau mot de passe. Consultez votre boîte mail et vos spams. Vous pouvez réessayer après une minute.";
    private final PasswordResetService service;

    @ModelAttribute
    void protectPage(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
    @GetMapping("/mot-de-passe-oublie")
    public String forgot(Model model) {
        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        return "auth/forgot-password";
    }
    @PostMapping("/mot-de-passe-oublie")
    public String request(@Valid @ModelAttribute ForgotPasswordForm form, BindingResult errors,
                          HttpSession session, RedirectAttributes flash) {
        if (errors.hasErrors()) return "auth/forgot-password";
        synchronized (session) {
            Long previous = (Long) session.getAttribute("passwordResetRequestedAt");
            long now = System.currentTimeMillis();
            if (previous == null || now - previous >= 60_000) {
                session.setAttribute("passwordResetRequestedAt", now);
                service.request(form.getEmail());
            }
        }
        flash.addFlashAttribute("success", SENT);
        return "redirect:/mot-de-passe-oublie";
    }
    @GetMapping("/reinitialiser-mot-de-passe")
    public String resetPage(@RequestParam(defaultValue = "") String token, Model model) {
        var form = new ResetPasswordForm(); form.setToken(token);
        model.addAttribute("resetPasswordForm", form);
        boolean valid = service.isValid(token);
        model.addAttribute("validLink", valid);
        if (!valid) model.addAttribute("error", PasswordResetService.INVALID_LINK);
        return "auth/reset-password";
    }
    @PostMapping("/reinitialiser-mot-de-passe")
    public String reset(@Valid @ModelAttribute ResetPasswordForm form, BindingResult errors,
                        Model model, RedirectAttributes flash) {
        if (!java.util.Objects.equals(form.getNewPassword(), form.getConfirmPassword()))
            errors.rejectValue("confirmPassword", "password.mismatch", "Les deux mots de passe doivent être identiques.");
        boolean valid = service.isValid(form.getToken());
        model.addAttribute("validLink", valid);
        if (!valid) { model.addAttribute("error", PasswordResetService.INVALID_LINK); return "auth/reset-password"; }
        if (errors.hasErrors()) return "auth/reset-password";
        try { service.reset(form.getToken(), form.getNewPassword()); }
        catch (IllegalOperationException ex) {
            model.addAttribute("validLink", false); model.addAttribute("error", ex.getMessage());
            return "auth/reset-password";
        }
        flash.addFlashAttribute("success", "Votre mot de passe a été modifié. Connectez-vous avec votre nouveau mot de passe.");
        return "redirect:/connexion";
    }
}
