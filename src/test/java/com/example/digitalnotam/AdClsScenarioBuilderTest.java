package com.example.digitalnotam;

import org.w3c.dom.*;
import java.lang.reflect.Method;
import java.time.Instant;

public final class AdClsScenarioBuilderTest {
    public static void main(String[] args) throws Exception {
        Document minimal = new AdClsScenarioBuilder().build(notam("CONTINUOUS", "", "", ""));
        assertText(minimal, "scenario", "AD.CLS");
        assertText(minimal, "version", "2.0");
        assertText(minimal, "interpretation", "BASELINE");
        if (countStatus(minimal, "NORMAL") == 0 || countStatus(minimal, "CLOSED") != 1)
            throw new AssertionError("TEMPDELTA availability structure is invalid");
        Element closed = closed(minimal);
        if (!BaselineAirportHeliportCatalog.directChildren(closed, "timeInterval").isEmpty())
            throw new AssertionError("Continuous closure must not contain a closure Timesheet");
        if (!BaselineAirportHeliportCatalog.directChildren(closed, "annotation").isEmpty())
            throw new AssertionError("Empty optional annotations must not be encoded");

        Document scheduled = new AdClsScenarioBuilder().build(notam("WEEKDAYS", "MON,WED", "WIP", "TERMINAL WORK"));
        Element scheduledClosed = closed(scheduled);
        assertEventAvailabilityCommentPosition(scheduled);
        if (BaselineAirportHeliportCatalog.directChildren(scheduledClosed, "timeInterval").size() != 2)
            throw new AssertionError("Expected one Timesheet per selected day");
        if (BaselineAirportHeliportCatalog.directChildren(scheduledClosed, "annotation").size() != 2)
            throw new AssertionError("Expected reason and note annotations");
        if (!"operationalStatus".equals(BaselineAirportHeliportCatalog.text(
                BaselineAirportHeliportCatalog.directChildren(scheduledClosed, "annotation").get(0), "propertyName")))
            throw new AssertionError("Closure reason is not linked to operationalStatus");

        String firstId = CommonDigitalNotamBuilder.one(minimal, CommonDigitalNotamBuilder.EVENT, "Event")
                .getAttributeNS(CommonDigitalNotamBuilder.GML, "id");
        String secondId = CommonDigitalNotamBuilder.one(scheduled, CommonDigitalNotamBuilder.EVENT, "Event")
                .getAttributeNS(CommonDigitalNotamBuilder.GML, "id");
        if (firstId.equals(secondId)) throw new AssertionError("Each publication must create a new Event");
        var producer = new AdClsNotamProducer();
        if (!"QFALC".equals(producer.produce(minimal, notam("CONTINUOUS", "", "", "")).qCode()))
            throw new AssertionError("AD/AH must produce QFALC");
        Notam hp = withAirport(notam("CONTINUOUS", "", "", ""), "EADH");
        Document hpDocument = new AdClsScenarioBuilder().build(hp);
        if (!"QFPLC".equals(producer.produce(hpDocument, hp).qCode()))
            throw new AssertionError("HP must produce QFPLC");
        BaselineAirportHeliportCatalog airportCatalog=new BaselineAirportHeliportCatalog();
        if(airportCatalog.list().size()!=5)throw new AssertionError("Expected five supported airports");
        String airportJson=airportCatalog.json();if(!airportJson.startsWith("[{\"designator\":")||airportJson.contains("\\\""))throw new AssertionError("Airport endpoint must return valid JSON without escaped object quotes");
        Document daily=new AdClsScenarioBuilder().build(notam("DAILY","","",""));producer.produce(daily,notam("DAILY","","",""));
        Notam dates=withDates(notam("DATES","","",""),"2026-08-01","2026-08-02");Document datesDocument=new AdClsScenarioBuilder().build(dates);producer.produce(datesDocument,dates);
        Method xsd = DigitalNotamPipeline.class.getDeclaredMethod("validateXsd", Document.class);
        xsd.setAccessible(true);
        xsd.invoke(null, minimal);
        xsd.invoke(null, scheduled);
        xsd.invoke(null,daily);xsd.invoke(null,datesDocument);xsd.invoke(null,hpDocument);
        Method header=DigitalNotamPipeline.class.getDeclaredMethod("replaceHeaderComments",Document.class,String.class);header.setAccessible(true);header.invoke(null,scheduled,"AD.CLS");Method serialize=DigitalNotamPipeline.class.getDeclaredMethod("serialize",Document.class);serialize.setAccessible(true);String xml=(String)serialize.invoke(null,scheduled);
        var blank=java.util.regex.Pattern.compile("(?:\\r?\\n)[ \\t]*(?:\\r?\\n)").matcher(xml);if(blank.find())throw new AssertionError("Serialized XML must not contain blank lines near: "+xml.substring(Math.max(0,blank.start()-60),Math.min(xml.length(),blank.end()+60)).replace("\r","\\r").replace("\n","\\n"));
        if(!xml.contains("<!-- Schedule -->")||!xml.contains("<!-- Closure Reason -->")||!xml.contains("<!-- Note -->")||xml.contains("Digital NOTAM module:"))throw new AssertionError("AD.CLS comments must follow the virtual-data template");
        System.out.println("AD.CLS scenario tests passed");
    }

