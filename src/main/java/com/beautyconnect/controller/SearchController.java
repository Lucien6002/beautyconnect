package com.beautyconnect.controller;

import com.beautyconnect.dto.SearchCriteria;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.ServiceType;
import com.beautyconnect.model.TargetGender;
import com.beautyconnect.service.ProfessionalService;
import com.beautyconnect.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moteur de recherche public des professionnels : filtre par ville
 * (recherche geolocalisee simple), par sexe de la clientele visee et par
 * type de prestation.
 *
 * @RequiredArgsConstructor (Lombok) : genere le constructeur qui recoit les
 * champs "final" ci-dessous ; Spring les injecte automatiquement (voir
 * l'explication complete dans UserService).
 */
@Controller
@RequiredArgsConstructor
public class SearchController {

    private final ProfessionalService professionalService;
    private final ReviewService reviewService;

    // @ModelAttribute sur le parametre : Spring MVC construit automatiquement
    // un objet SearchCriteria en lisant les parametres de la query string de
    // l'URL (ex: /recherche?city=Paris&gender=FEMME) et en remplissant les
    // champs correspondants par reflexion (setCity("Paris"), setGender(FEMME)...).
    // On n'a donc pas besoin de lire manuellement chaque parametre un par un.
    @GetMapping("/recherche")
    public String search(@ModelAttribute SearchCriteria searchCriteria, Model model) {
        List<ProfessionalProfile> results = professionalService.search(searchCriteria);

        // Precalcule la note moyenne de chaque professionnel trouve, pour
        // eviter de refaire la requete depuis le template Thymeleaf (les
        // templates ne devraient jamais contenir de logique d'acces aux donnees).
        // LinkedHashMap conserve l'ordre d'insertion, donc l'ordre des
        // resultats de recherche est preserve a l'affichage.
        Map<Long, Double> averageRatings = new LinkedHashMap<>();
        for (ProfessionalProfile pro : results) {
            averageRatings.put(pro.getId(), reviewService.getAverageRating(pro));
        }

        model.addAttribute("searchCriteria", searchCriteria); // pour reafficher les filtres choisis dans le formulaire
        model.addAttribute("results", results);
        model.addAttribute("averageRatings", averageRatings);
        model.addAttribute("genders", TargetGender.values());
        model.addAttribute("types", ServiceType.values());
        return "search/results";
    }
}
