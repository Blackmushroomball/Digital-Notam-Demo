package com.example.digitalnotam.scenario.atsanew;

import com.example.aixm.geometry.model.GeometryModel.*;
import com.example.digitalnotam.baseline.BaselineAirspaceCatalog;
import com.example.digitalnotam.baseline.BaselineAirportHeliportCatalog;
import com.example.digitalnotam.domain.AtsaNewData;
import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.scenario.common.schedule.EventScheduleSupport;
import com.example.digitalnotam.workflow.ScenarioBuilder;
import com.example.digitalnotam.xml.CommonDigitalNotamBuilder;
import com.example.digitalnotam.xml.XmlCommentPolicy;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Builds the ATSA.NEW Event BASELINE and the newly commissioned Airspace
 * BASELINE. Geometry is delegated to the reusable AIXM geometry tool.
 */
public final class AtsaNewScenarioBuilder implements ScenarioBuilder {
    private static final Path BLUEPRINT=Path.of("data","virtual data","Donlon_2025","Donlon","Digital NOTAM","DN_ATSA.NEW_1_CLASS_circle_0_airports_1_FIR.xml");
    private static final Set<String> TYPES=Set.of("CTR","CLASS","ATZ","HTZ","ADIZ","CTA","UTA","OCA","OTA","AWY","SECTOR","SECTOR_C","RAS","TMA","ADV","UADV","FIR","OTHER:TMZ","OTHER:RMZ");
    private static final Pattern AIXM_CHARACTER_3=Pattern.compile("[A-Z0-9, !\"&#$%'()*+\\-./:;<=>?@\\[\\\\\\]^_|{}]*");
    private final CommonDigitalNotamBuilder common=new CommonDigitalNotamBuilder();
    private final AtsaNewGeometrySupport geometry=new AtsaNewGeometrySupport();
    private final AtsaNewQCodeResolver qCodes=new AtsaNewQCodeResolver();
    private final BaselineAirspaceCatalog airspaces=new BaselineAirspaceCatalog();
    private final BaselineAirportHeliportCatalog airports=new BaselineAirportHeliportCatalog();

    @Override public String scenario(){return "ATSA.NEW";}
    public int notificationCount(Notam n){return Math.max(1,csv(n.affectedAirports()).size());}

    @Override
    public Document build(Notam n)throws Exception{
        validateInput(n);
        AtsaNewData data=n.atsaNewData();
        AtsaNewGeometrySupport.Analysis analysis=geometry.analyse(data.geometryJson());
        var associations=geometry.associate(analysis,threshold(data),Instant.parse(n.effectiveStart()),Instant.parse(n.effectiveEnd()));
        if(associations.firs().isEmpty())throw new IllegalArgumentException("ATSA.NEW geometry does not intersect a baseline FIR");
        List<AtsaNewGeometrySupport.Fir> firs=mergeFirs(associations.firs(),n.additionalFirs());
        List<BaselineAirportHeliportCatalog.AirportBaseline> affectedAirports=affectedAirports(n);

        Document d=common.populate(BLUEPRINT,n);
        regenerateIds(d.getDocumentElement());
        d.getDocumentElement().setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","DN_ATSA.NEW_"+UUID.randomUUID());
        Element event=one(d,CommonDigitalNotamBuilder.EVENT,"Event");
        Element eventSlice=one(d,CommonDigitalNotamBuilder.EVENT,"EventTimeSlice");
        String eventUuid=UUID.randomUUID().toString();
        event.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","uuid."+eventUuid);
        one(event,CommonDigitalNotamBuilder.GML,"identifier").setTextContent(eventUuid);
        set(one(eventSlice,CommonDigitalNotamBuilder.EVENT,"scenario"),"ATSA.NEW");
        set(one(eventSlice,CommonDigitalNotamBuilder.EVENT,"version"),"2.0");
        set(one(eventSlice,CommonDigitalNotamBuilder.EVENT,"name"),eventName(data,affectedAirports));
        replaceTimes(eventSlice,n);
        replaceAssociations(d,eventSlice,firs,affectedAirports);
        replaceNotifications(d,eventSlice,n,data,analysis,firs,affectedAirports);
        replaceAirspace(d,n,data,analysis,eventUuid);
        XmlCommentPolicy.validate(d);
        return d;
    }

