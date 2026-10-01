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

        Map<Long, Double> averageRatings = new LinkedHashMap<>();
        Map<Long, Double> distances = new LinkedHashMap<>(); // <-- NOUVEAU : Contiendra les distances

        for (ProfessionalProfile pro : results) {
            averageRatings.put(pro.getId(), reviewService.getAverageRating(pro));

            // Si le client a envoyé sa position et que le pro a des coordonnées GPS
            if (searchCriteria.getClientLatitude() != null && searchCriteria.getClientLongitude() != null
                    && pro.getLatitude() != null && pro.getLongitude() != null) {

                double dist = com.beautyconnect.utils.LocationUtils.calculateDistance(
                        searchCriteria.getClientLatitude(), searchCriteria.getClientLongitude(),
                        pro.getLatitude(), pro.getLongitude()
                );
                distances.put(pro.getId(), dist);
            }
        }

        model.addAttribute("searchCriteria", searchCriteria);
        model.addAttribute("results", results);
        model.addAttribute("averageRatings", averageRatings);
        model.addAttribute("distances", distances); // <-- ON L'ENVOIE À LA VUE !
        model.addAttribute("genders", TargetGender.values());
        model.addAttribute("types", ServiceType.values());
        return "search/results";
    }
}
