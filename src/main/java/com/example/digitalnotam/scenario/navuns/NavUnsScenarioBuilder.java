package com.example.digitalnotam.scenario.navuns;

import com.example.digitalnotam.baseline.*;
import com.example.digitalnotam.domain.*;
import com.example.digitalnotam.workflow.ScenarioBuilder;
import com.example.digitalnotam.xml.*;
import org.w3c.dom.*;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class NavUnsScenarioBuilder implements ScenarioBuilder {
    private static final Path BLUEPRINT=Path.of("data","virtual data","Donlon_2025","Donlon","Digital NOTAM","DN_NAV.UNS_1_VOR-DME_all_components_unserviceable.xml");
    private final CommonDigitalNotamBuilder common=new CommonDigitalNotamBuilder();
    private final BaselineNavaidCatalog catalog=new BaselineNavaidCatalog();
    private final BaselineAirportHeliportCatalog airports=new BaselineAirportHeliportCatalog();
    private final NavUnsStatusResolver statuses=new NavUnsStatusResolver();
    private final NavUnsQCodeResolver qcodes=new NavUnsQCodeResolver();
    public String scenario(){return"NAV.UNS";}

    public int notificationCount(Notam n){
        try{return eventTargets(n).stream().mapToInt(x->notificationPlan(n).size()).sum();}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}
    }
    public Document build(Notam n)throws Exception{
        validateInput(n);BaselineNavaidCatalog.Navaid selected=catalog.require(n.navUnsData().navaidUuid());
        List<BaselineNavaidCatalog.Equipment> affected=catalog.affected(selected,n.navUnsData().impactMode(),n.navUnsData().equipmentUuid());
        List<BaselineNavaidCatalog.Navaid> targets=eventTargets(n);Document d=common.populate(BLUEPRINT,n);Element root=d.getDocumentElement();
        Element eventMemberTemplate=null;for(Element m:members(root)){Element f=first(m);if(f!=null&&"Event".equals(f.getLocalName())){eventMemberTemplate=(Element)m.cloneNode(true);break;}}
        if(eventMemberTemplate==null)throw new IllegalArgumentException("NAV.UNS blueprint has no Event template");
        for(Element m:new ArrayList<>(members(root)))root.removeChild(m);XmlCommentPolicy.clearDirectComments(root);
        List<String> eventIds=new ArrayList<>();int numberOffset=0;
        for(int index=0;index<targets.size();index++){
            var target=targets.get(index);String eventId=UUID.randomUUID().toString();eventIds.add(eventId);
            Element member=(Element)eventMemberTemplate.cloneNode(true),event=first(member),slice=find(event,"EventTimeSlice");regenerateIds(member);
            event.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","uuid."+eventId);find(event,"identifier").setTextContent(eventId);
            replaceTimes(slice,n);set(find(slice,"scenario"),"NAV.UNS");set(find(slice,"version"),"2.0");set(find(slice,"name"),display(target));
            removeDirect(slice,"concernedAirspace");removeDirect(slice,"concernedAirportHeliport");Element parent=direct(slice,"parentEvent");
            for(String fir:firs(n)){Element x=eventEl(d,"concernedAirspace");if("EAAD".equals(fir))link(x,"f4d5e4d4-d84a-481f-b9e3-b359e42c0dff","EAAD FIR AMSWELL FIR");else{x.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:href","urn:uuid:"+fir);x.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:title",fir);}slice.insertBefore(x,parent);}
            for(String code:csv(n.affectedAirports())){var a=airports.find(code,Instant.parse(n.effectiveStart()),Instant.parse(n.effectiveEnd()));Element x=eventEl(d,"concernedAirportHeliport");link(x,a.uuid(),a.designator()+" "+a.name());slice.insertBefore(x,parent);}
            Element cause=direct(slice,"causeEvent");if(index==0){nil(cause);}else{clear(cause);link(cause,eventIds.get(0),display(targets.get(0))+" DNOTAM NAV.UNS");}
            List<Element> notifications=directChildren(slice,"notification");Element template=(Element)notifications.get(0).cloneNode(true);notifications.forEach(slice::removeChild);
            Element before=direct(slice,"provider");var resolution=resolution(target,affected,n);var q=qcodes.resolve(target,resolution.affectedEquipment(),resolution.navaidStatus());
            List<Notice> plan=notificationPlan(n);
            for(int p=0;p<plan.size();p++){Element wrapper=(Element)template.cloneNode(true);regenerateIds(wrapper);Element notam=find(wrapper,"NOTAM");populateNotam(notam,n,target,resolution,q,plan.get(p),numberOffset++);slice.insertBefore(wrapper,before);}
            root.appendChild(member);
        }
        // A Navaid TEMPDELTA is required for every main/consequence Event.
        for(int i=0;i<targets.size();i++){var target=targets.get(i);var resolution=resolution(target,affected,n);Element member=featureMember(d,target.uuid(),"Navaid",target.baselineSlice(),resolution.temporaryNavaidType().equals(target.type())?"":resolution.temporaryNavaidType(),n,resolution.navaidStatus(),n.navUnsData().signalType(),eventIds.get(i),true);root.appendChild(member);XmlCommentPolicy.insertBefore(member,display(target)+" Navaid");}
        // Equipment TEMPDELTA belongs only to the primary Event (ER-03).
        for(var equipment:affected){Element member=featureMember(d,equipment.uuid(),equipment.type(),equipment.baselineSlice(),"",n,n.navUnsData().operationalStatus(),n.navUnsData().signalType(),eventIds.get(0),false);root.appendChild(member);XmlCommentPolicy.insertBefore(member,(equipment.designator()+" "+equipment.type()+" NavaidEquipment").trim());}
        root.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","DN_NAV.UNS_"+UUID.randomUUID());XmlCommentPolicy.validate(d);return d;
    }
    public void validate(Document d,Notam n){validateInput(n);if(d.getElementsByTagNameNS(CommonDigitalNotamBuilder.EVENT,"Event").getLength()!=eventTargets(n).size())throw new IllegalArgumentException("NAV.UNS Event count mismatch");if(d.getElementsByTagNameNS(CommonDigitalNotamBuilder.EVENT,"NOTAM").getLength()!=notificationCount(n))throw new IllegalArgumentException("NAV.UNS NOTAM count mismatch");}

    private List<BaselineNavaidCatalog.Navaid> eventTargets(Notam n){
        var selected=catalog.require(n.navUnsData().navaidUuid());var affected=catalog.affected(selected,n.navUnsData().impactMode(),n.navUnsData().equipmentUuid());
        LinkedHashMap<String,BaselineNavaidCatalog.Navaid> result=new LinkedHashMap<>();result.put(selected.uuid(),selected);
        if(!"ALL_PRIMARY".equals(n.navUnsData().impactMode()))for(var x:catalog.usingAsPrimary(affected.get(0).uuid()))result.putIfAbsent(x.uuid(),x);
        return List.copyOf(result.values());
    }
    private NavUnsStatusResolver.Resolution resolution(BaselineNavaidCatalog.Navaid target,List<BaselineNavaidCatalog.Equipment> originallyAffected,Notam n){
        List<BaselineNavaidCatalog.Equipment> related=originallyAffected.stream().filter(e->target.components().stream().anyMatch(c->c.equipmentUuid().equals(e.uuid()))).toList();
        if(related.isEmpty())related=List.of(originallyAffected.get(0));return statuses.resolve(target,related,n.navUnsData());
    }
    private void validateInput(Notam n){
        if(!"NAV.UNS".equals(n.scenario()))throw new IllegalArgumentException("Scenario must be NAV.UNS");if(n.navUnsData()==null)throw new IllegalArgumentException("NAV.UNS data is required");
        Instant a=Instant.parse(n.effectiveStart()),b=Instant.parse(n.effectiveEnd());if(!b.isAfter(a))throw new IllegalArgumentException("NAV.UNS requires a definite end after start");
        var nav=catalog.require(n.navUnsData().navaidUuid());var affected=catalog.affected(nav,n.navUnsData().impactMode(),n.navUnsData().equipmentUuid());statuses.resolve(nav,affected,n.navUnsData());validateSchedule(n);
    }
    private static void validateSchedule(Notam n){if(!Set.of("CONTINUOUS","DAILY","WEEKDAYS","DATES").contains(n.scheduleMode()))throw new IllegalArgumentException("Unsupported NAV.UNS schedule mode");if("CONTINUOUS".equals(n.scheduleMode()))return;if(!n.scheduleStart().matches("(?:[01]\\d|2[0-3]):[0-5]\\d")||!n.scheduleEnd().matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))throw new IllegalArgumentException("NAV.UNS schedule requires HH:mm UTC times");if("WEEKDAYS".equals(n.scheduleMode())){List<String>d=csv(n.scheduleDay());if(d.isEmpty()||d.size()!=new HashSet<>(d).size()||!Set.of("MON","TUE","WED","THU","FRI","SAT","SUN").containsAll(d))throw new IllegalArgumentException("NAV.UNS Weekdays requires unique MON-SUN values");}if("DATES".equals(n.scheduleMode())&&(n.scheduleStartDate().isBlank()||n.scheduleEndDate().isBlank()||LocalDate.parse(n.scheduleStartDate()).isAfter(LocalDate.parse(n.scheduleEndDate()))))throw new IllegalArgumentException("NAV.UNS Dates range is invalid");}

    private Element featureMember(Document d,String uuid,String type,Element baseline,String temporaryType,Notam n,String status,String signal,String eventId,boolean navaid){
        Element member=d.createElementNS("http://www.aixm.aero/schema/5.1.1/message","message:hasMember"),feature=aixm(d,type);member.appendChild(feature);feature.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","uuid."+uuid);Element identifier=d.createElementNS(CommonDigitalNotamBuilder.GML,"gml:identifier");identifier.setAttribute("codeSpace","urn:uuid:");identifier.setTextContent(uuid);feature.appendChild(identifier);
        Element timeSlice=aixm(d,"timeSlice"),slice=(Element)d.importNode(baseline,true);timeSlice.appendChild(slice);feature.appendChild(timeSlice);regenerateIds(slice);prepareSlice(d,slice,temporaryType,n,status,signal,navaid);
        Element extension=aixm(d,"extension"),ext=d.createElementNS(CommonDigitalNotamBuilder.EVENT,"event:"+(navaid?"Navaid":type)+"Extension");ext.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","id_"+UUID.randomUUID());Element theEvent=d.createElementNS(CommonDigitalNotamBuilder.EVENT,"event:theEvent");link(theEvent,eventId,"DNOTAM NAV.UNS");ext.appendChild(theEvent);extension.appendChild(ext);slice.appendChild(extension);XmlCommentPolicy.insertBefore(extension,"The link to the event");return member;
    }
    private void prepareSlice(Document d,Element slice,String temporaryType,Notam n,String status,String signal,boolean navaid){
        set(find(slice,"interpretation"),"TEMPDELTA");set(find(slice,"sequenceNumber"),Integer.toString(parse(find(slice,"sequenceNumber").getTextContent())+1));set(find(slice,"correctionNumber"),"0");replaceTimes(slice,n);removeDirect(slice,"featureLifetime");
        Set<String> keep=new HashSet<>(List.of("validTime","interpretation","sequenceNumber","correctionNumber","availability"));if(!temporaryType.isBlank())keep.add("type");
        for(Node node=slice.getFirstChild();node!=null;){Node next=node.getNextSibling();if(node instanceof Element e&&!keep.contains(e.getLocalName()))slice.removeChild(e);else if(node.getNodeType()==Node.COMMENT_NODE)slice.removeChild(node);node=next;}
        if(!temporaryType.isBlank())set(direct(slice,"type"),temporaryType);List<Element> baselineAvail=directChildren(slice,"availability");baselineAvail.forEach(slice::removeChild);
        Node before=null;if(!"CONTINUOUS".equals(n.scheduleMode())){int copied=0;for(Element base:baselineAvail){Element s0=optional(base,"NavaidOperationalStatus");if(s0==null)continue;Element copy=(Element)base.cloneNode(true);regenerateIds(copy);Element s=find(copy,"NavaidOperationalStatus");addSchedule(d,s,n,true);addAnnotation(d,s,"Baseline data copy. Not included in the NOTAM text generation","",false);slice.insertBefore(copy,before);copied++;}if(copied==0)slice.insertBefore(baselineAvailability(d,n),before);}
        if("SIGNAL".equals(n.navUnsData().impactMode())){
            String unaffected="AZIMUTH".equals(signal)?"DISTANCE":"AZIMUTH";
            Element normal=signalAvailability(d,unaffected);
            slice.insertBefore(normal,before);
            XmlCommentPolicy.insertBefore(normal,"The unaffected TACAN signal baseline status");
        }
        Element event=availability(d,n,status,signal,navaid);slice.insertBefore(event,before);XmlCommentPolicy.insertBefore(event,navaid?"The operational status of the navaid during the event":"The operational status of the navaid equipment during the event");
    }
    private Element availability(Document d,Notam n,String status,String signal,boolean navaid){Element wrapper=aixm(d,"availability"),s=id(d,"NavaidOperationalStatus");wrapper.appendChild(s);if(!"CONTINUOUS".equals(n.scheduleMode()))addSchedule(d,s,n,false);if(navaid){if(!n.reason().isBlank())addAnnotation(d,s,n.reason(),"operationalStatus",true);if(!n.remarks().isBlank())addAnnotation(d,s,n.remarks(),"",true);}Element special=aixm(d,"specialDateAuthority");nil(special);s.appendChild(special);add(d,s,"operationalStatus",status);Element sig=aixm(d,"signalType");if(signal.isBlank())nil(sig);else sig.setTextContent(signal);s.appendChild(sig);return wrapper;}
    private static Element baselineAvailability(Document d,Notam n){Element wrapper=aixm(d,"availability"),s=id(d,"NavaidOperationalStatus");wrapper.appendChild(s);addSchedule(d,s,n,true);addAnnotation(d,s,"Baseline data copy. Not included in the NOTAM text generation","",false);Element special=aixm(d,"specialDateAuthority");nil(special);s.appendChild(special);add(d,s,"operationalStatus","OPERATIONAL");Element signal=aixm(d,"signalType");nil(signal);s.appendChild(signal);return wrapper;}
    private static Element signalAvailability(Document d,String signalType){Element wrapper=aixm(d,"availability"),s=id(d,"NavaidOperationalStatus");wrapper.appendChild(s);addAnnotation(d,s,"Baseline data copy. Not included in the NOTAM text generation","",false);Element special=aixm(d,"specialDateAuthority");nil(special);s.appendChild(special);add(d,s,"operationalStatus","OPERATIONAL");add(d,s,"signalType",signalType);return wrapper;}
    private static void addSchedule(Document d,Element status,Notam n,boolean excluded){Node before=direct(status,"annotation");if(before==null)before=direct(status,"specialDateAuthority");List<String> days="WEEKDAYS".equals(n.scheduleMode())?csv(n.scheduleDay()):List.of("ANY");for(String day:days){Element w=aixm(d,"timeInterval"),s=id(d,"Timesheet");w.appendChild(s);add(d,s,"timeReference","UTC");if("DATES".equals(n.scheduleMode())){add(d,s,"startDate",dateOnly(n.scheduleStartDate()));add(d,s,"endDate",dateOnly(n.scheduleEndDate()));}add(d,s,"day",day);add(d,s,"startTime",n.scheduleStart());add(d,s,"endTime",n.scheduleEnd());add(d,s,"daylightSavingAdjust","NO");add(d,s,"excluded",excluded?"YES":"NO");status.insertBefore(w,before);}}
    private static void addAnnotation(Document d,Element status,String value,String property,boolean event){Element wrapper=aixm(d,"annotation"),note=id(d,"Note");wrapper.appendChild(note);if(!property.isBlank())add(d,note,"propertyName",property);add(d,note,"purpose","REMARK");Element translated=aixm(d,"translatedNote"),ling=id(d,"LinguisticNote"),text=add(d,ling,"note",value);text.setAttribute("lang","ENG");translated.appendChild(ling);note.appendChild(translated);Node before=direct(status,"specialDateAuthority");if(before==null)before=direct(status,"operationalStatus");status.insertBefore(wrapper,before);if(event)XmlCommentPolicy.insertBefore(wrapper,property.isBlank()?"Note":"Reason");}

    private List<Notice> notificationPlan(Notam n){List<String> firs=firs(n),aps=csv(n.affectedAirports());List<Notice> out=new ArrayList<>();if(aps.isEmpty())out.add(new Notice("E",firs.get(0),firs.get(0)));else if(firs.size()==1){out.add(new Notice("AE",aps.get(0),firs.get(0)));for(int i=1;i<aps.size();i++)out.add(new Notice("A",aps.get(i),firs.get(0)));}else{out.add(new Notice("E",firs.get(0),firs.get(0)));for(String ap:aps)out.add(new Notice("A",ap,firs.get(0)));}return out;}
    private void populateNotam(Element e,Notam n,BaselineNavaidCatalog.Navaid nav,NavUnsStatusResolver.Resolution r,NavUnsQCodeResolver.Mapping q,Notice notice,int offset)throws Exception{number(e,increment(n.number(),offset));put(e,"affectedFIR",notice.fir);put(e,"selectionCode",q.qCode());put(e,"traffic",q.traffic());put(e,"purpose",q.purpose());put(e,"scope",notice.scope);put(e,"minimumFL","000");put(e,"maximumFL","999");double lat=Double.parseDouble(nav.latitude()),lon=Double.parseDouble(nav.longitude());int radius=25;if("A".equals(notice.scope)){var a=airports.find(notice.location,Instant.parse(n.effectiveStart()),Instant.parse(n.effectiveEnd()));lat=Double.parseDouble(a.latitude());lon=Double.parseDouble(a.longitude());radius=5;}put(e,"coordinates",coord(lat,2,lat<0?"S":"N")+coord(lon,3,lon<0?"W":"E"));put(e,"radius","%03d".formatted(radius));put(e,"location",notice.location);put(e,"effectiveStart",notamTime(n.effectiveStart()));put(e,"effectiveEnd",notamTime(n.effectiveEnd()));put(e,"estimatedEnd","NO");put(e,"permanent","NO");put(e,"text",itemE(nav,r,n));}
    private String itemE(BaselineNavaidCatalog.Navaid nav,NavUnsStatusResolver.Resolution r,Notam n){StringBuilder out=new StringBuilder();if(!nav.name().isBlank())out.append(nav.name()).append(' ');out.append(displayType(nav.type())).append(' ');if(r.affectedEquipment().size()==1&&nav.components().stream().filter(BaselineNavaidCatalog.Component::primary).count()>1)out.append(equipmentText(r.affectedEquipment().get(0),nav)).append(' ');if(!n.navUnsData().signalType().isBlank())out.append(n.navUnsData().signalType()).append(' ');if(Set.of("ILS","ILS_DME","LOC","LOC_DME","MLS","MLS_DME").contains(nav.type()))out.append("RWY-").append(runwayDesignator(nav.runwayDirectionTitle())).append(' ');else if(!nav.designator().isBlank())out.append(nav.designator()).append(' ');var eq=r.affectedEquipment().get(0);if(!eq.frequency().isBlank())out.append(eq.frequency()).append(eq.frequencyUom()).append(' ');if(!eq.channel().isBlank())out.append(eq.channel()).append(' ');out.append(statusText(r.equipmentStatus())).append('.');if(!n.reason().isBlank())out.append(System.lineSeparator()).append("DUE TO ").append(finish(n.reason()));if(!n.remarks().isBlank())out.append(System.lineSeparator()).append(finish(n.remarks()));return out.toString().toUpperCase(Locale.ENGLISH);}
    private static String equipmentText(BaselineNavaidCatalog.Equipment e,BaselineNavaidCatalog.Navaid n){return switch(e.type()){case"DME"->"DME PART";case"VOR"->"VOR PART";case"TACAN"->"TACAN PART";case"Glidepath"->"GP PART";case"Localizer"->"LOC PART";case"Azimuth"->"AZM SIGNAL";case"Elevation"->"ELEV SIGNAL";case"DirectionFinder"->"DF";case"MarkerBeacon"->n.components().stream().filter(c->c.equipmentUuid().equals(e.uuid())).map(c->c.markerPosition()+" MKR").findFirst().orElse("MKR");default->e.type().toUpperCase(Locale.ROOT);};}
    private static String displayType(String t){return switch(t){case"ILS_DME"->"ILS";case"MLS_DME"->"MLS";case"VOR_DME"->"VOR/DME";case"NDB_DME"->"NDB/DME";case"LOC_DME"->"LOC/DME";case"NDB_MKR"->"NDB/MKR";case"DF"->"DF SERVICE";case"SDF"->"SIMPLIFIED DIRECTIONAL FACILITY EQPT";default->t;};}
    private static String statusText(String s){return switch(NavUnsStatusResolver.base(s)){case"UNSERVICEABLE","PARTIAL"->"UNSERVICEABLE";case"ONTEST"->"ON TEST, DO NOT USE. FALSE INDICATION POSSIBLE";case"INTERRUPT"->"SUBJECT TO INTERRUPTION";case"FALSE_INDICATION"->"DO NOT USE, FALSE INDICATION";case"IN_CONSTRUCTION"->"IN CONSTRUCTION, DO NOT USE";default->"OPERATIONAL STATUS IS AFFECTED";};}

    private static void replaceTimes(Element root,Notam n){NodeList begins=root.getElementsByTagNameNS("*","beginPosition");for(int i=0;i<begins.getLength();i++)begins.item(i).setTextContent(n.effectiveStart());NodeList ends=root.getElementsByTagNameNS("*","endPosition");for(int i=0;i<ends.getLength();i++){Element e=(Element)ends.item(i);while(e.hasAttributes())e.removeAttributeNode((Attr)e.getAttributes().item(0));e.setTextContent(n.effectiveEnd());}}
    private static List<Element> members(Element root){return directChildren(root,"hasMember");}
    private static Element find(Element p,String local){NodeList n=p.getElementsByTagNameNS("*",local);if(n.getLength()==0)throw new IllegalArgumentException("Missing "+local);return(Element)n.item(0);}
    private static Element optional(Element p,String local){NodeList n=p.getElementsByTagNameNS("*",local);return n.getLength()==0?null:(Element)n.item(0);}
    private static Element direct(Element p,String local){for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))return e;return null;}
    private static List<Element> directChildren(Element p,String local){List<Element>r=new ArrayList<>();for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))r.add(e);return r;}
    private static Element first(Element p){for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e)return e;return null;}
    private static void removeDirect(Element p,String local){for(Element e:new ArrayList<>(directChildren(p,local)))p.removeChild(e);}
    private static Element aixm(Document d,String local){return d.createElementNS(CommonDigitalNotamBuilder.AIXM,"aixm:"+local);}
    private static Element eventEl(Document d,String local){return d.createElementNS(CommonDigitalNotamBuilder.EVENT,"event:"+local);}
    private static Element id(Document d,String local){Element e=aixm(d,local);e.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","id_"+UUID.randomUUID());return e;}
    private static Element add(Document d,Element p,String local,String value){Element e=aixm(d,local);e.setTextContent(value);p.appendChild(e);return e;}
    private static void put(Element p,String local,String value){find(p,local).setTextContent(value);}
    private static void set(Element e,String v){e.setTextContent(v);}
    private static void link(Element e,String uuid,String title){clear(e);e.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:href","urn:uuid:"+uuid);e.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:title",title);e.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:type","simple");}
    private static void clear(Element e){while(e.hasAttributes())e.removeAttributeNode((Attr)e.getAttributes().item(0));e.setTextContent("");}
    private static void nil(Element e){clear(e);e.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance","xsi:nil","true");}
    private static void regenerateIds(Node root){if(root instanceof Element e&&e.hasAttributeNS(CommonDigitalNotamBuilder.GML,"id"))e.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","id_"+UUID.randomUUID());for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling())regenerateIds(n);}
    private static int parse(String s){try{return Integer.parseInt(s.trim());}catch(Exception e){return 1;}}
    private static String increment(String number,int amount){String[]p=number.split("/");int x=Integer.parseInt(p[0].substring(1))+amount;if(x>9999)throw new IllegalArgumentException("NAV.UNS consecutive number block exceeds 9999");return p[0].substring(0,1)+"%04d".formatted(x)+"/"+p[1];}
    private static void number(Element n,String value){String[]p=value.split("/");put(n,"series",p[0].substring(0,1));put(n,"number",p[0].substring(1));put(n,"year","20"+p[1]);}
    private static String notamTime(String iso){return Instant.parse(iso).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyMMddHHmm"));}
    private static String dateOnly(String iso){return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd-MM"));}
    private static String coord(double value,int width,String hem){double x=Math.abs(value);int d=(int)x,m=(int)Math.round((x-d)*60);if(m==60){d++;m=0;}return("%0"+width+"d%02d%s").formatted(d,m,hem);}
    private static List<String> csv(String s){return s==null?List.of():Arrays.stream(s.split(",")).map(String::trim).filter(x->!x.isBlank()).toList();}
    private static List<String> firs(Notam n){LinkedHashSet<String>x=new LinkedHashSet<>();x.add("EAAD");x.addAll(csv(n.additionalFirs()));return List.copyOf(x);}
    private static String display(BaselineNavaidCatalog.Navaid n){return(n.designator()+" "+displayType(n.type())+" "+n.name()).trim();}
    private static String runwayDesignator(String title){var m=java.util.regex.Pattern.compile("(?:RWY\\s*)?(\\d{2}[LRC]?)\\b").matcher(title);return m.find()?m.group(1):title;}
    private static String finish(String s){return s.trim().endsWith(".")?s.trim():s.trim()+".";}
    private record Notice(String scope,String location,String fir){}
}
