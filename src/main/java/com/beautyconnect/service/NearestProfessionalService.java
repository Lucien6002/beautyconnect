package com.beautyconnect.service;

import com.beautyconnect.dto.SearchCriteria;
import com.beautyconnect.exception.RoutingUnavailableException;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.utils.LocationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NearestProfessionalService {
    public record Result(List<ProfessionalProfile> profiles, Map<Long, Double> airDistances,
                         Map<Long, RoutingClient.WalkingMetric> walking, String notice) {}
    private final RoutingClient routingClient;

    public Result rank(List<ProfessionalProfile> profiles, SearchCriteria criteria) {
        List<ProfessionalProfile> ordered = new ArrayList<>(profiles);
        Map<Long, Double> air = new LinkedHashMap<>();
        Map<Long, RoutingClient.WalkingMetric> walking = new LinkedHashMap<>();
        for (ProfessionalProfile pro : ordered) {
            if (pro.isCoordinatesPublic() && LocationUtils.isValidPosition(pro.getLatitude(), pro.getLongitude())) {
                air.put(pro.getId(), LocationUtils.calculateDistance(criteria.getClientLatitude(),
                        criteria.getClientLongitude(), pro.getLatitude(), pro.getLongitude()));
            }
        }
        ordered.sort(Comparator.comparingDouble(pro -> air.getOrDefault(pro.getId(), Double.MAX_VALUE)));
        List<ProfessionalProfile> candidates = ordered.stream()
                .filter(pro -> air.containsKey(pro.getId()) && air.get(pro.getId()) <= 100).limit(12).toList();
        if (candidates.isEmpty()) return new Result(ordered, air, walking, "Aucun professionnel géolocalisé à moins de 100 km parmi ces résultats.");
        try {
            var metrics = routingClient.matrixWalking(criteria.getClientLatitude(), criteria.getClientLongitude(),
                    candidates.stream().map(pro -> new double[]{pro.getLatitude(), pro.getLongitude()}).toList());
            if (metrics.size() != candidates.size()) throw new RoutingUnavailableException();
            for (int i = 0; i < candidates.size(); i++) {
                if (metrics.get(i).distanceKm() != null && metrics.get(i).durationMinutes() != null) {
                    walking.put(candidates.get(i).getId(), metrics.get(i));
                }
            }
            ordered.sort(Comparator.<ProfessionalProfile>comparingDouble(pro -> walking.containsKey(pro.getId())
                    ? walking.get(pro.getId()).distanceKm() : Double.MAX_VALUE)
                    .thenComparingDouble(pro -> air.getOrDefault(pro.getId(), Double.MAX_VALUE)));
            return new Result(ordered, air, walking, "Les plus proches à pied parmi les " + walking.size()
                    + " résultats mesurés de cette page. Présélection de 12 professionnels au maximum dans un rayon de 100 km ; distances et durées estimées.");
        } catch (RoutingUnavailableException ex) {
            return new Result(ordered, air, walking, "Calcul piéton indisponible : classement à vol d’oiseau des résultats de cette page.");
        }
    }
}