    @Override
    public void validate(Document d,Notam n){
        validateInput(n);
        if(d.getElementsByTagNameNS(CommonDigitalNotamBuilder.EVENT,"NOTAM").getLength()!=notificationCount(n))
            throw new IllegalArgumentException("ATSA.NEW notification count mismatch");
        if(d.getElementsByTagNameNS(CommonDigitalNotamBuilder.AIXM,"Airspace").getLength()!=1)
            throw new IllegalArgumentException("ATSA.NEW must create exactly one Airspace");
    }

    public void validateInput(Notam n){
        if(!"ATSA.NEW".equals(n.scenario()))throw new IllegalArgumentException("Scenario must be ATSA.NEW");
        AtsaNewData x=n.atsaNewData();if(x==null)throw new IllegalArgumentException("ATSA.NEW data is missing");
        String type=x.type().isBlank()?"CLASS":x.type();
        if(!TYPES.contains(type))throw new IllegalArgumentException("Unsupported ATSA.NEW airspace type: "+type);
        if(!x.classification().matches("[A-G]"))throw new IllegalArgumentException("ATSA.NEW class must be A-G");
        if(!Set.of("ACTIVE","INTERMITTENT").contains(x.activationStatus()))throw new IllegalArgumentException("ATSA.NEW activation status must be ACTIVE or INTERMITTENT");
        validateAixmName(x.designator(),16,"Designator");
        validateAixmName(x.name(),60,"Name");
        validateAixmName((String.join(" ",csv(n.affectedAirports()))+" "+type+" "+x.designator()).trim(),60,"generated Event name");
        try{if(!Instant.parse(n.effectiveEnd()).isAfter(Instant.parse(n.effectiveStart())))throw new IllegalArgumentException("ATSA.NEW requires a definite end after start");}catch(java.time.format.DateTimeParseException e){throw new IllegalArgumentException("ATSA.NEW start and end must be UTC instants",e);}
        validateLimit(x.lowerValue(),x.lowerUom(),x.lowerReference(),"lower");
        validateLimit(x.upperValue(),x.upperUom(),x.upperReference(),"upper");
        if("UNL".equals(x.lowerValue())||Set.of("GND","SFC").contains(x.upperValue()))throw new IllegalArgumentException("ATSA.NEW vertical limits are inverted");
        if(x.geometryJson().isBlank())throw new IllegalArgumentException("ATSA.NEW geometry JSON is required");
        geometry.analyse(x.geometryJson());
        EventScheduleSupport.validate(n);
        double threshold=threshold(x);if(!Double.isFinite(threshold)||threshold<0||threshold>100)throw new IllegalArgumentException("Nearby airport threshold must be between 0 and 100 NM");
        for(String uuid:csv(x.excludedAirspaces()))airspaces.get(uuid);
    }

    private static void validateLimit(String value,String uom,String reference,String label){
        if(value.isBlank())throw new IllegalArgumentException("ATSA.NEW "+label+" limit is required");
        if(Set.of("GND","SFC","UNL").contains(value)){if(!uom.isBlank()||!reference.isBlank())throw new IllegalArgumentException(label+" special limit must not have uom/reference");return;}
        try{if(Double.parseDouble(value)<0)throw new Exception();}catch(Exception e){throw new IllegalArgumentException(label+" limit must be GND/SFC/UNL or a non-negative number");}
        if(!Set.of("FL","FT","M").contains(uom))throw new IllegalArgumentException(label+" limit uom must be FL, FT or M");
        if(!Set.of("STD","MSL","HEI").contains(reference))throw new IllegalArgumentException(label+" limit reference must be STD, MSL or HEI");
        if("FL".equals(uom)&&!"STD".equals(reference))throw new IllegalArgumentException("FL limit must use STD reference");
    }

    private static void validateAixmName(String value,int maximum,String label){
        if(value.isBlank())return;
        if(value.length()>maximum)throw new IllegalArgumentException("ATSA.NEW "+label+" must not exceed "+maximum+" characters");
        if(!AIXM_CHARACTER_3.matcher(value).matches())
            throw new IllegalArgumentException("ATSA.NEW "+label+" contains characters not allowed by AIXM TextName/Designator");
    }

