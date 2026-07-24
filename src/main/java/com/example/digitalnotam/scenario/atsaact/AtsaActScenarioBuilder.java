package com.example.digitalnotam.scenario.atsaact;

import com.example.digitalnotam.baseline.*;
import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.scenario.common.schedule.EventScheduleSupport;
import com.example.digitalnotam.workflow.ScenarioBuilder;
import com.example.digitalnotam.xml.CommonDigitalNotamBuilder;
import com.example.digitalnotam.xml.XmlCommentPolicy;
import org.w3c.dom.*;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class AtsaActScenarioBuilder implements ScenarioBuilder {
    private static final Path BLUEPRINT=Path.of("data","virtual data","Donlon_2025","Donlon","Digital NOTAM","DN_ATSA.ACT_1_sector_activation.xml");
    private final CommonDigitalNotamBuilder common=new CommonDigitalNotamBuilder();
    private final BaselineAirspaceCatalog catalog=new BaselineAirspaceCatalog();
    private final AirspaceGeometryService geometry=new AirspaceGeometryService();
    private final AtsaActActivationComposer activationComposer=new AtsaActActivationComposer();
    private final AtsaActQCodeResolver codes=new AtsaActQCodeResolver();
    public String scenario(){return "ATSA.ACT";}
    public int notificationCount(Notam n){return Math.max(1,csv(n.affectedAirports()).size());}
    public Document build(Notam n)throws Exception{
        validateInput(n);List<BaselineAirspaceCatalog.Airspace> targets=catalog.resolve(n.airspaceGroupId(),n.selectedAirspaces());
        activationComposer.composeForPublication(n,targets);
        String normalized=AtsaActQCodeResolver.normalize(targets.get(0).type());if(targets.stream().anyMatch(a->!normalized.equals(AtsaActQCodeResolver.normalize(a.type()))))throw new IllegalArgumentException("All ATSA.ACT sectors must have the same Q-code subject");
        var q=codes.resolve(targets.get(0).type(),n.activationStatus());var geo=geometry.calculate(targets);
        Document d=common.populate(BLUEPRINT,n);Element root=d.getDocumentElement(),event=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.EVENT,"Event"),eventSlice=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.EVENT,"EventTimeSlice");
        String eventUuid=UUID.randomUUID().toString();event.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","uuid."+eventUuid);CommonDigitalNotamBuilder.one(event,CommonDigitalNotamBuilder.GML,"identifier").setTextContent(eventUuid);
        set(CommonDigitalNotamBuilder.one(event,CommonDigitalNotamBuilder.EVENT,"scenario"),"ATSA.ACT");set(CommonDigitalNotamBuilder.one(event,CommonDigitalNotamBuilder.EVENT,"version"),"2.0");set(CommonDigitalNotamBuilder.one(event,CommonDigitalNotamBuilder.EVENT,"name"),targets.stream().map(a->a.designator().isBlank()?a.name():a.designator()).reduce((a,b)->a+" "+b).orElse(""));
        removeDirect(eventSlice,"concernedAirspace");removeDirect(eventSlice,"concernedAirportHeliport");Element before=direct(eventSlice,"parentEvent");
        Element fir=eventEl(d,"concernedAirspace");link(fir,"f4d5e4d4-d84a-481f-b9e3-b359e42c0dff","EAAD FIR AMSWELL FIR");eventSlice.insertBefore(fir,before);
        List<String> airports=csv(n.affectedAirports());BaselineAirportHeliportCatalog airportCatalog=new BaselineAirportHeliportCatalog();List<BaselineAirportHeliportCatalog.AirportBaseline> airportData=new ArrayList<>();
        for(String code:airports){var a=airportCatalog.find(code,Instant.parse(n.effectiveStart()),Instant.parse(n.effectiveEnd()));airportData.add(a);Element x=eventEl(d,"concernedAirportHeliport");link(x,a.uuid(),a.designator()+" "+a.name());eventSlice.insertBefore(x,before);}
        Element notification=direct(eventSlice,"notification"),notam=CommonDigitalNotamBuilder.one(notification,CommonDigitalNotamBuilder.EVENT,"NOTAM");List<Element> notificationCopies=new ArrayList<>();notificationCopies.add(notification);
        for(int i=1;i<Math.max(1,airportData.size());i++){Element copy=(Element)notification.cloneNode(true);regenerateIds(copy);eventSlice.insertBefore(copy,direct(eventSlice,"provider"));notificationCopies.add(copy);}
        for(int i=0;i<notificationCopies.size();i++){Element nn=CommonDigitalNotamBuilder.one(notificationCopies.get(i),CommonDigitalNotamBuilder.EVENT,"NOTAM");String number=increment(n.number(),i);number(nn,number);String scope=airportData.isEmpty()?"E":i==0?"AE":"A";String location=airportData.isEmpty()?"EAAD":airportData.get(i).designator();populateNotam(nn,n,q,geo,scope,location,targets);}
        Element templateMember=null;List<Node> remove=new ArrayList<>();for(Node node=root.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element member&&"hasMember".equals(member.getLocalName())){Element f=first(member);if(f!=null&&"Airspace".equals(f.getLocalName())){if(templateMember==null)templateMember=(Element)member.cloneNode(true);remove.add(member);}}
        if(templateMember==null)throw new IllegalArgumentException("ATSA.ACT blueprint has no Airspace TEMPDELTA");for(Node x:remove)root.removeChild(x);
        // Feature-label comments from the blueprint belonged to the removed
        // members. Keeping them would make them describe the next generated member.
        XmlCommentPolicy.clearDirectComments(root);
        for(var target:targets){Element member=(Element)templateMember.cloneNode(true),feature=first(member);regenerateIds(member);feature.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","uuid."+target.uuid());CommonDigitalNotamBuilder.one(feature,CommonDigitalNotamBuilder.GML,"identifier").setTextContent(target.uuid());Element slice=CommonDigitalNotamBuilder.one(feature,CommonDigitalNotamBuilder.AIXM,"AirspaceTimeSlice");for(Element a:new ArrayList<>(children(slice,"activation")))slice.removeChild(a);Element extension=direct(slice,"extension");
            // The cloned comments described activation nodes from the blueprint.
            // Clear them before rebuilding the property list.
            XmlCommentPolicy.clearDirectComments(slice);
            // TEMPDELTA activation replaces the full baseline property. For a scheduled
            // event, copy the baseline and subtract the event schedule using an excluded
            // Timesheet, so the copies cover gaps only. A continuous event has no gaps.
            if(!"CONTINUOUS".equals(n.scheduleMode()))for(Element baseline:children(target.baselineSlice(),"activation")){Element copy=(Element)d.importNode(baseline,true);regenerateIds(copy);markBaselineCopy(d,copy,n);slice.insertBefore(copy,extension);}
            Element eventActivation=activation(d,n);slice.insertBefore(eventActivation,extension);XmlCommentPolicy.insertBefore(eventActivation,"The activation status of the airspace during the event");
            XmlCommentPolicy.insertBefore(extension,"The link to the event");
            Element link=CommonDigitalNotamBuilder.one(feature,CommonDigitalNotamBuilder.EVENT,"theEvent");link(link,eventUuid,targets.get(0).name()+" DNOTAM ATSA.ACT");root.appendChild(member);XmlCommentPolicy.insertBefore(member,target.designator()+" "+target.name());}
        d.getDocumentElement().setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","DN_ATSA.ACT_"+UUID.randomUUID());XmlCommentPolicy.validate(d);return d;
    }
    public void validate(Document d,Notam n){validateInput(n);int expected=notificationCount(n);if(d.getElementsByTagNameNS(CommonDigitalNotamBuilder.EVENT,"NOTAM").getLength()!=expected)throw new IllegalArgumentException("ATSA.ACT notification count mismatch");}
    private void validateInput(Notam n){if(!"ATSA.ACT".equals(n.scenario()))throw new IllegalArgumentException("Scenario must be ATSA.ACT");if(!Set.of("ACTIVE","INACTIVE").contains(n.activationStatus()))throw new IllegalArgumentException("ATSA.ACT status must be ACTIVE or INACTIVE");if(!Instant.parse(n.effectiveEnd()).isAfter(Instant.parse(n.effectiveStart())))throw new IllegalArgumentException("ATSA.ACT requires a definite end after start");catalog.resolve(n.airspaceGroupId(),n.selectedAirspaces());}
    private static Element activation(Document d,Notam n){Element wrapper=aixm(d,"activation"),a=id(d,"AirspaceActivation");wrapper.appendChild(a);if(!"CONTINUOUS".equals(n.scheduleMode()))addSchedule(d,a,n);add(d,a,"status",n.activationStatus());Element levels=aixm(d,"levels"),layer=id(d,"AirspaceLayer");add(d,layer,"upperLimit","CEILING");add(d,layer,"lowerLimit","FLOOR");levels.appendChild(layer);a.appendChild(levels);if(!n.remarks().isBlank())annotation(d,a,n.remarks());return wrapper;}
    private static void addSchedule(Document d,Element a,Notam n){addSchedule(d,a,n,false);}
    private static void addSchedule(Document d,Element a,Notam n,boolean excluded){Node before=null;for(Node x=a.getFirstChild();x!=null;x=x.getNextSibling())if(x instanceof Element e&&Set.of("annotation","specialDateAuthority","activity","status").contains(e.getLocalName())){before=x;break;}EventScheduleSupport.append(d,a,before,n,excluded);}
    private static void annotation(Document d,Element a,String value){Element w=aixm(d,"annotation");Node before=null;for(Node n=a.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&Set.of("specialDateAuthority","activity","status").contains(e.getLocalName())){before=n;break;}a.insertBefore(w,before);Element note=id(d,"Note");w.appendChild(note);add(d,note,"purpose","REMARK");Element tr=aixm(d,"translatedNote"),ln=id(d,"LinguisticNote"),text=add(d,ln,"note",value);text.setAttribute("lang","ENG");tr.appendChild(ln);note.appendChild(tr);}
    private static void markBaselineCopy(Document d,Element wrapper,Notam n){Element a=CommonDigitalNotamBuilder.one(wrapper,CommonDigitalNotamBuilder.AIXM,"AirspaceActivation");addSchedule(d,a,n,true);annotation(d,a,"Baseline data copy. Not included in the NOTAM text generation");}
    private static void populateNotam(Element e,Notam n,AtsaActQCodeResolver.Mapping q,AirspaceGeometryService.Result g,String scope,String location,List<BaselineAirspaceCatalog.Airspace> targets){put(e,"affectedFIR","EAAD");put(e,"selectionCode",q.qCode());put(e,"traffic",q.traffic());put(e,"purpose",q.purpose());put(e,"scope",scope);put(e,"minimumFL","%03d".formatted(g.minimumFl()));put(e,"maximumFL","%03d".formatted(g.maximumFl()));put(e,"coordinates",coordinate(g.latitude(),2,g.latitude()<0?"S":"N")+coordinate(g.longitude(),3,g.longitude()<0?"W":"E"));put(e,"radius","%03d".formatted(g.radiusNm()));put(e,"location",location);put(e,"effectiveStart",time(n.effectiveStart()));put(e,"effectiveEnd",time(n.effectiveEnd()));put(e,"estimatedEnd","NO");put(e,"permanent","NO");put(e,"text",itemE(n,targets));}
    private static String itemE(Notam n,List<BaselineAirspaceCatalog.Airspace> targets){List<String> labels=targets.stream().map(a->displayType(a.type(),a.localType())+" "+a.name()+(a.designator().isBlank()?"":" "+a.designator())+(a.classification().isBlank()?"":" (CLASS "+a.classification()+")")).toList();String joined=labels.size()==1?labels.get(0):String.join(", ",labels.subList(0,labels.size()-1))+" AND "+labels.get(labels.size()-1);String result=joined+" "+("ACTIVE".equals(n.activationStatus())?"ACTIVATED":"DEACTIVATED");if("ACTIVE".equals(n.activationStatus())&&!"CONTINUOUS".equals(n.scheduleMode()))result+=" AS FOLLOWS:\n"+scheduleText(n);result+=".";if(n.scheduleData()!=null&&!n.scheduleData().note().isBlank())result+="\n"+finalize(n.scheduleData().note());if(!n.remarks().isBlank())result+="\n"+finalize(n.remarks());return result.toUpperCase(Locale.ENGLISH);}
    private static String scheduleText(Notam n){return EventScheduleSupport.formatItemD(n);}
    private static String displayType(String t,String local){if("RAS".equals(t)&&!local.isBlank())return local.replace("OTHER:","");return t.endsWith("_P")?t.substring(0,t.length()-2)+" PART":t;}
    private static void number(Element n,String value){String[] p=value.split("/");put(n,"series",p[0].substring(0,1));put(n,"number",p[0].substring(1));put(n,"year","20"+p[1]);}
    private static String increment(String number,int amount){String[] p=number.split("/");int value=Integer.parseInt(p[0].substring(1))+amount;if(value>9999)throw new IllegalArgumentException("ATSA.ACT consecutive number block exceeds 9999");return p[0].substring(0,1)+"%04d".formatted(value)+"/"+p[1];}
    private static void put(Element p,String local,String value){CommonDigitalNotamBuilder.one(p,CommonDigitalNotamBuilder.EVENT,local).setTextContent(value);}
    private static String time(String iso){return Instant.parse(iso).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyMMddHHmm"));}
    private static String date(String iso){return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd-MM"));}
    private static String coordinate(double value,int width,String hem){double x=Math.abs(value);int d=(int)x,m=(int)Math.round((x-d)*60);if(m==60){d++;m=0;}return("%0"+width+"d%02d%s").formatted(d,m,hem);}
    private static String finalize(String s){return s.trim().endsWith(".")?s.trim():s.trim()+".";}
    private static List<String> csv(String s){return s==null?List.of():Arrays.stream(s.split(",")).map(String::trim).filter(x->!x.isBlank()).toList();}
    private static Element first(Element p){for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e)return e;return null;}
    private static Element direct(Element p,String local){for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))return e;throw new IllegalArgumentException("Missing "+local);}
    private static List<Element> children(Element p,String local){List<Element> r=new ArrayList<>();for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))r.add(e);return r;}
    private static void removeDirect(Element p,String local){for(Element e:new ArrayList<>(children(p,local)))p.removeChild(e);}
    private static Element aixm(Document d,String local){return d.createElementNS(CommonDigitalNotamBuilder.AIXM,"aixm:"+local);}
    private static Element eventEl(Document d,String local){return d.createElementNS(CommonDigitalNotamBuilder.EVENT,"event:"+local);}
    private static Element id(Document d,String local){Element e=aixm(d,local);e.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","id_"+UUID.randomUUID());return e;}
    private static Element add(Document d,Element p,String local,String value){Element e=aixm(d,local);e.setTextContent(value);p.appendChild(e);return e;}
    private static void link(Element e,String uuid,String title){e.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:href","urn:uuid:"+uuid);e.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:title",title);e.setAttributeNS(CommonDigitalNotamBuilder.XLINK,"xlink:type","simple");}
    private static void set(Element e,String value){e.setTextContent(value);}
    private static void regenerateIds(Node root){if(root instanceof Element e&&e.hasAttributeNS(CommonDigitalNotamBuilder.GML,"id"))e.setAttributeNS(CommonDigitalNotamBuilder.GML,"gml:id","id_"+UUID.randomUUID());for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling())regenerateIds(n);}
}
