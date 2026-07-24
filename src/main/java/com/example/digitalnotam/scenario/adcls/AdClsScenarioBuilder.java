package com.example.digitalnotam.scenario.adcls;

import com.example.digitalnotam.baseline.BaselineAirportHeliportCatalog;
import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.scenario.common.schedule.EventScheduleSupport;
import com.example.digitalnotam.workflow.ScenarioBuilder;
import com.example.digitalnotam.xml.CommonDigitalNotamBuilder;

import org.w3c.dom.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class AdClsScenarioBuilder implements ScenarioBuilder {
    private static final Path BLUEPRINT=Path.of("data","virtual data","Donlon_2025","Donlon","Digital NOTAM","DN_AD.CLS_1_ad_closed.xml");
    private final CommonDigitalNotamBuilder common=new CommonDigitalNotamBuilder();
    private final BaselineAirportHeliportCatalog catalog=new BaselineAirportHeliportCatalog();
    private final AdClsScenarioValidator validator=new AdClsScenarioValidator();
    public String scenario(){return "AD.CLS";}

    public Document build(Notam n)throws Exception{
        if(!"CLSD".equals(n.eventDescription()))throw new IllegalArgumentException("AD.CLS 的状态必须为 CLOSED");
        rejectExceptions(n.condition());
        var baseline=catalog.find(n.airport(),Instant.parse(n.effectiveStart()),Instant.parse(n.effectiveEnd()));
        Document d=common.populate(BLUEPRINT,n);
        applyIdentity(d,baseline);
        Element slice=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.AIXM,"AirportHeliportTimeSlice"),closed=null;
        List<Element> remove=new ArrayList<>();
        for(Element wrapper:BaselineAirportHeliportCatalog.directChildren(slice,"availability")){
            String status=BaselineAirportHeliportCatalog.text(wrapper,"operationalStatus");
            if("NORMAL".equals(status))remove.add(wrapper);else if("CLOSED".equals(status))closed=wrapper;
        }
        if(closed==null)throw new IllegalArgumentException("AD.CLS 蓝图缺少 CLOSED availability");
        for(Element e:remove)slice.removeChild(e);
        removeAvailabilityComments(slice);
        Node firstNormal=null;
        for(Element normal:baseline.normalAvailabilities()){Element imported=(Element)d.importNode(normal,true);EventScheduleSupport.excludeEventFromBaseline(d,imported,n);slice.insertBefore(imported,closed);if(firstNormal==null)firstNormal=imported;}
        if(firstNormal!=null)slice.insertBefore(d.createComment(" The baseline status of the airport "),firstNormal);
        slice.insertBefore(d.createComment(" The availability status of the airport during the event "),closed);
        Element availability=closedAvailability(closed);
        clearOptional(availability);
        if(!"CONTINUOUS".equals(n.scheduleMode()))addSchedule(d,availability,n);
        addAnnotation(d,availability,n.reason(),true);addAnnotation(d,availability,n.remarks(),false);
        common.regenerateIds(d,baseline.uuid(),baseline.designator(),baseline.name(),scenario());
        validate(d,n);return d;
    }
    public void validate(Document d,Notam n){validator.validate(d,n);}

    private static void applyIdentity(Document d,BaselineAirportHeliportCatalog.AirportBaseline b){
        CommonDigitalNotamBuilder.setNotam(d,"affectedFIR",b.firDesignator());CommonDigitalNotamBuilder.setNotam(d,"location",b.designator());
        Element event=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.EVENT,"Event");
        CommonDigitalNotamBuilder.one(event,CommonDigitalNotamBuilder.EVENT,"name").setTextContent(b.designator()+" "+b.name());
        Element fir=CommonDigitalNotamBuilder.one(event,CommonDigitalNotamBuilder.EVENT,"concernedAirspace");
        fir.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:href","urn:uuid:"+b.firUuid());fir.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:title",b.firDesignator()+" FIR");
        Element airport=CommonDigitalNotamBuilder.one(event,CommonDigitalNotamBuilder.EVENT,"concernedAirportHeliport");
        airport.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:href","urn:uuid:"+b.uuid());airport.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:title",b.designator()+" "+b.name());
    }
    private static Element closedAvailability(Element wrapper){Element e=BaselineAirportHeliportCatalog.first(wrapper,"AirportHeliportAvailability");if(e==null)throw new IllegalArgumentException("缺少 AirportHeliportAvailability");return e;}
    private static void removeAvailabilityComments(Element slice){for(Node node=slice.getFirstChild();node!=null;){Node next=node.getNextSibling();if(node.getNodeType()==Node.COMMENT_NODE){String value=node.getNodeValue().trim();if(value.equals("The baseline status of the airport")||value.equals("The availability status of the airport during the event"))slice.removeChild(node);}node=next;}}
    private static void clearOptional(Element a){for(String name:List.of("timeInterval","annotation"))for(Element e:new ArrayList<>(BaselineAirportHeliportCatalog.directChildren(a,name)))a.removeChild(e);}
    private static void addSchedule(Document d,Element a,Notam n){Node before=a.getFirstChild();a.insertBefore(d.createComment(" Schedule "),before);EventScheduleSupport.append(d,a,before,n,false);}
    private static void addAnnotation(Document d,Element a,String value,boolean reason){
        if(value==null||value.isBlank())return;Element annotation=element(d,"annotation"),note=identified(d,"Note");annotation.appendChild(note);
        if(reason)add(d,note,"propertyName","operationalStatus");add(d,note,"purpose","REMARK");
        Element translated=element(d,"translatedNote"),linguistic=identified(d,"LinguisticNote"),text=add(d,linguistic,"note",value.trim());text.setAttribute("lang","ENG");
        translated.appendChild(linguistic);note.appendChild(translated);Element status=BaselineAirportHeliportCatalog.first(a,"operationalStatus");a.insertBefore(d.createComment(reason?" Closure Reason ":" Note "),status);a.insertBefore(annotation,status);
    }
    private static Element element(Document d,String local){return d.createElementNS(CommonDigitalNotamBuilder.AIXM,"aixm:"+local);}
    private static Element identified(Document d,String local){Element e=element(d,local);e.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","id_"+UUID.randomUUID());return e;}
    private static Element add(Document d,Element p,String local,String value){Element e=element(d,local);e.setTextContent(value);p.appendChild(e);return e;}
    private static String aixmDate(String iso){return java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM"));}
    private static void rejectExceptions(String text){if(text.toUpperCase(Locale.ROOT).matches(".*\\b(EXCEPT|PPR|PRIOR PERMISSION|EMERGENCY|HOME.BASED)\\b.*"))throw new IllegalArgumentException("带运行例外的机场关闭必须使用 AD.LIM");}
}
