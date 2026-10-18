package com.beautyconnect.service;

import com.beautyconnect.dto.RouteResponse;
import com.beautyconnect.exception.RoutingUnavailableException;
import com.beautyconnect.utils.LocationUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/** ORS reste côté serveur. Cache mémoire borné, jamais de coordonnées ni de clé dans les logs. */
@Service
public class RoutingClient {
    public record WalkingMetric(Double distanceKm, Double durationMinutes) {}
    private record Cached(long expiresAt, JsonNode value) {}
    private final String apiKey;
    private final String baseUrl;
    private final RestTemplate http;
    private final JsonMapper json = new JsonMapper();
    private final Map<String, Cached> cache = new LinkedHashMap<>(16, .75f, true);
    private final Deque<Long> requests = new ArrayDeque<>();
    private final int requestsPerMinute;
    private final int cacheSeconds;

    public RoutingClient(@Value("${app.routing.api-key:}") String apiKey,
                         @Value("${app.routing.base-url:https://api.openrouteservice.org}") String baseUrl,
                         @Value("${app.routing.timeout-ms:5000}") int timeoutMs,
                         @Value("${app.routing.requests-per-minute:30}") int requestsPerMinute,
                         @Value("${app.routing.cache-seconds:300}") int cacheSeconds) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
        this.cacheSeconds = Math.max(0, cacheSeconds);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.http = new RestTemplate(factory);
    }

    public RouteResponse getWalkingRoute(double startLat, double startLon, double endLat, double endLon) {
        validate(startLat, startLon);
        validate(endLat, endLon);
        if (LocationUtils.calculateDistance(startLat, startLon, endLat, endLon) > 100) {
            throw new RoutingUnavailableException();
        }
        JsonNode result = post("/v2/directions/foot-walking/geojson", Map.of(
                "coordinates", List.of(List.of(startLon, startLat), List.of(endLon, endLat)),
                "instructions", false));
        try {
            JsonNode feature = result.path("features").get(0);
            JsonNode geometry = feature.path("geometry");
            JsonNode summary = feature.path("properties").path("summary");
            if (!"LineString".equals(geometry.path("type").asText())
                    || geometry.path("coordinates").size() < 2 || geometry.path("coordinates").size() > 10000) {
                throw new RoutingUnavailableException();
            }
            for (JsonNode point : geometry.path("coordinates")) {
                if (point.size() < 2 || !point.get(0).isNumber() || !point.get(1).isNumber()
                        || !LocationUtils.isValidPosition(point.get(1).asDouble(), point.get(0).asDouble())) {
                    throw new RoutingUnavailableException();
                }
            }
            return RouteResponse.builder().distanceKm(number(summary.path("distance")) / 1000)
                    .durationMinutes(number(summary.path("duration")) / 60)
                    .geometry(json.writeValueAsString(geometry)).build();
        } catch (RuntimeException ex) {
            throw new RoutingUnavailableException();
        }
    }

    public List<WalkingMetric> matrixWalking(double lat, double lon, List<double[]> destinations) {
        validate(lat, lon);
        if (destinations.isEmpty()) return List.of();
        if (destinations.size() > 12) throw new RoutingUnavailableException();
        List<List<Double>> locations = new ArrayList<>();
        locations.add(List.of(lon, lat));
        List<String> indexes = new ArrayList<>();
        for (double[] destination : destinations) {
            validate(destination[0], destination[1]);
            locations.add(List.of(destination[1], destination[0]));
            indexes.add(Integer.toString(indexes.size() + 1));
        }
        JsonNode result = post("/v2/matrix/foot-walking", Map.of("locations", locations,
                "sources", List.of("0"), "destinations", indexes,
                "metrics", List.of("distance", "duration"), "units", "m"));
        try {
            JsonNode distances = result.path("distances").get(0);
            JsonNode durations = result.path("durations").get(0);
            if (distances.size() != destinations.size() || durations.size() != destinations.size()) {
                throw new RoutingUnavailableException();
            }
            List<WalkingMetric> metrics = new ArrayList<>();
            for (int i = 0; i < destinations.size(); i++) {
                metrics.add(distances.get(i).isNull() || durations.get(i).isNull()
                        ? new WalkingMetric(null, null)
                        : new WalkingMetric(number(distances.get(i)) / 1000, number(durations.get(i)) / 60));
            }
            return metrics;
        } catch (RuntimeException ex) {
            throw new RoutingUnavailableException();
        }
    }

    private static double number(JsonNode node) {
        if (!node.isNumber() || !Double.isFinite(node.asDouble()) || node.asDouble() < 0) {
            throw new RoutingUnavailableException();
        }
        return node.asDouble();
    }

    private static void validate(double lat, double lon) {
        if (!LocationUtils.isValidPosition(lat, lon)) throw new IllegalArgumentException("Coordonnées invalides");
    }

    private JsonNode post(String path, Map<String, ?> payload) {
        if (apiKey.isBlank()) throw new RoutingUnavailableException();
        String body = json.writeValueAsString(payload);
        String cacheKey = path + body;
        long now = System.currentTimeMillis();
        synchronized (cache) {
            cache.values().removeIf(entry -> entry.expiresAt() <= now);
            Cached hit = cache.get(cacheKey);
            if (hit != null) return hit.value();
        }
        synchronized (requests) {
            while (!requests.isEmpty() && requests.peekFirst() <= now - 60_000) requests.removeFirst();
            if (requests.size() >= requestsPerMinute) throw new RoutingUnavailableException();
            requests.addLast(now);
        }
        try {
            JsonNode response = http.execute(baseUrl + path, HttpMethod.POST, request -> {
                request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                request.getHeaders().set("Authorization", apiKey);
                request.getBody().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }, result -> {
                byte[] bytes = result.getBody().readNBytes(1_000_001);
                if (bytes.length > 1_000_000) throw new RoutingUnavailableException();
                return json.readTree(bytes);
            });
            if (response == null) throw new RoutingUnavailableException();
            synchronized (cache) {
                if (cache.size() >= 128) cache.remove(cache.keySet().iterator().next());
                cache.put(cacheKey, new Cached(now + cacheSeconds * 1000L, response));
            }
            return response;
        } catch (RuntimeException ex) {
            // Aucun corps fournisseur, secret ou URL avec origine n'est journalisé/renvoyé.
            throw new RoutingUnavailableException();
        }
    }
}
