package com.example.digitalnotam.baseline;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.*;

public final class BaselineNavaidCatalog {
    public static final Path DATA=Path.of("data","virtual data","Donlon_2025","Donlon","DONLON original files","Common","Donlon_Navaid.xml");
    private static final String GML="http://www.opengis.net/gml/3.2",XLINK="http://www.w3.org/1999/xlink";
    private final Map<String,Equipment> equipment;
    private final Map<String,Navaid> navaids;

    public BaselineNavaidCatalog(){
        try{
            Document d=parse(DATA);Map<String,Equipment> eq=new LinkedHashMap<>();Map<String,Navaid> nav=new LinkedHashMap<>();
            NodeList members=d.getElementsByTagNameNS("*","hasMember");
            for(int i=0;i<members.getLength();i++){Element feature=firstElement((Element)members.item(i));if(feature==null||"Navaid".equals(feature.getLocalName())||"Event".equals(feature.getLocalName()))continue;Element slice=baselineSlice(feature);if(slice==null)continue;String uuid=uuid(feature),type=feature.getLocalName();if(!isEquipment(type))continue;String[] pos=position(slice);
                Element frequency=direct(slice,"frequency"),channel=direct(slice,"channel");
                eq.put(uuid,new Equipment(uuid,type,text(slice,"designator"),text(slice,"name"),text(slice,"class"),
                        frequency==null?"":frequency.getTextContent().trim(),frequency==null?"":frequency.getAttribute("uom"),
                        channel==null?"":channel.getTextContent().trim(),pos[0],pos[1],statuses(slice),(Element)slice.cloneNode(true)));
            }
            for(int i=0;i<members.getLength();i++){Element feature=firstElement((Element)members.item(i));if(feature==null||!"Navaid".equals(feature.getLocalName()))continue;Element slice=baselineSlice(feature);if(slice==null)continue;List<Component> components=new ArrayList<>();
                for(Element wrapper:directChildren(slice,"navaidEquipment")){Element component=descendant(wrapper,"NavaidComponent"),link=component==null?null:direct(component,"theNavaidEquipment");if(link==null)continue;String id=href(link);Equipment e=eq.get(id);if(e!=null)components.add(new Component(id,e.type(),nilText(component,"markerPosition"),isPrimary(text(slice,"type"),e.type())));}
                String[] pos=position(slice);if(pos[0].isBlank())for(Component c:components){Equipment e=eq.get(c.equipmentUuid());if(!e.latitude().isBlank()){pos=new String[]{e.latitude(),e.longitude()};break;}}
                Element runway=direct(slice,"runwayDirection"),served=direct(slice,"servedAirport");String airport=airportCode(served);
                nav.put(uuid(feature),new Navaid(uuid(feature),text(slice,"type"),text(slice,"designator"),text(slice,"name"),pos[0],pos[1],
                        runway==null?"":href(runway),runway==null?"":runway.getAttributeNS(XLINK,"title"),airport,List.copyOf(components),statuses(slice),(Element)slice.cloneNode(true)));
            }
            equipment=Map.copyOf(eq);navaids=Map.copyOf(nav);
        }catch(Exception e){throw new IllegalStateException("Unable to read Navaid baseline: "+e.getMessage(),e);}
    }