    private void replaceAssociations(Document d,Element eventSlice,List<AtsaNewGeometrySupport.Fir> firs,List<BaselineAirportHeliportCatalog.AirportBaseline> airports){
        removeDirect(eventSlice,"concernedAirspace");removeDirect(eventSlice,"concernedAirportHeliport");Element before=direct(eventSlice,"parentEvent");
        for(var fir:firs){Element e=event(d,"concernedAirspace");link(e,fir.uuid(),fir.designator()+" FIR "+fir.name());eventSlice.insertBefore(e,before);}
        for(var airport:airports){Element e=event(d,"concernedAirportHeliport");link(e,airport.uuid(),airport.designator()+" "+airport.name());eventSlice.insertBefore(e,before);}
    }

    private void replaceNotifications(Document d,Element eventSlice,Notam n,AtsaNewData data,AtsaNewGeometrySupport.Analysis analysis,List<AtsaNewGeometrySupport.Fir> firs,List<BaselineAirportHeliportCatalog.AirportBaseline> airports){
        List<Element> notifications=directChildren(eventSlice,"notification");Element template=notifications.getFirst();for(Element e:notifications)eventSlice.removeChild(e);
        int count=Math.max(1,airports.size());Element before=direct(eventSlice,"provider");
        for(int i=0;i<count;i++){
            Element wrapper=(Element)template.cloneNode(true);regenerateIds(wrapper);Element notam=one(wrapper,CommonDigitalNotamBuilder.EVENT,"NOTAM");
            String number=increment(n.number(),i);number(notam,number);
            String scope=airports.isEmpty()?"E":i==0?"AE":"A";
            String location=airports.isEmpty()?firs.getFirst().designator():airports.get(i).designator();
            put(notam,"affectedFIR",String.join(" ",firs.stream().map(AtsaNewGeometrySupport.Fir::designator).toList()));
            put(notam,"selectionCode",qCodes.resolve(data.type().isBlank()?"CLASS":data.type()));
            put(notam,"traffic","IV");put(notam,"purpose","NBO");put(notam,"scope",scope);
            if(i==0){put(notam,"minimumFL","%03d".formatted(limitFl(data.lowerValue(),data.lowerUom(),false)));put(notam,"maximumFL","%03d".formatted(limitFl(data.upperValue(),data.upperUom(),true)));put(notam,"coordinates",coordinate(analysis.latitude(),2,analysis.latitude()<0?"S":"N")+coordinate(analysis.longitude(),3,analysis.longitude()<0?"W":"E"));put(notam,"radius","%03d".formatted(Math.min(999,analysis.radiusNm())));}
            else{var a=airports.get(i);double lat=Double.parseDouble(a.latitude()),lon=Double.parseDouble(a.longitude());put(notam,"minimumFL","000");put(notam,"maximumFL","999");put(notam,"coordinates",coordinate(lat,2,lat<0?"S":"N")+coordinate(lon,3,lon<0?"W":"E"));put(notam,"radius","005");}
            put(notam,"location",location);put(notam,"effectiveStart",compact(n.effectiveStart()));put(notam,"effectiveEnd",compact(n.effectiveEnd()));put(notam,"estimatedEnd","NO");put(notam,"permanent","NO");put(notam,"text",itemE(n,data,analysis));
            eventSlice.insertBefore(wrapper,before);
        }
    }

