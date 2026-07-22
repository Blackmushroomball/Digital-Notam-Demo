package com.example.digitalnotam.domain;

public record AdLimRestriction(
        String limitationType, String operation,
        String flightType, String flightRule, String flightStatus, String flightMilitary, String flightOrigin, String flightPurpose,
        String aircraftType, String aircraftEngine, String aircraftWingSpan, String aircraftWingSpanUom, String aircraftWingSpanInterpretation,
        String aircraftWeight, String aircraftWeightUom, String aircraftWeightInterpretation,
        String pprValue, String pprUnit, String pprDetails) {
    public AdLimRestriction {
        limitationType = value(limitationType); operation = value(operation);
        flightType=value(flightType);flightRule=value(flightRule);flightStatus=value(flightStatus);flightMilitary=value(flightMilitary);flightOrigin=value(flightOrigin);flightPurpose=value(flightPurpose);
        aircraftType=value(aircraftType);aircraftEngine=value(aircraftEngine);aircraftWingSpan=value(aircraftWingSpan);aircraftWingSpanUom=value(aircraftWingSpanUom);aircraftWingSpanInterpretation=value(aircraftWingSpanInterpretation);
        aircraftWeight=value(aircraftWeight);aircraftWeightUom=value(aircraftWeightUom);aircraftWeightInterpretation=value(aircraftWeightInterpretation);pprValue=value(pprValue);pprUnit=value(pprUnit);pprDetails=value(pprDetails);
    }
    private static String value(String value){return value==null?"":value.trim();}
}
