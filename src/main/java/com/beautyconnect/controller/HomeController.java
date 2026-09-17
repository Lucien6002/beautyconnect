package com.beautyconnect.controller;

import com.beautyconnect.dto.SearchCriteria;
import com.beautyconnect.model.ServiceType;
import com.beautyconnect.model.TargetGender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pages publiques generales (accueil, page d'erreur d'acces refuse).
 *
 * @Controller (et pas @RestController) : les methodes ci-dessous retournent
 * le NOM d'une vue Thymeleaf (ex: "index" -> templates/index.html), pas
 * directement du JSON/texte. C'est le mode "MVC classique" utilise dans tout
 * ce projet (Model-View-Controller) : le controleur prepare les donnees
 * (Model), Spring choisit la vue (View) a partir du nom retourne, et
 * Thymeleaf genere le HTML final envoye au navigateur.
 */
@Controller
public class HomeController {

    // @GetMapping({"/", "/accueil"}) : cette methode repond a DEUX URL
    // differentes (la racine du site et /accueil), pratique quand plusieurs
    // chemins doivent afficher exactement la meme page.
    @GetMapping({"/", "/accueil"})
    public String home(Model model) {
        // model.addAttribute(nom, valeur) : rend cette donnee accessible dans
        // le template Thymeleaf correspondant via ${nom}. Ici, on prepare un
        // objet SearchCriteria vide pour le formulaire de recherche rapide
        // affiche sur la page d'accueil, plus les listes de valeurs possibles
        // pour remplir les listes deroulantes (menus <select>) du formulaire.
        model.addAttribute("searchCriteria", new SearchCriteria());
        model.addAttribute("genders", TargetGender.values());
        model.addAttribute("types", ServiceType.values());
        return "index"; // -> src/main/resources/templates/index.html
    }

    // Page affichee quand Spring Security bloque un acces (voir
    // SecurityConfig : exceptionHandling().accessDeniedPage("/erreur/403")).
    @GetMapping("/erreur/403")
    public String accessDenied() {
        return "error/403";
    }
}