    private void replaceAirspace(Document d,Notam n,AtsaNewData data,AtsaNewGeometrySupport.Analysis analysis,String eventUuid)throws Exception{
        Element feature=one(d,CommonDigitalNotamBuilder.AIXM,"Airspace"),slice=one(feature,CommonDigitalNotamBuilder.AIXM,"AirspaceTimeSlice");
        String uuid=UUID.randomUUID().toString();feature.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","uuid."+uuid);one(feature,CommonDigitalNotamBuilder.GML,"identifier").setTextContent(uuid);
        replaceTimes(slice,n);set(direct(slice,"type"),data.type().isBlank()?"CLASS":data.type());
        setOptional(d,slice,"designator",data.designator(),"class");setOptional(d,slice,"name",data.name(),"class");
        Element classWrapper=direct(slice,"class"),classification=one(classWrapper,CommonDigitalNotamBuilder.AIXM,"classification");classification.setTextContent(data.classification());
        for(Element e:new ArrayList<>(directChildren(slice,"geometryComponent")))slice.removeChild(e);
        for(Element e:new ArrayList<>(directChildren(slice,"activation")))slice.removeChild(e);
        for(Element e:new ArrayList<>(directChildren(slice,"annotation")))slice.removeChild(e);
        // The blueprint comments describe the elements just removed. Keeping
        // them would make two unrelated comments adjacent and violate the
        // project's XML comment policy.
        removeDirectComments(slice);
        Element extension=direct(slice,"extension");
        slice.insertBefore(baseGeometry(d,data,analysis),extension);
        int sequence=2;for(String excluded:csv(data.excludedAirspaces()))slice.insertBefore(exclusion(d,airspaces.get(excluded),sequence++),extension);
        slice.insertBefore(activation(d,n,data,false),extension);
        if(!"CONTINUOUS".equals(n.scheduleMode()))slice.insertBefore(activation(d,n,data,true),extension);
        if(!data.locationNote().isBlank())slice.insertBefore(annotation(d,"geometryComponent",data.locationNote()),extension);
        Element eventLink=one(feature,CommonDigitalNotamBuilder.EVENT,"theEvent");link(eventLink,eventUuid,eventName(data,List.of())+" DNOTAM ATSA.NEW");
    }

    private Element baseGeometry(Document d,AtsaNewData data,AtsaNewGeometrySupport.Analysis analysis)throws Exception{
        Element wrapper=aixm(d,"geometryComponent"),component=id(d,"AirspaceGeometryComponent");wrapper.appendChild(component);
        if(!data.excludedAirspaces().isBlank()){add(d,component,"operation","BASE");add(d,component,"operationSequence","1");}
        Element volumeWrapper=aixm(d,"theAirspaceVolume");component.appendChild(volumeWrapper);
        Element fragment=parseFragment(analysis.aixmXml()),volume;
        if("AirspaceVolume".equals(fragment.getLocalName()))volume=(Element)d.importNode(fragment,true);
        else{volume=id(d,"AirspaceVolume");Element projection=aixm(d,"horizontalProjection");projection.appendChild(d.importNode(fragment,true));volume.appendChild(projection);}
        prependLimits(d,volume,data);volumeWrapper.appendChild(volume);return wrapper;
    }

    private Element exclusion(Document d,BaselineAirspaceCatalog.Airspace excluded,int sequence){
        Element wrapper=aixm(d,"geometryComponent"),component=id(d,"AirspaceGeometryComponent");wrapper.appendChild(component);add(d,component,"operation","SUBTR");add(d,component,"operationSequence",Integer.toString(sequence));
        Element volumeWrapper=aixm(d,"theAirspaceVolume"),volume=id(d,"AirspaceVolume"),contributor=aixm(d,"contributorAirspace"),dependency=id(d,"AirspaceVolumeDependency");add(d,dependency,"dependency","FULL_GEOMETRY");Element target=aixm(d,"theAirspace");link(target,excluded.uuid(),excluded.designator()+" "+excluded.type()+" "+excluded.name());dependency.appendChild(target);contributor.appendChild(dependency);volume.appendChild(contributor);volumeWrapper.appendChild(volume);component.appendChild(volumeWrapper);return wrapper;
    }

    private Element activation(Document d,Notam n,AtsaNewData data,boolean inactive){
        Element wrapper=aixm(d,"activation"),activation=id(d,"AirspaceActivation");wrapper.appendChild(activation);
        if(!"CONTINUOUS".equals(n.scheduleMode()))EventScheduleSupport.append(d,activation,null,n,inactive);
        if(!inactive&&!data.controllingUnitNote().isBlank())activation.appendChild(annotation(d,"status",data.controllingUnitNote()));
        if(!inactive&&!data.note().isBlank())activation.appendChild(annotation(d,"",data.note()));
        add(d,activation,"status",inactive?"INACTIVE":data.activationStatus());
        Element levels=aixm(d,"levels"),layer=id(d,"AirspaceLayer");add(d,layer,"upperLimit","CEILING");add(d,layer,"lowerLimit","FLOOR");levels.appendChild(layer);activation.appendChild(levels);return wrapper;
    }