    private static Notam notam(String mode, String days, String reason, String remarks) {
        return new Notam("test", "A9999/26", "AD.CLS", "EADD DONLON/INTL.", "EADD", "AIRPORT_HELIPORT",
                "AD CLSD", "", "", "CLSD", reason, remarks,
                "2026-08-01T00:00:00Z", "2026-08-02T00:00:00Z", "52.38", "31.95", "5", "N", "W",
                "QFALC", "IV", "NBO", "A", "", "", mode, days, "08:00", "12:00",
                "PUBLISHED", Instant.now().toString(), "2026-07-20T00:00:00Z");
    }

    private static Notam withAirport(Notam n, String airport) {
        return new Notam(n.id(), n.number(), n.scenario(), n.title(), airport, n.featureType(), n.condition(),
                n.selectedRunways(), n.selectedTaxiways(), n.eventDescription(), n.reason(), n.remarks(),
                n.effectiveStart(), n.effectiveEnd(), n.latitude(), n.longitude(), n.radiusNm(),
                n.latitudeHemisphere(), n.longitudeHemisphere(), n.qCode(), n.traffic(), n.purpose(), n.scope(),
                n.lowerMeters(), n.upperMeters(), n.scheduleMode(), n.scheduleDay(), n.scheduleStart(), n.scheduleEnd(),
                n.status(), n.createdAt(), n.publishedAt(),n.fir(),n.scheduleStartDate(),n.scheduleEndDate(),n.qOverrideReason(),n.qOverrideOperator(),n.qOverrideAt());
    }

    private static Notam withDates(Notam n,String start,String end){return new Notam(n.id(),n.number(),n.scenario(),n.title(),n.airport(),n.featureType(),n.condition(),n.selectedRunways(),n.selectedTaxiways(),n.eventDescription(),n.reason(),n.remarks(),n.effectiveStart(),n.effectiveEnd(),n.latitude(),n.longitude(),n.radiusNm(),n.latitudeHemisphere(),n.longitudeHemisphere(),n.qCode(),n.traffic(),n.purpose(),n.scope(),n.lowerMeters(),n.upperMeters(),n.scheduleMode(),n.scheduleDay(),n.scheduleStart(),n.scheduleEnd(),n.status(),n.createdAt(),n.publishedAt(),n.fir(),start,end,n.qOverrideReason(),n.qOverrideOperator(),n.qOverrideAt());}

    private static Element closed(Document d) {
        for (Element status : CommonDigitalNotamBuilder.all(d, CommonDigitalNotamBuilder.AIXM, "operationalStatus"))
            if ("CLOSED".equals(status.getTextContent().trim())) return (Element) status.getParentNode();
        throw new AssertionError("CLOSED availability not found");
    }

    private static int countStatus(Document d, String value) {
        int result = 0;
        for (Element status : CommonDigitalNotamBuilder.all(d, CommonDigitalNotamBuilder.AIXM, "operationalStatus"))
            if (value.equals(status.getTextContent().trim())) result++;
        return result;
    }

    private static void assertEventAvailabilityCommentPosition(Document d){Element slice=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.AIXM,"AirportHeliportTimeSlice");for(Node node=slice.getFirstChild();node!=null;node=node.getNextSibling())if(node.getNodeType()==Node.COMMENT_NODE&&node.getNodeValue().trim().equals("The availability status of the airport during the event")){Node next=node.getNextSibling();while(next!=null&&next.getNodeType()==Node.TEXT_NODE&&next.getNodeValue().isBlank())next=next.getNextSibling();if(!(next instanceof Element e)||!"availability".equals(e.getLocalName())||!"CLOSED".equals(BaselineAirportHeliportCatalog.text(e,"operationalStatus")))throw new AssertionError("Event availability comment must immediately precede CLOSED availability");return;}throw new AssertionError("Event availability comment is missing");}

    private static void assertText(Document d, String local, String expected) {
        NodeList nodes = d.getElementsByTagNameNS("*", local);
        if (nodes.getLength() == 0 || !expected.equals(nodes.item(0).getTextContent().trim()))
            throw new AssertionError(local + " is not " + expected);
    }
}
