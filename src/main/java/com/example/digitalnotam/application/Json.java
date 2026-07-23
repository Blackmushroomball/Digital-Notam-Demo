package com.example.digitalnotam.application;

import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.domain.AdLimRestriction;
import com.example.digitalnotam.domain.NavUnsData;
import java.util.*;
import java.util.regex.*;

final class Json {
    private Json() {}

    static Map<String, String> parseObject(String body) {
        Map<String, String> values = new HashMap<>();
        Matcher m = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(?:\\\"((?:\\\\.|[^\\\"])*)\\\"|(-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|true|false)|null)").matcher(body);
        while (m.find()) values.put(m.group(1), m.group(2) != null ? unescape(m.group(2)) : m.group(3) == null ? "" : m.group(3));
        return values;
    }

    static List<Map<String,String>> parseObjectArray(String body,String key){
        Matcher keyMatcher=Pattern.compile("\\\""+Pattern.quote(key)+"\\\"\\s*:\\s*\\[").matcher(body);if(!keyMatcher.find())return List.of();
        List<Map<String,String>> result=new ArrayList<>();int depth=0,start=-1;boolean string=false,escape=false;
        for(int i=keyMatcher.end();i<body.length();i++){char c=body.charAt(i);if(string){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='\"')string=false;continue;}if(c=='\"'){string=true;continue;}if(c=='{'){if(depth++==0)start=i;}else if(c=='}'&&--depth==0&&start>=0){result.add(parseObject(body.substring(start,i+1)));start=-1;}else if(c==']'&&depth==0)break;}
        return result;
    }

    static String notam(Notam n) {
        return "{" + field("id", n.id()) + "," + field("number", n.number()) + "," + field("scenario", n.scenario()) + "," +
                field("title", n.title()) + "," + field("airport", n.airport()) + "," +
                field("featureType", n.featureType()) + "," + field("condition", n.condition()) + "," + field("selectedRunways",n.selectedRunways()) + "," + field("selectedTaxiways",n.selectedTaxiways()) + "," +
                field("eventDescription",n.eventDescription()) + "," + field("reason",n.reason()) + "," + field("remarks",n.remarks()) + "," +
                field("effectiveStart", n.effectiveStart()) + "," + field("effectiveEnd", n.effectiveEnd()) + "," +
                field("latitude", n.latitude()) + "," + field("longitude", n.longitude()) + "," +
                field("radiusNm", n.radiusNm()) + "," + field("latitudeHemisphere", n.latitudeHemisphere()) + "," +
                field("longitudeHemisphere", n.longitudeHemisphere()) + "," + field("qCode", n.qCode()) + "," +
                field("traffic", n.traffic()) + "," + field("purpose", n.purpose()) + "," + field("scope", n.scope()) + "," +
                field("lowerMeters", n.lowerMeters()) + "," + field("upperMeters", n.upperMeters()) + "," +
                field("minimumFl", n.minimumFl()) + "," + field("maximumFl", n.maximumFl()) + "," + field("scheduleMode",n.scheduleMode()) + "," + field("scheduleDay", n.scheduleDay()) + "," +
                field("scheduleStart", n.scheduleStart()) + "," + field("scheduleEnd", n.scheduleEnd()) + "," + field("status", n.status()) + "," +
                field("createdAt", n.createdAt()) + "," + field("publishedAt", n.publishedAt()) + "," + field("fir",n.fir()) + "," +
                field("scheduleStartDate",n.scheduleStartDate()) + "," + field("scheduleEndDate",n.scheduleEndDate()) + "," +
                field("qOverrideReason",n.qOverrideReason()) + "," + field("qOverrideOperator",n.qOverrideOperator()) + "," + field("qOverrideAt",n.qOverrideAt()) + "," +
                field("limitationType",n.limitationType()) + "," + field("operation",n.operation()) + "," +
                field("flightType",n.flightType()) + "," + field("flightRule",n.flightRule()) + "," + field("flightStatus",n.flightStatus()) + "," + field("flightMilitary",n.flightMilitary()) + "," + field("flightOrigin",n.flightOrigin()) + "," + field("flightPurpose",n.flightPurpose()) + "," +
                field("aircraftType",n.aircraftType()) + "," + field("aircraftEngine",n.aircraftEngine()) + "," + field("aircraftWingSpan",n.aircraftWingSpan()) + "," + field("aircraftWingSpanUom",n.aircraftWingSpanUom()) + "," + field("aircraftWingSpanInterpretation",n.aircraftWingSpanInterpretation()) + "," +
                field("aircraftWeight",n.aircraftWeight()) + "," + field("aircraftWeightUom",n.aircraftWeightUom()) + "," + field("aircraftWeightInterpretation",n.aircraftWeightInterpretation()) + "," + field("pprValue",n.pprValue()) + "," + field("pprUnit",n.pprUnit()) + "," + field("pprDetails",n.pprDetails()) + "," + field("rwyTargetType",n.rwyTargetType()) + "," + field("runwayUuid",n.runwayUuid()) + "," + field("runwayDirectionUuid",n.runwayDirectionUuid()) + "," + field("airspaceGroupId",n.airspaceGroupId()) + "," + field("selectedAirspaces",n.selectedAirspaces()) + "," + field("activationStatus",n.activationStatus()) + "," + field("affectedAirports",n.affectedAirports()) + "," + field("additionalFirs",n.additionalFirs()) + ",\"navUnsData\":" + navUns(n.navUnsData()) + ",\"restrictions\":" + restrictions(n.adLimRestrictions()) + "}";
    }

    static String list(Collection<Notam> items) {
        return items.stream().map(Json::notam).reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b).transform(s -> "[" + s + "]");
    }

    static String message(String value) { return "{" + field("message", value) + "}"; }
    private static String restrictions(List<AdLimRestriction> values){return values.stream().map(r->"{"+field("limitationType",r.limitationType())+","+field("operation",r.operation())+","+field("flightType",r.flightType())+","+field("flightRule",r.flightRule())+","+field("flightStatus",r.flightStatus())+","+field("flightMilitary",r.flightMilitary())+","+field("flightOrigin",r.flightOrigin())+","+field("flightPurpose",r.flightPurpose())+","+field("aircraftType",r.aircraftType())+","+field("aircraftEngine",r.aircraftEngine())+","+field("aircraftWingSpan",r.aircraftWingSpan())+","+field("aircraftWingSpanUom",r.aircraftWingSpanUom())+","+field("aircraftWingSpanInterpretation",r.aircraftWingSpanInterpretation())+","+field("aircraftWeight",r.aircraftWeight())+","+field("aircraftWeightUom",r.aircraftWeightUom())+","+field("aircraftWeightInterpretation",r.aircraftWeightInterpretation())+","+field("pprValue",r.pprValue())+","+field("pprUnit",r.pprUnit())+","+field("pprDetails",r.pprDetails())+"}").reduce((a,b)->a+","+b).map(x->"["+x+"]").orElse("[]");}
    private static String navUns(NavUnsData value){NavUnsData v=value==null?NavUnsData.empty():value;return "{"+field("navaidUuid",v.navaidUuid())+","+field("impactMode",v.impactMode())+","+field("equipmentUuid",v.equipmentUuid())+","+field("signalType",v.signalType())+","+field("operationalStatus",v.operationalStatus())+",\"signalStillEmitted\":"+v.signalStillEmitted()+"}";}
    private static String field(String key, String value) { return "\"" + key + "\":\"" + escape(value == null ? "" : value) + "\""; }
    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"); }
    private static String unescape(String s) { return s.replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\\\", "\\"); }
}