    private static Element annotation(Document d,String property,String text){
        Element wrapper=aixm(d,"annotation"),note=id(d,"Note");wrapper.appendChild(note);if(!property.isBlank())add(d,note,"propertyName",property);add(d,note,"purpose","REMARK");Element translated=aixm(d,"translatedNote"),linguistic=id(d,"LinguisticNote");add(d,linguistic,"note",text);translated.appendChild(linguistic);note.appendChild(translated);return wrapper;
    }

    private static void prependLimits(Document d,Element volume,AtsaNewData data){
        Node first=volume.getFirstChild();List<Element> values=new ArrayList<>();
        values.add(limit(d,"upperLimit",data.upperValue(),data.upperUom()));if(!data.upperReference().isBlank())values.add(element(d,"upperLimitReference",data.upperReference()));
        values.add(limit(d,"lowerLimit",data.lowerValue(),data.lowerUom()));if(!data.lowerReference().isBlank())values.add(element(d,"lowerLimitReference",data.lowerReference()));
        // Keep the AIXM AirspaceVolume sequence: upper value/reference first,
        // then lower value/reference, before the horizontal geometry.
        for(Element value:values)volume.insertBefore(value,first);
    }

    private static String itemE(Notam n,AtsaNewData d,AtsaNewGeometrySupport.Analysis a){
        String type=d.type().isBlank()?"CLASS":d.type();String label="CLASS".equals(type)?"CLASS "+d.classification()+" AIRSPACE":displayType(type)+(d.name().isBlank()?"":" "+d.name())+(d.designator().isBlank()?"":" "+d.designator())+" (CLASS "+d.classification()+")";
        StringBuilder out=new StringBuilder(label+" ESTABLISHED WITHIN:\n").append(geometryText(a.geometry(),d.locationNote())).append(", FROM ").append(limitText(d.lowerValue(),d.lowerUom(),d.lowerReference())).append(" UP TO ").append(limitText(d.upperValue(),d.upperUom(),d.upperReference()));
        List<String> excluded=csv(d.excludedAirspaces());if(!excluded.isEmpty()){out.append(", EXCLUDING ");for(int i=0;i<excluded.size();i++){if(i>0)out.append(" AND ");var x=new BaselineAirspaceCatalog().get(excluded.get(i));out.append(x.designator()).append(" ").append(x.type());}}
        out.append(".");if(!"CONTINUOUS".equals(n.scheduleMode()))out.append("\n").append("INTERMITTENT".equals(d.activationStatus())?"INTERMITTENTLY ACTIVE WITHIN THE FOLLOWING PERIODS: ":"ACTIVE AS FOLLOWS: ").append(EventScheduleSupport.formatItemD(n)).append(".");
        else if("INTERMITTENT".equals(d.activationStatus()))out.append("\nINTERMITTENTLY ACTIVE.");
        if(!d.controllingUnitNote().isBlank())out.append("\n").append(period(d.controllingUnitNote()));if(!d.note().isBlank())out.append("\n").append(period(d.note()));return out.toString().toUpperCase(Locale.ENGLISH);
    }

