package com.beautyconnect.service;

import com.beautyconnect.exception.RoutingUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class RoutingClientTests {
    HttpServer server;
    AtomicInteger calls;
    String payload;
    String authorization;
    int status;
    String response;
    String url;

    @BeforeEach void setup() throws Exception {
        calls = new AtomicInteger(); status = 200;
        response = "{\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[2,48],[2.01,48.01]]},\"properties\":{\"summary\":{\"distance\":1500,\"duration\":1080}}}]}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start(); url = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { server.stop(0); }
    private RoutingClient client(int quota) { return new RoutingClient("test-key", url, 5000, quota, 300); }

    @Test void directionsUsesLonLatParsesUnitsAndCaches() {
        var client = client(30);
        var route = client.getWalkingRoute(48, 2, 48.01, 2.01);
        assertThat(route.getDistanceKm()).isEqualTo(1.5);
        assertThat(route.getDurationMinutes()).isEqualTo(18);
        assertThat(authorization).isEqualTo("test-key");
        assertThat(payload).contains("[2.0,48.0]");
        assertThat(route.getGeometry()).contains("LineString").doesNotContain("test-key");
        client.getWalkingRoute(48, 2, 48.01, 2.01); assertThat(calls.get()).isEqualTo(1);
    }
    @Test void matrixParsesUnreachableResults() {
        response = "{\"distances\":[[1500,null]],\"durations\":[[1080,null]]}";
        var metrics = client(30).matrixWalking(48, 2, List.of(new double[]{48.01,2.01}, new double[]{48.02,2.02}));
        assertThat(metrics.get(0).distanceKm()).isEqualTo(1.5);
        assertThat(metrics.get(1).distanceKm()).isNull();
        assertThat(payload).contains("\"sources\":[\"0\"]").contains("\"destinations\":[\"1\",\"2\"]");
    }
    @Test void missingKeyQuotaAndProviderErrorsHaveNoFakeRoute() {
        assertThatThrownBy(() -> new RoutingClient("", url, 5000, 30, 300).getWalkingRoute(48,2,48.01,2.01))
                .isInstanceOf(RoutingUnavailableException.class);
        assertThat(calls.get()).isZero();
        var client = client(1); client.getWalkingRoute(48,2,48.01,2.01);
        assertThatThrownBy(() -> client.getWalkingRoute(48,2,48.02,2.02)).isInstanceOf(RoutingUnavailableException.class);
        status = 429;
        assertThatThrownBy(() -> client(30).getWalkingRoute(48,2,48.01,2.01))
                .isInstanceOf(RoutingUnavailableException.class).hasMessageNotContaining("test-key");
    }
    @Test void malformedAndOversizedResponsesAreRejected() {
        response = "{}";
        assertThatThrownBy(() -> client(30).getWalkingRoute(48,2,48.01,2.01)).isInstanceOf(RoutingUnavailableException.class);
        response = " ".repeat(1_000_001);
        assertThatThrownBy(() -> client(30).getWalkingRoute(48,2,48.01,2.01)).isInstanceOf(RoutingUnavailableException.class);
    }
}
