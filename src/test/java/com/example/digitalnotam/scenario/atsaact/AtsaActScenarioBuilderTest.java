package com.example.digitalnotam.scenario.atsaact;

import com.example.digitalnotam.domain.*;
import com.example.digitalnotam.baseline.BaselineAirspaceCatalog;
import org.w3c.dom.Document;
import java.time.Instant;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.*;
import java.util.List;

public final class AtsaActScenarioBuilderTest {
    public static void main(String[] args)throws Exception{
        String now=Instant.now().toString();
        Notam n=new Notam("atsa-test","D0900/26","ATSA.ACT","MAGNETO TMA ACTIVATION","","AIRSPACE",
                "ACTIVE","","","","","GENERAL NOTE", "2026-08-01T00:00:00Z","2026-08-01T05:00:00Z",
                "54","36","1","N","W","QATCA","IV","BO","E","","",
                "DAILY","ANY","01:00","03:00","DRAFT",now,now)
                .withAtsaAct("MAGNETO_TMA","0df377fe-dd53-4d60-b6c4-6546ef31d26b,010d8451-d751-4abb-9c71-f48ad024045b","ACTIVE","EAMN,EADD","")
                .withScheduleData(new ScheduleData("DAILY",List.of(new ScheduleEntry("","","ANY","","01:00","03:00",false)),List.of(),"SCHEDULE NOTE"));
        AtsaActScenarioBuilder builder=new AtsaActScenarioBuilder();Document d=builder.build(n);builder.validate(d,n);
        check(d.getElementsByTagNameNS("*","NOTAM").getLength()==2,"one AE and one A notification expected");
        check(d.getElementsByTagNameNS("*","Airspace").getLength()==2,"two sector TEMPDELTAs expected");
        check(d.getElementsByTagNameNS("*","AirspaceActivation").getLength()>=2,"activation blocks expected");
        check(text(d,"text").contains("SCHEDULE NOTE"),"ATSA.ACT E item must contain the schedule note");
        assertCommentPlacement(d);
        SchemaFactory schema=SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schema.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");schema.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"file,http,https");
        schema.newSchema(new Source[]{new StreamSource(Path.of("schemas","aixm-5.1.1","aixm-5.1.1","message","AIXM_BasicMessage.xsd").toFile()),new StreamSource(Path.of("schemas","event-5.1.1-k","Event_Features.xsd").toFile())}).newValidator().validate(new DOMSource(d));
        Notam circle=new Notam("atsa-circle","D0902/26","ATSA.ACT","DONLON CTR ACTIVATION","","AIRSPACE",
                "ACTIVE","","","","","", "2026-08-02T00:00:00Z","2026-08-02T05:00:00Z",
                "0","0","0","N","E","QACCA","IV","BO","E","","",
                "CONTINUOUS","ANY","00:00","05:00","DRAFT",now,now)
                .withAtsaAct("ASE_21a13c9f-a8ff-4fdd-9aaa-5dbfd91514b9","21a13c9f-a8ff-4fdd-9aaa-5dbfd91514b9","ACTIVE","","");
        Document circleXml=builder.build(circle);builder.validate(circleXml,circle);
        check("019".equals(text(circleXml,"radius"))||"020".equals(text(circleXml,"radius")),"circle geometry must produce its calculated radius");
        var catalog=new BaselineAirspaceCatalog();
        var composition=new AtsaActActivationComposer().composeForPublication(n,catalog.resolve(n.airspaceGroupId(),n.selectedAirspaces()));
        check(composition.airspaces().size()==2,"calendar must contain every selected sector");
        check(composition.airspaces().stream().allMatch(a->a.intervals().stream().anyMatch(i->"EVENT".equals(i.source()))),"calendar must expose event intervals");
        check(composition.compositionHash().matches("[0-9a-f]{64}"),"calendar must expose a stable composition hash");
        System.out.println("ATSA.ACT scenario tests passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void assertCommentPlacement(Document d){
        NodeList slices=d.getElementsByTagNameNS("*","AirspaceTimeSlice");
        for(int i=0;i<slices.getLength();i++){Node previous=null;for(Node n=slices.item(i).getFirstChild();n!=null;n=n.getNextSibling()){if(n.getNodeType()==Node.TEXT_NODE&&n.getNodeValue().isBlank())continue;check(!(previous instanceof Comment&&n instanceof Comment),"comments must not be adjacent");if(previous instanceof Comment c&&c.getNodeValue().contains("activation status"))check(n instanceof Element&&"activation".equals(n.getLocalName()),"activation comment must own activation");if(previous instanceof Comment c&&c.getNodeValue().contains("link to the event"))check(n instanceof Element&&"extension".equals(n.getLocalName()),"link comment must own extension");previous=n;}}
    }
    private static String text(Document d,String local){NodeList n=d.getElementsByTagNameNS("*",local);return n.getLength()==0?"":n.item(0).getTextContent().trim();}
}
