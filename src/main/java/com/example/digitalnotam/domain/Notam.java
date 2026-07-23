package com.example.digitalnotam.domain;

import java.time.Instant;
import java.util.List;

public record Notam(
        String id, String number, String scenario, String title, String airport, String featureType,
        String condition, String selectedRunways, String selectedTaxiways, String eventDescription, String reason, String remarks, String effectiveStart, String effectiveEnd,
        String latitude, String longitude, String radiusNm,
        String latitudeHemisphere, String longitudeHemisphere,
        String qCode, String traffic, String purpose, String scope,
        String lowerMeters, String upperMeters,
        String scheduleMode, String scheduleDay, String scheduleStart, String scheduleEnd, String status,
        String createdAt, String publishedAt,
        String fir, String scheduleStartDate, String scheduleEndDate,
        String qOverrideReason, String qOverrideOperator, String qOverrideAt,
        String limitationType, String operation,
        String flightType, String flightRule, String flightStatus, String flightMilitary, String flightOrigin, String flightPurpose,
        String aircraftType, String aircraftEngine, String aircraftWingSpan, String aircraftWingSpanUom, String aircraftWingSpanInterpretation,
        String aircraftWeight, String aircraftWeightUom, String aircraftWeightInterpretation,
        String pprValue, String pprUnit, String pprDetails,
        List<AdLimRestriction> adLimRestrictions,
        String rwyTargetType, String runwayUuid, String runwayDirectionUuid,
        String airspaceGroupId, String selectedAirspaces, String activationStatus,
        String affectedAirports, String additionalFirs,
        NavUnsData navUnsData) {

    /** Backwards-compatible constructor for scenario builders created before Airspace fields existed. */
    public Notam(String id,String number,String scenario,String title,String airport,String featureType,
                 String condition,String selectedRunways,String selectedTaxiways,String eventDescription,String reason,String remarks,
                 String effectiveStart,String effectiveEnd,String latitude,String longitude,String radiusNm,
                 String latitudeHemisphere,String longitudeHemisphere,String qCode,String traffic,String purpose,String scope,
                 String lowerMeters,String upperMeters,String scheduleMode,String scheduleDay,String scheduleStart,String scheduleEnd,
                 String status,String createdAt,String publishedAt,String fir,String scheduleStartDate,String scheduleEndDate,
                 String qOverrideReason,String qOverrideOperator,String qOverrideAt,String limitationType,String operation,
                 String flightType,String flightRule,String flightStatus,String flightMilitary,String flightOrigin,String flightPurpose,
                 String aircraftType,String aircraftEngine,String aircraftWingSpan,String aircraftWingSpanUom,String aircraftWingSpanInterpretation,
                 String aircraftWeight,String aircraftWeightUom,String aircraftWeightInterpretation,String pprValue,String pprUnit,String pprDetails,
                 List<AdLimRestriction> adLimRestrictions,String rwyTargetType,String runwayUuid,String runwayDirectionUuid){
        this(id,number,scenario,title,airport,featureType,condition,selectedRunways,selectedTaxiways,eventDescription,reason,remarks,
                effectiveStart,effectiveEnd,latitude,longitude,radiusNm,latitudeHemisphere,longitudeHemisphere,qCode,traffic,purpose,scope,
                lowerMeters,upperMeters,scheduleMode,scheduleDay,scheduleStart,scheduleEnd,status,createdAt,publishedAt,fir,scheduleStartDate,scheduleEndDate,
                qOverrideReason,qOverrideOperator,qOverrideAt,limitationType,operation,flightType,flightRule,flightStatus,flightMilitary,flightOrigin,flightPurpose,
                aircraftType,aircraftEngine,aircraftWingSpan,aircraftWingSpanUom,aircraftWingSpanInterpretation,aircraftWeight,aircraftWeightUom,aircraftWeightInterpretation,
                pprValue,pprUnit,pprDetails,adLimRestrictions,rwyTargetType,runwayUuid,runwayDirectionUuid,"","","","","",NavUnsData.empty());
    }

    public Notam(String id, String number, String scenario, String title, String airport, String featureType,
                 String condition, String selectedRunways, String selectedTaxiways, String eventDescription,
                 String reason, String remarks, String effectiveStart, String effectiveEnd, String latitude,
                 String longitude, String radiusNm, String latitudeHemisphere, String longitudeHemisphere,
                 String qCode, String traffic, String purpose, String scope, String lowerMeters, String upperMeters,
                 String scheduleMode, String scheduleDay, String scheduleStart, String scheduleEnd, String status,
                 String createdAt, String publishedAt) {
        this(id, number, scenario, title, airport, featureType, condition, selectedRunways, selectedTaxiways,
                eventDescription, reason, remarks, effectiveStart, effectiveEnd, latitude, longitude, radiusNm,
                latitudeHemisphere, longitudeHemisphere, qCode, traffic, purpose, scope, lowerMeters, upperMeters,
                scheduleMode, scheduleDay, scheduleStart, scheduleEnd, status, createdAt, publishedAt,
                "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", List.of(),"","","",
                "","","","","",NavUnsData.empty());
    }

    public Notam(String id, String number, String scenario, String title, String airport, String featureType,
                 String condition, String selectedRunways, String selectedTaxiways, String eventDescription, String reason, String remarks,
                 String effectiveStart, String effectiveEnd, String latitude, String longitude, String radiusNm,
                 String latitudeHemisphere, String longitudeHemisphere, String qCode, String traffic, String purpose, String scope,
                 String lowerMeters, String upperMeters, String scheduleMode, String scheduleDay, String scheduleStart, String scheduleEnd,
                 String status, String createdAt, String publishedAt, String fir, String scheduleStartDate, String scheduleEndDate,
                 String qOverrideReason, String qOverrideOperator, String qOverrideAt) {
        this(id,number,scenario,title,airport,featureType,condition,selectedRunways,selectedTaxiways,eventDescription,reason,remarks,
                effectiveStart,effectiveEnd,latitude,longitude,radiusNm,latitudeHemisphere,longitudeHemisphere,qCode,traffic,purpose,scope,
                lowerMeters,upperMeters,scheduleMode,scheduleDay,scheduleStart,scheduleEnd,status,createdAt,publishedAt,fir,scheduleStartDate,scheduleEndDate,
                qOverrideReason,qOverrideOperator,qOverrideAt,"","","","","","","","","","","","","","","","","","","",List.of(),"","","",
                "","","","","",NavUnsData.empty());
    }

    public Notam publish() {
        return new Notam(id, number, scenario, title, airport, featureType, condition, selectedRunways, selectedTaxiways, eventDescription, reason, remarks,
                effectiveStart, effectiveEnd, latitude, longitude, radiusNm, latitudeHemisphere, longitudeHemisphere,
                qCode, traffic, purpose, scope, lowerMeters, upperMeters, scheduleMode, scheduleDay, scheduleStart, scheduleEnd,
                "PUBLISHED", createdAt, Instant.now().toString(), fir, scheduleStartDate, scheduleEndDate,
                qOverrideReason, qOverrideOperator, qOverrideAt,
                limitationType, operation, flightType, flightRule, flightStatus, flightMilitary, flightOrigin, flightPurpose,
                aircraftType, aircraftEngine, aircraftWingSpan, aircraftWingSpanUom, aircraftWingSpanInterpretation, aircraftWeight, aircraftWeightUom, aircraftWeightInterpretation,
                pprValue, pprUnit, pprDetails, adLimRestrictions,rwyTargetType,runwayUuid,runwayDirectionUuid,
                airspaceGroupId,selectedAirspaces,activationStatus,affectedAirports,additionalFirs,navUnsData);
    }

    public String minimumFl() { return FlightLevelConverter.toFl(lowerMeters, false); }
    public String maximumFl() { return FlightLevelConverter.toFl(upperMeters, true); }
    public Notam withAdLimRestrictions(List<AdLimRestriction> values){return new Notam(id,number,scenario,title,airport,featureType,condition,selectedRunways,selectedTaxiways,eventDescription,reason,remarks,effectiveStart,effectiveEnd,latitude,longitude,radiusNm,latitudeHemisphere,longitudeHemisphere,qCode,traffic,purpose,scope,lowerMeters,upperMeters,scheduleMode,scheduleDay,scheduleStart,scheduleEnd,status,createdAt,publishedAt,fir,scheduleStartDate,scheduleEndDate,qOverrideReason,qOverrideOperator,qOverrideAt,limitationType,operation,flightType,flightRule,flightStatus,flightMilitary,flightOrigin,flightPurpose,aircraftType,aircraftEngine,aircraftWingSpan,aircraftWingSpanUom,aircraftWingSpanInterpretation,aircraftWeight,aircraftWeightUom,aircraftWeightInterpretation,pprValue,pprUnit,pprDetails,List.copyOf(values),rwyTargetType,runwayUuid,runwayDirectionUuid,airspaceGroupId,selectedAirspaces,activationStatus,affectedAirports,additionalFirs,navUnsData);}
    public Notam withRwyClsTarget(String targetType,String runway,String direction){return new Notam(id,number,scenario,title,airport,featureType,condition,selectedRunways,selectedTaxiways,eventDescription,reason,remarks,effectiveStart,effectiveEnd,latitude,longitude,radiusNm,latitudeHemisphere,longitudeHemisphere,qCode,traffic,purpose,scope,lowerMeters,upperMeters,scheduleMode,scheduleDay,scheduleStart,scheduleEnd,status,createdAt,publishedAt,fir,scheduleStartDate,scheduleEndDate,qOverrideReason,qOverrideOperator,qOverrideAt,limitationType,operation,flightType,flightRule,flightStatus,flightMilitary,flightOrigin,flightPurpose,aircraftType,aircraftEngine,aircraftWingSpan,aircraftWingSpanUom,aircraftWingSpanInterpretation,aircraftWeight,aircraftWeightUom,aircraftWeightInterpretation,pprValue,pprUnit,pprDetails,adLimRestrictions,targetType,runway,direction,airspaceGroupId,selectedAirspaces,activationStatus,affectedAirports,additionalFirs,navUnsData);}
    public Notam withAtsaAct(String group,String airspaces,String activation,String airports,String firs){return new Notam(id,number,scenario,title,airport,featureType,condition,selectedRunways,selectedTaxiways,eventDescription,reason,remarks,effectiveStart,effectiveEnd,latitude,longitude,radiusNm,latitudeHemisphere,longitudeHemisphere,qCode,traffic,purpose,scope,lowerMeters,upperMeters,scheduleMode,scheduleDay,scheduleStart,scheduleEnd,status,createdAt,publishedAt,fir,scheduleStartDate,scheduleEndDate,qOverrideReason,qOverrideOperator,qOverrideAt,limitationType,operation,flightType,flightRule,flightStatus,flightMilitary,flightOrigin,flightPurpose,aircraftType,aircraftEngine,aircraftWingSpan,aircraftWingSpanUom,aircraftWingSpanInterpretation,aircraftWeight,aircraftWeightUom,aircraftWeightInterpretation,pprValue,pprUnit,pprDetails,adLimRestrictions,rwyTargetType,runwayUuid,runwayDirectionUuid,group,airspaces,activation,airports,firs,navUnsData);}
    public Notam withNavUns(NavUnsData value){return new Notam(id,number,scenario,title,airport,featureType,condition,selectedRunways,selectedTaxiways,eventDescription,reason,remarks,effectiveStart,effectiveEnd,latitude,longitude,radiusNm,latitudeHemisphere,longitudeHemisphere,qCode,traffic,purpose,scope,lowerMeters,upperMeters,scheduleMode,scheduleDay,scheduleStart,scheduleEnd,status,createdAt,publishedAt,fir,scheduleStartDate,scheduleEndDate,qOverrideReason,qOverrideOperator,qOverrideAt,limitationType,operation,flightType,flightRule,flightStatus,flightMilitary,flightOrigin,flightPurpose,aircraftType,aircraftEngine,aircraftWingSpan,aircraftWingSpanUom,aircraftWingSpanInterpretation,aircraftWeight,aircraftWeightUom,aircraftWeightInterpretation,pprValue,pprUnit,pprDetails,adLimRestrictions,rwyTargetType,runwayUuid,runwayDirectionUuid,airspaceGroupId,selectedAirspaces,activationStatus,affectedAirports,additionalFirs,value);}
}
