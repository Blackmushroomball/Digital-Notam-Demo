package com.example.digitalnotam.scenario.navuns;

import com.example.digitalnotam.domain.*;
import org.w3c.dom.Document;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public final class NavUnsScenarioBuilderTest {
    public static void main(String[] args)throws Exception{
        String now=Instant.now().toString();
        Notam n=new Notam("nav-test","A0900/26","NAV.UNS","BOR VOR/DME UNSERVICEABLE","","NAVAID",
                "UNSERVICEABLE","","","","MAINTENANCE","", "2026-08-01T00:00:00Z","2026-08-01T05:00:00Z",
                "52","32","25","N","W","QNMAS","IV","BO","AE","","",
                "DAILY","ANY","01:00","03:00","DRAFT",now,now)
                .withNavUns(new NavUnsData("08a1bbd5-ea70-4fe3-836a-ea9686349495","ALL_PRIMARY","","","UNSERVICEABLE",false))
                .withScheduleData(new ScheduleData("DAILY",List.of(new ScheduleEntry("","","ANY","","01:00","03:00",false)),List.of(),"SCHEDULE NOTE"));
        // Affected airports are deliberately omitted here to test the en-route
        // notification branch; the UI tests cover automatic airport defaults.
        NavUnsScenarioBuilder builder=new NavUnsScenarioBuilder();Document d=builder.build(n);builder.validate(d,n);
        check(d.getElementsByTagNameNS("*","Navaid").getLength()==1,"one Navaid TEMPDELTA expected");
        check(d.getElementsByTagNameNS("*","VOR").getLength()==1,"VOR TEMPDELTA expected");
        check(d.getElementsByTagNameNS("*","DME").getLength()==1,"DME TEMPDELTA expected");
        check(d.getElementsByTagNameNS("*","NOTAM").getLength()==1,"one FIR NOTAM expected");
        check("QNMAS".equals(d.getElementsByTagNameNS("*","selectionCode").item(0).getTextContent()),"Q code must follow production mapping");
        check(d.getElementsByTagNameNS("*","text").item(0).getTextContent().contains("SCHEDULE NOTE"),"NAV.UNS E item must contain the schedule note");
        SchemaFactory schema=SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schema.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");schema.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"file,http,https");
        schema.newSchema(new Source[]{new StreamSource(Path.of("schemas","aixm-5.1.1","aixm-5.1.1","message","AIXM_BasicMessage.xsd").toFile()),new StreamSource(Path.of("schemas","event-5.1.1-k","Event_Features.xsd").toFile())}).newValidator().validate(new DOMSource(d));
        System.out.println("NAV.UNS scenario tests passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
