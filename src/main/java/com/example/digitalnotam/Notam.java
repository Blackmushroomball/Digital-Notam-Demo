package com.example.digitalnotam;

import java.time.Instant;

public record Notam(
        String id, String number, String scenario, String title, String airport, String featureType,
        String condition, String selectedRunways, String selectedTaxiways, String eventDescription, String reason, String remarks, String effectiveStart, String effectiveEnd,
        String latitude, String longitude, String radiusNm,
        String latitudeHemisphere, String longitudeHemisphere,
        String qCode, String traffic, String purpose, String scope,
        String lowerMeters, String upperMeters,
        String scheduleMode, String scheduleDay, String scheduleStart, String scheduleEnd, String status,
        String createdAt, String publishedAt) {

    public Notam publish() {
        return new Notam(id, number, scenario, title, airport, featureType, condition, selectedRunways, selectedTaxiways, eventDescription, reason, remarks,
                effectiveStart, effectiveEnd, latitude, longitude, radiusNm, latitudeHemisphere, longitudeHemisphere,
                qCode, traffic, purpose, scope, lowerMeters, upperMeters, scheduleMode, scheduleDay, scheduleStart, scheduleEnd,
                "PUBLISHED", createdAt, Instant.now().toString());
    }

    public String minimumFl() { return FlightLevelConverter.toFl(lowerMeters, false); }
    public String maximumFl() { return FlightLevelConverter.toFl(upperMeters, true); }
}
