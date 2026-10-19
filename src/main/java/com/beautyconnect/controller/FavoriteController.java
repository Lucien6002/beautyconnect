package com.beautyconnect.controller;

import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Professionnels favoris du client. Acces reserve au role CLIENT
 * (SecurityConfig : "/client/**" -> hasRole("CLIENT")).
 */
@Controller
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping("/client/favoris")
    public String list(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("favorites", favoriteService.getFavorites(principal.getId()));
        return "client/favorites";
    }

    @PostMapping("/client/favoris/{professionalId}/ajouter")
    public String add(@PathVariable Long professionalId, @AuthenticationPrincipal CustomUserDetails principal,
                      RedirectAttributes redirectAttributes) {
        favoriteService.add(principal.getId(), professionalId);
        redirectAttributes.addFlashAttribute("success", "Professionnel ajoute a vos favoris.");
        return "redirect:/professionnels/" + professionalId;
    }

    // "from=favoris" : le retrait depuis la page des favoris y revient ;
    // sinon retour sur la fiche du professionnel (valeur fixe, pas de redirection libre).
    @PostMapping("/client/favoris/{professionalId}/retirer")
    public String remove(@PathVariable Long professionalId, @RequestParam(required = false) String from,
                         @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        favoriteService.remove(principal.getId(), professionalId);
        redirectAttributes.addFlashAttribute("success", "Professionnel retire de vos favoris.");
        return "favoris".equals(from) ? "redirect:/client/favoris" : "redirect:/professionnels/" + professionalId;
    }
}