    private static String geometryText(Geometry g,String location){
        String text=switch(g){
            case Circle c -> number(c.radius().value())+c.radius().uom()+" RADIUS CENTERED ON "+dms(c.center());
            case Polygon p -> "AREA BOUNDED BY "+String.join(" - ",polygonPositions(p).stream().map(AtsaNewScenarioBuilder::dms).toList());
            case Corridor c -> "AREA OF "+number(c.width().value().divide(java.math.BigDecimal.valueOf(2),8,RoundingMode.HALF_UP))+c.width().uom()+" EITHER SIDE OF A LINE: "+String.join(" - ",curvePositions(c.centreline()).stream().map(AtsaNewScenarioBuilder::dms).toList());
            default -> throw new IllegalArgumentException("Unsupported ATSA.NEW geometry");
        };return location.isBlank()?text:text+" ("+location+")";
    }
    private static List<Position> polygonPositions(Polygon p){List<Position> out=new ArrayList<>();for(Segment s:p.segments()){if(s instanceof Geodesic g)out.addAll(g.positions());else{out.add(s.start());out.add(s.end());}}return distinct(out);}
    private static List<Position> curvePositions(Curve c){List<Position> out=new ArrayList<>();for(Segment s:c.segments()){if(s instanceof Geodesic g)out.addAll(g.positions());else{out.add(s.start());out.add(s.end());}}return distinct(out);}
    private static List<Position> distinct(List<Position> v){List<Position> r=new ArrayList<>();for(Position p:v)if(r.isEmpty()||!r.getLast().equals(p))r.add(p);return r;}
    private static String limitText(String value,String uom,String reference){if(Set.of("GND","SFC").contains(value))return "SFC";if("UNL".equals(value))return "UNL";String suffix="MSL".equals(reference)?" AMSL":"HEI".equals(reference)?" AGL":"";return value+uom+suffix;}
    private static int limitFl(String value,String uom,boolean upper){if(Set.of("GND","SFC").contains(value))return 0;if("UNL".equals(value))return 999;double x=Double.parseDouble(value),feet=switch(uom){case"FL"->x*100;case"FT"->x;case"M"->x/0.3048;default->0;};return (int)(upper?Math.ceil(feet/100):Math.floor(feet/100));}
    private static String displayType(String t){return switch(t){case"OTA"->"OCEANIC TRANSITION AREA";case"SECTOR"->"ATS SECTOR";case"SECTOR_C"->"COLLAPSED ATS SECTOR";case"RAS"->"REGULATED ATS AIRSPACE";case"ADV"->"ADVISORY AREA";case"UADV"->"UPPER ADVISORY AREA";case"HTZ"->"HELICOPTER TRAFFIC ZONE";case"OTHER:TMZ"->"TRANSPONDER MANDATORY ZONE";case"OTHER:RMZ"->"RADIO MANDATORY ZONE";default->t;};}