    public List<Navaid> list(){return navaids.values().stream().filter(this::complete).sorted(Comparator.comparing(Navaid::name).thenComparing(Navaid::designator)).toList();}
    public Navaid require(String uuid){Navaid n=navaids.get(uuid);if(n==null||!complete(n))throw new IllegalArgumentException("Navaid has no complete baseline: "+uuid);return n;}
    public Equipment equipment(String uuid){Equipment e=equipment.get(uuid);if(e==null)throw new IllegalArgumentException("NavaidEquipment baseline not found: "+uuid);return e;}
    public List<Navaid> usingAsPrimary(String equipmentUuid){return list().stream().filter(n->n.components().stream().anyMatch(c->c.primary()&&c.equipmentUuid().equals(equipmentUuid))).toList();}
    public List<Equipment> affected(Navaid n,String impactMode,String equipmentUuid){
        if("ALL_PRIMARY".equals(impactMode))return n.components().stream().filter(Component::primary).map(c->equipment(c.equipmentUuid())).toList();
        if(Set.of("COMPONENT","SIGNAL").contains(impactMode)){if(n.components().stream().noneMatch(c->c.equipmentUuid().equals(equipmentUuid)))throw new IllegalArgumentException("Selected equipment does not belong to Navaid");return List.of(equipment(equipmentUuid));}
        throw new IllegalArgumentException("NAV.UNS impactMode must be ALL_PRIMARY, COMPONENT or SIGNAL");
    }
    public String json(){return "["+list().stream().map(this::json).reduce((a,b)->a+","+b).orElse("")+"]";}
    private String json(Navaid n){return "{\"uuid\":\""+n.uuid()+"\",\"type\":\""+esc(n.type())+"\",\"designator\":\""+esc(n.designator())+"\",\"name\":\""+esc(n.name())+"\",\"latitude\":\""+n.latitude()+"\",\"longitude\":\""+n.longitude()+"\",\"runwayDirection\":\""+esc(n.runwayDirectionTitle())+"\",\"servedAirport\":\""+esc(n.servedAirport())+"\",\"baselineStatuses\":"+array(n.baselineStatuses())+",\"components\":["+n.components().stream().map(c->{Equipment e=equipment(c.equipmentUuid());return "{\"uuid\":\""+e.uuid()+"\",\"type\":\""+e.type()+"\",\"designator\":\""+esc(e.designator())+"\",\"frequency\":\""+esc(e.frequency())+"\",\"frequencyUom\":\""+esc(e.frequencyUom())+"\",\"channel\":\""+esc(e.channel())+"\",\"markerPosition\":\""+esc(c.markerPosition())+"\",\"primary\":"+c.primary()+",\"baselineStatuses\":"+array(e.baselineStatuses())+"}";}).reduce((a,b)->a+","+b).orElse("")+"]}";}
    private boolean complete(Navaid n){return !n.type().isBlank()&&!n.components().isEmpty()&&!n.latitude().isBlank()&&n.components().stream().allMatch(c->equipment.containsKey(c.equipmentUuid()));}
    private static boolean isPrimary(String nav,String equipment){return switch(nav){case"ILS"->Set.of("Localizer","Glidepath").contains(equipment);case"ILS_DME"->Set.of("Localizer","Glidepath","DME").contains(equipment);case"MLS"->Set.of("Azimuth","Elevation").contains(equipment);case"MLS_DME"->Set.of("Azimuth","Elevation","DME").contains(equipment);default->true;};}
    private static boolean isEquipment(String local){return Set.of("Azimuth","DME","DirectionFinder","Elevation","Glidepath","Localizer","MarkerBeacon","NDB","SDF","TACAN","VOR").contains(local);}
    private static List<String> statuses(Element slice){List<String> result=new ArrayList<>();for(Element w:directChildren(slice,"availability")){String value=text(w,"operationalStatus");if(!value.isBlank())result.add(value);}return result.isEmpty()?List.of("UNKNOWN"):List.copyOf(result);}
    private static String airportCode(Element served){if(served==null)return "";String title=served.getAttributeNS(XLINK,"title");var m=java.util.regex.Pattern.compile("\\bEA[A-Z0-9]{2,3}\\b").matcher(title);return m.find()?m.group():"";}
    private static String[] position(Element slice){Element p=descendant(slice,"pos");if(p==null)return new String[]{"",""};String[] x=p.getTextContent().trim().split("\\s+");return x.length<2?new String[]{"",""}:new String[]{x[0],x[1]};}
    private static String href(Element e){return e.getAttributeNS(XLINK,"href").replaceFirst("^urn:uuid:","");}
    private static String uuid(Element e){return e.getAttributeNS(GML,"id").replaceFirst("^uuid\\.","");}
    private static Element baselineSlice(Element feature){NodeList slices=feature.getElementsByTagNameNS("*",feature.getLocalName()+"TimeSlice");for(int i=slices.getLength()-1;i>=0;i--){Element s=(Element)slices.item(i);if("BASELINE".equals(text(s,"interpretation")))return s;}return null;}
    private static Element firstElement(Element p){for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e)return e;return null;}
    private static List<Element> directChildren(Element p,String local){List<Element> r=new ArrayList<>();for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))r.add(e);return r;}
    private static Element direct(Element p,String local){for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))return e;return null;}
    private static Element descendant(Element p,String local){NodeList n=p.getElementsByTagNameNS("*",local);return n.getLength()==0?null:(Element)n.item(0);}
    private static String text(Element p,String local){Element e=descendant(p,local);return e==null?"":e.getTextContent().trim();}
    private static String nilText(Element p,String local){Element e=direct(p,local);return e==null||"true".equals(e.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance","nil"))?"":e.getTextContent().trim();}
    private static Document parse(Path p)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");return f.newDocumentBuilder().parse(p.toFile());}
    private static String esc(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
    private static String array(List<String> values){return "["+values.stream().map(x->"\""+esc(x)+"\"").reduce((a,b)->a+","+b).orElse("")+"]";}
    public record Component(String equipmentUuid,String equipmentType,String markerPosition,boolean primary){}
    public record Equipment(String uuid,String type,String designator,String name,String equipmentClass,String frequency,String frequencyUom,String channel,String latitude,String longitude,List<String> baselineStatuses,Element baselineSlice){}
    public record Navaid(String uuid,String type,String designator,String name,String latitude,String longitude,String runwayDirectionUuid,String runwayDirectionTitle,String servedAirport,List<Component> components,List<String> baselineStatuses,Element baselineSlice){}
}
