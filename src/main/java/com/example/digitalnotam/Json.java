package com.example.digitalnotam;

import java.util.*;
import java.util.regex.*;

final class Json {
    private Json() {}

    static Map<String, String> parseObject(String body) {
        Map<String, String> values = new HashMap<>();
        Matcher m = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*(?:\\\"((?:\\\\.|[^\\\"])*)\\\"|null)").matcher(body);
        while (m.find()) values.put(m.group(1), m.group(2) == null ? "" : unescape(m.group(2)));
        return values;
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
                field("qOverrideReason",n.qOverrideReason()) + "," + field("qOverrideOperator",n.qOverrideOperator()) + "," + field("qOverrideAt",n.qOverrideAt()) + "}";
    }

    static String list(Collection<Notam> items) {
        return items.stream().map(Json::notam).reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b).transform(s -> "[" + s + "]");
    }

    static String message(String value) { return "{" + field("message", value) + "}"; }
    private static String field(String key, String value) { return "\"" + key + "\":\"" + escape(value == null ? "" : value) + "\""; }
    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"); }
    private static String unescape(String s) { return s.replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\\\", "\\"); }
}