    private List<AtsaNewGeometrySupport.Fir> mergeFirs(List<AtsaNewGeometrySupport.Fir> detected,String additional){Map<String,AtsaNewGeometrySupport.Fir> result=new LinkedHashMap<>();detected.forEach(f->result.put(f.designator(),f));for(String value:csv(additional))airspaces.all().stream().filter(a->a.type().startsWith("FIR")&&(a.designator().equals(value)||a.uuid().equals(value))).findFirst().ifPresent(a->result.put(a.designator(),new AtsaNewGeometrySupport.Fir(a.uuid(),a.designator(),a.name())));return List.copyOf(result.values());}
    private List<BaselineAirportHeliportCatalog.AirportBaseline> affectedAirports(Notam n)throws Exception{List<BaselineAirportHeliportCatalog.AirportBaseline> result=new ArrayList<>();for(String code:csv(n.affectedAirports()))result.add(airports.find(code,Instant.parse(n.effectiveStart()),Instant.parse(n.effectiveEnd())));return result;}
    private static String eventName(AtsaNewData d,List<BaselineAirportHeliportCatalog.AirportBaseline> airports){String prefix=airports.stream().map(BaselineAirportHeliportCatalog.AirportBaseline::designator).reduce((a,b)->a+" "+b).orElse("");return (prefix+" "+(d.type().isBlank()?"CLASS":d.type())+" "+d.designator()).trim();}
    private static void replaceTimes(Element root,Notam n){NodeList begins=root.getElementsByTagNameNS(CommonDigitalNotamBuilder.GML,"beginPosition");for(int i=0;i<begins.getLength();i++)begins.item(i).setTextContent(n.effectiveStart());NodeList ends=root.getElementsByTagNameNS(CommonDigitalNotamBuilder.GML,"endPosition");for(int i=0;i<ends.getLength();i++){((Element)ends.item(i)).removeAttribute("indeterminatePosition");ends.item(i).setTextContent(n.effectiveEnd());}}
    private static void setOptional(Document d,Element parent,String local,String value,String beforeLocal){Element old=directOrNull(parent,local);if(value.isBlank()){if(old!=null)parent.removeChild(old);return;}if(old==null){old=aixm(d,local);parent.insertBefore(old,direct(parent,beforeLocal));}old.setTextContent(value);}
    private static Element parseFragment(String xml)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();}
    private static double threshold(AtsaNewData d){return Double.parseDouble(d.nearbyAirportThresholdNm().isBlank()?"5":d.nearbyAirportThresholdNm());}
    private static Element limit(Document d,String local,String value,String uom){Element e=element(d,local,value);if(!uom.isBlank())e.setAttribute("uom",uom);return e;}
    private static Element element(Document d,String local,String value){Element e=aixm(d,local);e.setTextContent(value);return e;}
    private static Element one(Node p,String ns,String local){NodeList n=p instanceof Document d?d.getElementsByTagNameNS(ns,local):((Element)p).getElementsByTagNameNS(ns,local);if(n.getLength()==0)throw new IllegalArgumentException("Missing "+local);return(Element)n.item(0);}
    private static Element direct(Element p,String local){Element e=directOrNull(p,local);if(e==null)throw new IllegalArgumentException("Missing "+local);return e;}
    private static Element directOrNull(Element p,String local){for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))return e;return null;}
    private static List<Element> directChildren(Element p,String local){List<Element> r=new ArrayList<>();for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))r.add(e);return r;}
    private static void removeDirect(Element p,String local){for(Element e:new ArrayList<>(directChildren(p,local)))p.removeChild(e);}
    private static void removeDirectComments(Element p){for(Node n=p.getFirstChild(),next;n!=null;n=next){next=n.getNextSibling();if(n.getNodeType()==Node.COMMENT_NODE)p.removeChild(n);}}
    private static Element aixm(Document d,String local){return d.createElementNS(CommonDigitalNotamBuilder.AIXM,"aixm:"+local);}
    private static Element event(Document d,String local){return d.createElementNS(CommonDigitalNotamBuilder.EVENT,"event:"+local);}
    private static Element id(Document d,String local){Element e=aixm(d,local);e.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","id_"+UUID.randomUUID());return e;}
    private static Element add(Document d,Element p,String local,String value){Element e=element(d,local,value);p.appendChild(e);return e;}
    private static void link(Element e,String uuid,String title){e.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:href","urn:uuid:"+uuid);e.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:title",title);e.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:type","simple");}
    private static void put(Element p,String local,String value){one(p,CommonDigitalNotamBuilder.EVENT,local).setTextContent(value);}
    private static void set(Element e,String value){e.setTextContent(value);}
    private static String compact(String iso){return Instant.parse(iso).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyMMddHHmm"));}
    private static String increment(String number,int amount){String[] p=number.split("/");int v=Integer.parseInt(p[0].substring(1))+amount;if(v>9999)throw new IllegalArgumentException("ATSA.NEW consecutive number block exceeds 9999");return p[0].substring(0,1)+"%04d".formatted(v)+"/"+p[1];}
    private static void number(Element n,String value){String[] p=value.split("/");put(n,"series",p[0].substring(0,1));put(n,"number",p[0].substring(1));put(n,"year","20"+p[1]);}
    private static String coordinate(double value,int width,String hem){double x=Math.abs(value);int d=(int)x,m=(int)Math.round((x-d)*60);if(m==60){d++;m=0;}return("%0"+width+"d%02d%s").formatted(d,m,hem);}
    private static String dms(Position p){return dms(p.y().doubleValue(),2,p.y().signum()<0?"S":"N")+ " "+dms(p.x().doubleValue(),3,p.x().signum()<0?"W":"E");}
    private static String dms(double value,int width,String hem){double x=Math.abs(value);int deg=(int)x;double raw=(x-deg)*60;int min=(int)raw,sec=(int)Math.round((raw-min)*60);if(sec==60){min++;sec=0;}if(min==60){deg++;min=0;}return("%0"+width+"d%02d%02d%s").formatted(deg,min,sec,hem);}
    private static String number(java.math.BigDecimal v){return v.stripTrailingZeros().toPlainString();}
    private static String period(String value){return value.trim().endsWith(".")?value.trim():value.trim()+".";}
    private static List<String> csv(String value){return value==null?List.of():Arrays.stream(value.split(",")).map(String::trim).filter(x->!x.isBlank()).distinct().toList();}
    private static void regenerateIds(Node root){if(root instanceof Element e&&e.hasAttributeNS(CommonDigitalNotamBuilder.GML,"id"))e.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","id_"+UUID.randomUUID());for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling())regenerateIds(n);}
}
