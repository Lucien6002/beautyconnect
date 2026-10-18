package com.beautyconnect.controller;

import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.exception.RoutingUnavailableException;
import com.beautyconnect.service.ProfessionalService;
import com.beautyconnect.service.RoutingClient;
import com.beautyconnect.utils.LocationUtils;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayDeque;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/itineraire")
public class RouteController {
    private final ProfessionalService professionalService;
    private final RoutingClient routingClient;

    @GetMapping("/professionnels/{id}")
    public ResponseEntity<?> route(@PathVariable Long id, @RequestParam Double lat,
                                   @RequestParam Double lon, HttpSession session) {
        if (!LocationUtils.isValidPosition(lat, lon)) return error(400, "Coordonnées invalides");
        synchronized (session) {
            @SuppressWarnings("unchecked")
            var times = (ArrayDeque<Long>) session.getAttribute("routingRequestTimes");
            if (times == null) { times = new ArrayDeque<>(); session.setAttribute("routingRequestTimes", times); }
            long now = System.currentTimeMillis();
            while (!times.isEmpty() && times.peekFirst() <= now - 60_000) times.removeFirst();
            if (times.size() >= 10) return ResponseEntity.status(429).header("Retry-After", "60")
                    .cacheControl(CacheControl.noStore()).body(Map.of("message", "Trop de demandes. Réessayez dans une minute."));
            times.addLast(now);
        }
        try {
            var pro = professionalService.getPublicProfileOrThrow(id);
            if (!pro.isCoordinatesPublic() || !LocationUtils.isValidPosition(pro.getLatitude(), pro.getLongitude())) {
                return error(404, "Ce professionnel n’a pas de coordonnées publiables.");
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(routingClient.getWalkingRoute(lat, lon, pro.getLatitude(), pro.getLongitude()));
        } catch (ResourceNotFoundException ex) {
            return error(404, "Professionnel introuvable");
        } catch (RoutingUnavailableException ex) {
            return error(503, ex.getMessage());
        }
    }

    private ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", message));
    }
}
