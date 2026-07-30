package com.example.digitalnotam.domain;

import java.util.Locale;

/**
 * Input fields that are specific to the ATSA.NEW scenario.
 *
 * <p>{@code geometryJson} is the canonical geometry value. Both the structured
 * editor and the advanced JSON editor use this same contract.</p>
 */
public record AtsaNewData(
        String type,
        String classification,
        String designator,
        String name,
        String activationStatus,
        String locationNote,
        String geometryJson,
        String lowerValue,
        String lowerUom,
        String lowerReference,
        String upperValue,
        String upperUom,
        String upperReference,
        String controllingUnitNote,
        String note,
        String excludedAirspaces,
        String nearbyAirportThresholdNm) {

    public AtsaNewData {
        type = code(type);
        classification = code(classification);
        designator = code(designator);
        name = code(name);
        activationStatus = code(activationStatus);
        locationNote = text(locationNote);
        geometryJson = text(geometryJson);
        lowerValue = code(lowerValue);
        lowerUom = code(lowerUom);
        lowerReference = code(lowerReference);
        upperValue = code(upperValue);
        upperUom = code(upperUom);
        upperReference = code(upperReference);
        controllingUnitNote = text(controllingUnitNote);
        note = text(note);
        excludedAirspaces = text(excludedAirspaces);
        nearbyAirportThresholdNm = text(nearbyAirportThresholdNm);
    }

    public static AtsaNewData empty() {
        return new AtsaNewData("", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "5");
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** AIXM Character3-backed code/name properties use uppercase characters. */
    private static String code(String value) {
        return text(value).toUpperCase(Locale.ROOT);
    }
}
