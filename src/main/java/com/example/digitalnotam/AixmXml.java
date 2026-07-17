package com.example.digitalnotam;

final class AixmXml {
    private AixmXml() {}

    static String render(Notam n) {
        String feature = switch (n.featureType()) {
            case "RUNWAY" -> "Runway";
            case "AIRSPACE" -> "Airspace";
            default -> "AirportHeliport";
        };
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <message:AIXMBasicMessage
                  xmlns:message="http://www.aixm.aero/schema/5.1.1/message"
                  xmlns:aixm="http://www.aixm.aero/schema/5.1.1"
                  xmlns:gml="http://www.opengis.net/gml/3.2"
                  xmlns:xlink="http://www.w3.org/1999/xlink"
                  gml:id="msg-%s">
                  <message:hasMember>
                    <aixm:%s gml:id="feature-%s">
                      <gml:identifier codeSpace="urn:uuid:">%s</gml:identifier>
                      <aixm:timeSlice>
                        <aixm:%sTimeSlice gml:id="ts-%s">
                          <gml:validTime><gml:TimePeriod gml:id="valid-%s">
                            <gml:beginPosition>%s</gml:beginPosition>
                            <gml:endPosition>%s</gml:endPosition>
                          </gml:TimePeriod></gml:validTime>
                          <aixm:interpretation>TEMPDELTA</aixm:interpretation>
                          <aixm:sequenceNumber>1</aixm:sequenceNumber>
                          <aixm:correctionNumber>0</aixm:correctionNumber>
                          <aixm:designator>%s</aixm:designator>
                          <aixm:annotation><aixm:Note gml:id="note-%s">
                            <aixm:propertyName>digitalNOTAM</aixm:propertyName>
                            <aixm:translatedNote><aixm:LinguisticNote>
                              <aixm:note lang="eng">%s - %s</aixm:note>
                            </aixm:LinguisticNote></aixm:translatedNote>
                          </aixm:Note></aixm:annotation>
                        </aixm:%sTimeSlice>
                      </aixm:timeSlice>
                    </aixm:%s>
                  </message:hasMember>
                </message:AIXMBasicMessage>
                """.formatted(x(n.id()), feature, x(n.id()), x(n.id()), feature, x(n.id()), x(n.id()),
                x(n.effectiveStart()), x(n.effectiveEnd()), x(n.airport()), x(n.id()),
                x(n.title()), x(n.condition()), feature, feature);
    }

    private static String x(String s) {
        return (s == null ? "" : s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
