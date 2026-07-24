package com.example.digitalnotam.scenario.adlim;

import com.example.digitalnotam.baseline.BaselineAirportHeliportCatalog;
import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.domain.AdLimRestriction;
import com.example.digitalnotam.scenario.common.schedule.EventScheduleSupport;
import com.example.digitalnotam.workflow.ScenarioBuilder;
import com.example.digitalnotam.xml.CommonDigitalNotamBuilder;
import org.w3c.dom.*;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class AdLimScenarioBuilder implements ScenarioBuilder {
    private static final Path ROOT=Path.of("data","virtual data","Donlon_2025","Donlon","Digital NOTAM");
    private static final Map<String,String> TEMPLATES=Map.of(
            "RESERV","DN_AD.LIM_1_closed_except_for.xml",
            "CONDITIONAL","DN_AD.LIM_2_conditional_for.xml",
            "FORBID","DN_AD.LIM_3_prohibited_for.xml",
            "PERMIT","DN_AD.LIM_4_additionally_allowed_for.xml");
    private static final String XSI="http://www.w3.org/2001/XMLSchema-instance";
    private static final Map<String,Set<String>> CONTROLLED=Map.of(
            "aircraftType",Set.of("LANDPLANE","SEAPLANE","AMPHIBIAN","HELICOPTER","GYROCOPTER","TILT_WING","STOL","GLIDER","HANGGLIDER","PARAGLIDER","ULTRA_LIGHT","BALLOON","UAV","ALL"),
            "aircraftEngine",Set.of("JET","PISTON","TURBOPROP","ALL"),
            "flightType",Set.of("OAT","GAT","ALL"),"flightRule",Set.of("IFR","VFR","ALL"),
            "flightStatus",Set.of("HEAD","STATE","HUM","HOSP","SAR","ALL","EMERGENCY"),"flightMilitary",Set.of("MIL","CIVIL","ALL"),
            "flightOrigin",Set.of("NTL","INTL","ALL","HOME_BASED"),"flightPurpose",Set.of("SCHEDULED","NON_SCHEDULED","PRIVATE","AIR_TRAINING","AIR_WORK","ALL","PARTICIPANT"));
    private static final Set<String> INTERPRETATIONS=Set.of("ABOVE","AT_OR_ABOVE","AT_OR_BELOW","BELOW");
    private final CommonDigitalNotamBuilder common=new CommonDigitalNotamBuilder();
    private final BaselineAirportHeliportCatalog catalog=new BaselineAirportHeliportCatalog();
    private final AdLimConfiguration config=new AdLimConfiguration();
    private final AdLimScenarioValidator validator=new AdLimScenarioValidator(config);

    public String scenario(){return "AD.LIM";}

    public Document build(Notam n)throws Exception{
        List<AdLimRestriction> restrictions=restrictions(n);for(AdLimRestriction restriction:restrictions)validateInput(restriction,n);String commonType=restrictions.get(0).limitationType();if(restrictions.stream().anyMatch(x->!commonType.equals(x.limitationType())))throw new IllegalArgumentException("一次 AD.LIM 通告中的所有限制条件必须使用相同的限制类型");
        var baseline=catalog.find(n.airport(),Instant.parse(n.effectiveStart()),Instant.parse(n.effectiveEnd()));
        Document d=common.populate(ROOT.resolve(TEMPLATES.get(restrictions.get(0).limitationType())),n);
        applyIdentity(d,baseline);
        Element slice=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.AIXM,"AirportHeliportTimeSlice");
        Element eventWrapper=null;
        List<Element> remove=new ArrayList<>();
        for(Element wrapper:BaselineAirportHeliportCatalog.directChildren(slice,"availability")){
            String status=BaselineAirportHeliportCatalog.text(wrapper,"operationalStatus");
            if("LIMITED".equals(status)||"OTHER:EXTENDED".equals(status)) eventWrapper=wrapper; else remove.add(wrapper);
        }
        if(eventWrapper==null)throw new IllegalArgumentException("AD.LIM 模板缺少事件 availability");
        for(Element e:remove)slice.removeChild(e);
        removeGeneratedComments(slice);
        Node firstNormal=null;
        for(Element normal:baseline.normalAvailabilities()){Element imported=(Element)d.importNode(normal,true);EventScheduleSupport.excludeEventFromBaseline(d,imported,n);slice.insertBefore(imported,eventWrapper);if(firstNormal==null)firstNormal=imported;}
        if(firstNormal!=null)slice.insertBefore(d.createComment(" The baseline status of the airport "),firstNormal);
        slice.insertBefore(d.createComment(" The availability status of the airport during the event "),eventWrapper);Element availability=BaselineAirportHeliportCatalog.first(eventWrapper,"AirportHeliportAvailability");clearChildren(availability,"timeInterval","annotation","usage");removeLimitationComments(availability);boolean permit=restrictions.stream().allMatch(x->"PERMIT".equals(x.limitationType()));if(!permit&&restrictions.stream().anyMatch(x->"PERMIT".equals(x.limitationType())))throw new IllegalArgumentException("PERMIT 不能与 CONDITIONAL/RESERV/FORBID 混合在同一次 AD.LIM 事件中");BaselineAirportHeliportCatalog.first(availability,"operationalStatus").setTextContent(permit?"OTHER:EXTENDED":"LIMITED");if(!"CONTINUOUS".equals(n.scheduleMode()))addSchedule(d,availability,n);addAnnotation(d,availability,n.reason(),true);addAnnotation(d,availability,n.remarks(),false);for(AdLimRestriction restriction:restrictions)availability.appendChild(buildUsage(d,restriction));
        common.regenerateIds(d,baseline.uuid(),baseline.designator(),baseline.name(),scenario());
        validate(d,n);return d;
    }

    public void validate(Document d,Notam n){validator.validate(d,n);}

    private void validateInput(AdLimRestriction r,Notam n){
        if(!TEMPLATES.containsKey(r.limitationType()))throw new IllegalArgumentException("AD.LIM limitationType 无效");
        if(!config.operations.contains(r.operation())&&!other(r.operation()))throw new IllegalArgumentException("operation 不在 AD.LIM 受控配置中: "+r.operation());
        controlled("aircraftType",r.aircraftType());controlled("aircraftEngine",r.aircraftEngine());controlled("flightType",r.flightType());controlled("flightRule",r.flightRule());controlled("flightStatus",r.flightStatus());controlled("flightMilitary",r.flightMilitary());controlled("flightOrigin",r.flightOrigin());controlled("flightPurpose",r.flightPurpose());
        if(!r.aircraftWingSpanInterpretation().isBlank()&&!INTERPRETATIONS.contains(r.aircraftWingSpanInterpretation()))throw new IllegalArgumentException("wingSpanInterpretation 无效");
        if(!r.aircraftWeightInterpretation().isBlank()&&!INTERPRETATIONS.contains(r.aircraftWeightInterpretation()))throw new IllegalArgumentException("weightInterpretation 无效");
        if(r.aircraftWingSpan().isBlank()&&!r.aircraftWingSpanInterpretation().isBlank())throw new IllegalArgumentException("wingSpanInterpretation 必须与 wingSpan 同时使用");
        if(r.aircraftWeight().isBlank()&&!r.aircraftWeightInterpretation().isBlank())throw new IllegalArgumentException("weightInterpretation 必须与 weight 同时使用");
        if(!r.aircraftWingSpan().isBlank()&&!config.wingSpanUnits.contains(r.aircraftWingSpanUom()))throw new IllegalArgumentException("wingSpan uom 不在受控配置中");
        if(!r.aircraftWeight().isBlank()&&!config.weightUnits.contains(r.aircraftWeightUom()))throw new IllegalArgumentException("weight uom 不在受控配置中");
        if(!r.pprValue().isBlank()&&(r.pprUnit().isBlank()||!Set.of("HR","MIN","SEC").contains(r.pprUnit())))throw new IllegalArgumentException("PPR 单位必须为 HR/MIN/SEC");
        if(r.pprValue().isBlank()&&!r.pprDetails().isBlank())throw new IllegalArgumentException("PPR details 不能脱离 PPR 使用");
        if(!"CONTINUOUS".equals(n.scheduleMode())){
            if(!n.scheduleStart().matches("(?:[01]\\d|2[0-3]):[0-5]\\d")||!n.scheduleEnd().matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))throw new IllegalArgumentException("AD.LIM schedule 必须使用明确的 HH:mm 时间");
            if("WEEKDAYS".equals(n.scheduleMode())){List<String> days=Arrays.stream(n.scheduleDay().split(",")).filter(x->!x.isBlank()).toList();if(days.isEmpty()||days.size()!=new HashSet<>(days).size()||!Set.of("MON","TUE","WED","THU","FRI","SAT","SUN").containsAll(days))throw new IllegalArgumentException("Weekdays 必须使用不重复的 MON-SUN");}
            if("DATES".equals(n.scheduleMode())){if(n.scheduleStartDate().isBlank()||n.scheduleEndDate().isBlank())throw new IllegalArgumentException("Dates 必须填写 startDate/endDate");if(LocalDate.parse(n.scheduleStartDate()).isAfter(LocalDate.parse(n.scheduleEndDate())))throw new IllegalArgumentException("Dates startDate 不能晚于 endDate");}
        }
    }

    private static void controlled(String name,String value){if(value!=null&&!value.isBlank()&&!CONTROLLED.get(name).contains(value)&&!other(value))throw new IllegalArgumentException(name+" 不在受控值域中: "+value);}
    private static boolean other(String value){return value!=null&&value.matches("OTHER:[A-Z0-9][A-Z0-9_\\-]*");}
    private static List<AdLimRestriction> restrictions(Notam n){if(n.adLimRestrictions()!=null&&!n.adLimRestrictions().isEmpty())return n.adLimRestrictions();return List.of(new AdLimRestriction(n.limitationType(),n.operation(),n.flightType(),n.flightRule(),n.flightStatus(),n.flightMilitary(),n.flightOrigin(),n.flightPurpose(),n.aircraftType(),n.aircraftEngine(),n.aircraftWingSpan(),n.aircraftWingSpanUom(),n.aircraftWingSpanInterpretation(),n.aircraftWeight(),n.aircraftWeightUom(),n.aircraftWeightInterpretation(),n.pprValue(),n.pprUnit(),n.pprDetails()));}

    private static Element buildUsage(Document d,AdLimRestriction n){
        Element wrapper=e(d,"usage"),usage=id(d,"AirportHeliportUsage");wrapper.appendChild(usage);add(d,usage,"type",n.limitationType());
        Element ppr=add(d,usage,"priorPermission",n.pprValue());if(n.pprValue().isBlank()){ppr.setAttributeNS(XSI,"xsi:nil","true");ppr.setAttribute("nilReason","inapplicable");}else ppr.setAttribute("uom",n.pprUnit());
        Element contact=e(d,"contact");contact.setAttributeNS(XSI,"xsi:nil","true");contact.setAttribute("nilReason","inapplicable");usage.appendChild(contact);
        boolean hasFlight=has(n.flightType(),n.flightRule(),n.flightStatus(),n.flightMilitary(),n.flightOrigin(),n.flightPurpose());
        boolean hasAircraft=has(n.aircraftType(),n.aircraftEngine(),n.aircraftWingSpan(),n.aircraftWeight());
        if(hasFlight||hasAircraft){Element selection=e(d,"selection"),combination=id(d,"ConditionCombination");selection.appendChild(combination);usage.appendChild(selection);add(d,combination,"logicalOperator",hasFlight&&hasAircraft?"AND":"NONE");
            if(hasAircraft){Element aw=e(d,"aircraft"),a=id(d,"AircraftCharacteristic");aw.appendChild(a);combination.appendChild(aw);optional(d,a,"type",n.aircraftType());optional(d,a,"engine",n.aircraftEngine());measure(d,a,"wingSpan",n.aircraftWingSpan(),n.aircraftWingSpanUom());optional(d,a,"wingSpanInterpretation",n.aircraftWingSpanInterpretation());measure(d,a,"weight",n.aircraftWeight(),n.aircraftWeightUom());optional(d,a,"weightInterpretation",n.aircraftWeightInterpretation());}
            if(hasFlight){Element fw=e(d,"flight"),f=id(d,"FlightCharacteristic");fw.appendChild(f);combination.appendChild(fw);optional(d,f,"type",n.flightType());optional(d,f,"rule",n.flightRule());optional(d,f,"status",n.flightStatus());optional(d,f,"military",n.flightMilitary());optional(d,f,"origin",n.flightOrigin());optional(d,f,"purpose",n.flightPurpose());}}
        if(!n.pprDetails().isBlank())usage.appendChild(note(d,n.pprDetails(),"priorPermission"));
        else {Element nil=e(d,"annotation");nil.setAttributeNS(XSI,"xsi:nil","true");usage.appendChild(nil);}
        add(d,usage,"operation",n.operation());return wrapper;
    }

    private static void measure(Document d,Element p,String name,String value,String uom){if(value.isBlank())return;Element x=add(d,p,name,value);x.setAttribute("uom",uom);}
    private static void optional(Document d,Element p,String name,String value){if(value!=null&&!value.isBlank())add(d,p,name,value);}
    private static boolean has(String... values){return Arrays.stream(values).anyMatch(v->v!=null&&!v.isBlank());}
    private static Element note(Document d,String value,String property){Element wrapper=e(d,"annotation"),note=id(d,"Note");wrapper.appendChild(note);if(property!=null)add(d,note,"propertyName",property);add(d,note,"purpose","REMARK");Element translated=e(d,"translatedNote"),ling=id(d,"LinguisticNote"),text=add(d,ling,"note",value.trim());text.setAttribute("lang","ENG");translated.appendChild(ling);note.appendChild(translated);return wrapper;}
    private static void addAnnotation(Document d,Element a,String value,boolean reason){if(value==null||value.isBlank())return;Element status=BaselineAirportHeliportCatalog.first(a,"operationalStatus");a.insertBefore(d.createComment(reason?" Limitation Reason ":" Note "),status);a.insertBefore(note(d,value,reason?"operationalStatus":null),status);}
    private static void addSchedule(Document d,Element a,Notam n){Element before=BaselineAirportHeliportCatalog.first(a,"operationalStatus");a.insertBefore(d.createComment(" Schedule "),before);EventScheduleSupport.append(d,a,before,n,false);}
    private static String date(String iso){return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd-MM"));}
    private static void clearChildren(Element p,String... names){Set<String>s=Set.of(names);for(Node n=p.getFirstChild();n!=null;){Node next=n.getNextSibling();if(n instanceof Element e&&s.contains(e.getLocalName()))p.removeChild(n);n=next;}}
    private static void removeLimitationComments(Element p){for(Node n=p.getFirstChild();n!=null;){Node next=n.getNextSibling();if(n.getNodeType()==Node.COMMENT_NODE&&n.getNodeValue().trim().toLowerCase(Locale.ROOT).startsWith("limitation"))p.removeChild(n);n=next;}}
    private static void removeGeneratedComments(Element p){for(Node n=p.getFirstChild();n!=null;){Node next=n.getNextSibling();if(n.getNodeType()==Node.COMMENT_NODE){String value=n.getNodeValue().trim().toLowerCase(Locale.ROOT);if(value.startsWith("the baseline status")||value.startsWith("the availability status"))p.removeChild(n);}n=next;}}
    private static void applyIdentity(Document d,BaselineAirportHeliportCatalog.AirportBaseline b){CommonDigitalNotamBuilder.setNotam(d,"affectedFIR",b.firDesignator());CommonDigitalNotamBuilder.setNotam(d,"location",b.designator());Element event=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.EVENT,"Event");CommonDigitalNotamBuilder.one(event,CommonDigitalNotamBuilder.EVENT,"name").setTextContent(b.designator()+" "+b.name());Element fir=CommonDigitalNotamBuilder.one(event,CommonDigitalNotamBuilder.EVENT,"concernedAirspace");fir.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:href","urn:uuid:"+b.firUuid());fir.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:title",b.firDesignator()+" FIR");Element airport=CommonDigitalNotamBuilder.one(event,CommonDigitalNotamBuilder.EVENT,"concernedAirportHeliport");airport.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:href","urn:uuid:"+b.uuid());airport.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:title",b.designator()+" "+b.name());}
    private static Element e(Document d,String local){return d.createElementNS(CommonDigitalNotamBuilder.AIXM,"aixm:"+local);}private static Element id(Document d,String local){Element x=e(d,local);x.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","id_"+UUID.randomUUID());return x;}private static Element add(Document d,Element p,String local,String value){Element x=e(d,local);x.setTextContent(value);p.appendChild(x);return x;}
}
