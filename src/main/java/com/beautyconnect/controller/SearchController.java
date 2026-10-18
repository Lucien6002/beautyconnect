package com.beautyconnect.controller;

import com.beautyconnect.dto.SearchCriteria;
import com.beautyconnect.model.*;
import com.beautyconnect.repository.ReviewRepository;
import com.beautyconnect.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class SearchController {
    private final ProfessionalService professionalService;
    private final NearestProfessionalService nearestService;
    private final ReviewRepository reviewRepository;
    @org.springframework.beans.factory.annotation.Value("${app.map.tile-url:https://tile.openstreetmap.org/{z}/{x}/{y}.png}")
    private String mapTileUrl;

    @GetMapping("/recherche")
    public String search(@Valid @ModelAttribute SearchCriteria searchCriteria, BindingResult errors,
                         Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "strict-origin");
        model.addAttribute("mapTileUrl", mapTileUrl);
        // La position n'est ni enregistrée en base ni ajoutée à la journalisation.
        var page = errors.hasErrors() ? org.springframework.data.domain.Page.<ProfessionalProfile>empty()
                : professionalService.searchPage(searchCriteria);
        List<ProfessionalProfile> results = page.getContent();
        Map<Long, Double> air = Map.of();
        Map<Long, RoutingClient.WalkingMetric> walking = Map.of();
        String notice = null;
        if (!errors.hasErrors() && searchCriteria.getClientLatitude() != null) {
            var ranked = nearestService.rank(results, searchCriteria);
            results = ranked.profiles(); air = ranked.airDistances(); walking = ranked.walking(); notice = ranked.notice();
        }
        Map<Long, Double> ratings = new LinkedHashMap<>();
        results.forEach(pro -> ratings.put(pro.getId(), 0.0));
        if (!results.isEmpty()) reviewRepository.averageRatings(results.stream().map(ProfessionalProfile::getId).toList())
                .forEach(r -> ratings.put(r.getProfessionalId(), r.getAverage()));
        model.addAttribute("results", results);
        model.addAttribute("resultPage", page);
        model.addAttribute("averageRatings", ratings);
        model.addAttribute("distances", air);
        model.addAttribute("walking", walking);
        model.addAttribute("routingNotice", notice);
        model.addAttribute("genders", TargetGender.values());
        model.addAttribute("types", ServiceType.values());
        if (errors.hasErrors()) {
            model.addAttribute("error", "Filtres invalides : vérifiez les coordonnées, la longueur des champs et le numéro de page.");
            response.setStatus(400);
        }
        return "search/results";
    }
}
