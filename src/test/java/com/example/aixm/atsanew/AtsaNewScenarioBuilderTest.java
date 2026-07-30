package com.example.aixm.atsanew;

import com.example.digitalnotam.domain.AtsaNewData;
import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.domain.ScheduleData;
import com.example.digitalnotam.domain.ScheduleEntry;
import com.example.digitalnotam.scenario.atsanew.AtsaNewScenarioBuilder;
import com.example.digitalnotam.workflow.DigitalNotamPipeline;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end ATSA.NEW scenario test including the full message XSD. */
final class AtsaNewScenarioBuilderTest {
    @Test
    void createsTemporaryClassAirspaceFromSharedGeometryJson()throws Exception{
        String geometry= """
                {"schemaVersion":"1.0","geometry":{
                  "type":"CIRCLE","gmlId":"atsa-new-circle","crs":"EPSG:4326",
                  "center":{"x":-27.3992573509,"y":53.6197929845},
                  "radius":{"value":10,"uom":"NM"}
                }}
                """;
        Notam n=new Notam("test","D9000/26","ATSA.NEW","Temporary class A airspace","","AIRSPACE",
                "ESTABLISHED","","","","","","2026-08-01T10:00:00Z","2026-08-01T20:00:00Z",
                "53.62","27.40","10","N","W","QXXXX","IV","NBO","E","","",
                "CONTINUOUS","ANY","10:00","20:00","PUBLISHED","2026-07-30T00:00:00Z","2026-07-30T00:00:00Z")
                .withAtsaNew(new AtsaNewData("CLASS","A","EAAD900026","","ACTIVE","",geometry,
                        "GND","","","50","FL","STD","","","","5"));
        AtsaNewScenarioBuilder builder=new AtsaNewScenarioBuilder();
        Document d=builder.build(n);
        builder.validate(d,n);
        assertEquals("ATSA.NEW",text(d,"scenario"));
        assertEquals("BASELINE",text(d,"interpretation"));
        assertEquals("CLASS",text(d,"http://www.aixm.aero/schema/5.1.1","type"));
        assertEquals("A",text(d,"http://www.aixm.aero/schema/5.1.1","classification"));
        assertEquals("QXXXX",text(d,"http://www.aixm.aero/schema/5.1.1/event","selectionCode"));
        assertEquals(1,d.getElementsByTagNameNS("*","CircleByCenterPoint").getLength());
        assertTrue(text(d,"text").contains("CLASS A AIRSPACE"));

        var validation=DigitalNotamPipeline.class.getDeclaredMethod("validateXsd",Document.class);
        validation.setAccessible(true);
        validation.invoke(null,d);
    }

    @Test
    void createsScheduledCorridorWithExclusionsAndAirportCopies()throws Exception{
        String geometry= """
                {"schemaVersion":"1.0","geometry":{
                  "type":"CORRIDOR","gmlId":"volume-test","crs":"EPSG:4326",
                  "centreline":{"gmlId":"centreline-test","segments":[{
                    "type":"GEODESIC","positions":[
                      {"x":-34.2069959825,"y":53.4290270156},
                      {"x":-32.8476570106,"y":52.8669590956},
                      {"x":-31.4172604780,"y":51.3182439909}
                    ]}]},
                  "width":{"value":6,"uom":"KM"}
                }}
                """;
        Notam n=new Notam("test-corridor","D9001/26","ATSA.NEW","Temporary HTZ","","AIRSPACE",
                "ESTABLISHED","","","","","","2026-08-01T00:00:00Z","2026-08-08T00:00:00Z",
                "52.8","32.8","100","N","W","QXXXX","IV","NBO","AE","","",
                "WEEKDAYS","MON","09:00","17:00","PUBLISHED","2026-07-30T00:00:00Z","2026-07-30T00:00:00Z")
                .withAtsaNew(new AtsaNewData("HTZ","C","EADD900126","DONLON HTZ","INTERMITTENT","Training corridor",geometry,
                        "GND","","","1500","FT","MSL","CONTACT DONLON APP","HELICOPTER TRAINING",
                        "21a13c9f-a8ff-4fdd-9aaa-5dbfd91514b9,b42efcec-d6c3-4a0c-8e29-925e0aa6b800","5"))
                .withAtsaAct("","","","EADD,EADA,EADH","")
                .withScheduleData(new ScheduleData("WEEKDAYS",List.of(
                        new ScheduleEntry("","","MON","","09:00","17:00",false)),List.of(),"UTC"));
        var builder=new AtsaNewScenarioBuilder();
        Document d=builder.build(n);
        builder.validate(d,n);
        assertEquals(3,d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1/event","NOTAM").getLength());
        assertEquals(3,d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","geometryComponent").getLength());
        assertEquals(2,d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","activation").getLength());
        var validation=DigitalNotamPipeline.class.getDeclaredMethod("validateXsd",Document.class);
        validation.setAccessible(true);
        validation.invoke(null,d);
    }

    private static String text(Document d,String local){
        var nodes=d.getElementsByTagNameNS("*",local);
        return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();
    }
    private static String text(Document d,String namespace,String local){
        var nodes=d.getElementsByTagNameNS(namespace,local);
        return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();
    }
}
