package com.beautyconnect.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RouteResponse {

    private Double distanceKm;
    private Double durationMinutes;
    private String geometry;

}