package com.beautyconnect.controller;

import com.beautyconnect.dto.PasswordChangeForm;
import com.beautyconnect.dto.PersonalInfoForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.User;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Modification des informations personnelles (prenom, nom, telephone) par
 * l'utilisateur connecte, qu'il soit client ou professionnel.
 */
@Controller
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;

    @GetMapping("/compte")
    public String form(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        User user = userService.getById(principal.getId());
        PersonalInfoForm form = new PersonalInfoForm();
        form.setFirstName(user.getFirstName());
        form.setLastName(user.getLastName());
        form.setPhone(user.getPhone());
        model.addAttribute("personalInfoForm", form);
        model.addAttribute("email", user.getEmail());
        return "account/personal-info";
    }

    @GetMapping("/compte/mot-de-passe")
    public String passwordForm(Model model) {
        model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        return "account/change-password";
    }

    @PostMapping("/compte/mot-de-passe")
    public String changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                                 @Valid @ModelAttribute PasswordChangeForm passwordChangeForm,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("newPassword") && !bindingResult.hasFieldErrors("confirmPassword")
                && !passwordChangeForm.getNewPassword().equals(passwordChangeForm.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Les deux mots de passe ne correspondent pas");
        }
        if (!bindingResult.hasErrors()) {
            try {
                userService.changePassword(principal.getId(), passwordChangeForm.getCurrentPassword(),
                        passwordChangeForm.getNewPassword());
                redirectAttributes.addFlashAttribute("success", "Votre mot de passe a ete modifie.");
                return "redirect:/compte";
            } catch (IllegalOperationException ex) {
                String field = ex.getMessage().contains("actuel est incorrect") ? "currentPassword" : "newPassword";
                bindingResult.rejectValue(field, "invalid", ex.getMessage());
            }
        }
        // On ne renvoie jamais les mots de passe saisis dans la page.
        passwordChangeForm.setCurrentPassword(null);
        passwordChangeForm.setNewPassword(null);
        passwordChangeForm.setConfirmPassword(null);
        return "account/change-password";
    }

    @PostMapping("/compte")
    public String update(@AuthenticationPrincipal CustomUserDetails principal,
                         @Valid @ModelAttribute PersonalInfoForm personalInfoForm,
                         BindingResult bindingResult, Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("email", userService.getById(principal.getId()).getEmail());
            return "account/personal-info";
        }
        userService.updatePersonalInfo(principal.getId(), personalInfoForm);
        redirectAttributes.addFlashAttribute("success", "Vos informations personnelles ont ete mises a jour.");
        return "redirect:/compte";
    }
}
