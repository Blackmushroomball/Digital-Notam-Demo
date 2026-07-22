package com.example.digitalnotam.scenario.adlim;

import com.example.digitalnotam.baseline.BaselineAirportHeliportCatalog;
import org.w3c.dom.Element;

final class AdLimQCodeResolver {
    String resolve(Element availability, boolean heliport) {
        Element usage = BaselineAirportHeliportCatalog.first(availability, "AirportHeliportUsage");
        String type = BaselineAirportHeliportCatalog.text(usage, "type");
        String operation = BaselineAirportHeliportCatalog.text(usage, "operation");
        boolean scheduled = !BaselineAirportHeliportCatalog.directChildren(availability, "timeInterval").isEmpty();
        boolean ppr = presentValue(usage, "priorPermission");
        Element flight = BaselineAirportHeliportCatalog.first(usage, "FlightCharacteristic");
        Element aircraft = BaselineAirportHeliportCatalog.first(usage, "AircraftCharacteristic");
        String suffix;
        if (scheduled && "PERMIT".equals(type) && "ALL".equals(operation) && !ppr && flight == null && aircraft == null) suffix = "AH";
        else if ("RESERV".equals(type) && only(flight, "military", "MIL") && aircraft == null) suffix = "AM";
        else if ("CONDITIONAL".equals(type) && ppr) suffix = "AP";
        else if ("PERMIT".equals(type) && !ppr) suffix = "AR";
        else if ("RESERV".equals(type) && only(flight, "origin", "HOME_BASED") && aircraft == null) suffix = "LB";
        else if ("FORBID".equals(type) && presentValue(aircraft, "weight")) suffix = heliport ? "LT" : "LH";
        else if ("FORBID".equals(type) && flight != null && "IFR".equals(BaselineAirportHeliportCatalog.text(flight, "rule"))) suffix = "LI";
        else if ("FORBID".equals(type) && flight != null && "VFR".equals(BaselineAirportHeliportCatalog.text(flight, "rule"))) suffix = "LV";
        else suffix = "LT";
        return "QF" + (heliport ? "P" : "A") + suffix;
    }

    private static boolean presentValue(Element parent, String local) {
        if (parent == null) return false;
        Element e = BaselineAirportHeliportCatalog.first(parent, local);
        return e != null && !e.getTextContent().trim().isBlank();
    }

    private static boolean only(Element element, String local, String value) {
        if (element == null || !value.equals(BaselineAirportHeliportCatalog.text(element, local))) return false;
        int populated = 0;
        for (String field : new String[]{"type","rule","status","military","origin","purpose"})
            if (!BaselineAirportHeliportCatalog.text(element, field).isBlank()) populated++;
        return populated == 1;
    }
}
