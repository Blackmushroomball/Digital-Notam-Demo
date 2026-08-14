package com.example.digitalnotam.baseline;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

public final class BaselineAirspaceCatalog {
    public static final Path DATA=Path.of("data","virtual data","Donlon_2025","Donlon","DONLON original files","Common","Donlon_Airspace.xml");
    public static final Path GEO_BORDERS=Path.of("data","virtual data","Donlon_2025","Donlon","DONLON original files","Common","Donlon_GeoBorder.xml");
    private static final Path GROUPS=Path.of("config","scenarios","atsa-act-airspace-groups.properties");
    private final Map<String,Airspace> airspaces;
    private final List<Group> groups;
    public BaselineAirspaceCatalog(){
        try{
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
            Document d=f.newDocumentBuilder().parse(DATA.toFile()),gbd=f.newDocumentBuilder().parse(GEO_BORDERS.toFile());
            Map<String,Element> slices=new LinkedHashMap<>(),borders=new LinkedHashMap<>();Map<String,Airspace> found=new LinkedHashMap<>();
            NodeList list=d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","Airspace");
            for(int i=0;i<list.getLength();i++){Element feature=(Element)list.item(i);Element slice=baselineSlice(feature);if(slice==null)continue;String uuid=text(feature,"identifier");if(!uuid.isBlank())slices.put(uuid,slice);}
            NodeList borderNodes=gbd.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","GeoBorder");
            for(int i=0;i<borderNodes.getLength();i++){Element feature=(Element)borderNodes.item(i);Element slice=baselineGeoBorderSlice(feature);String uuid=text(feature,"identifier");if(slice!=null&&!uuid.isBlank())borders.put(uuid,slice);}
            BaselineGeometryResolver geometry=new BaselineGeometryResolver(slices,borders);
            for(var entry:slices.entrySet()){String uuid=entry.getKey();Element slice=entry.getValue();String type=text(slice,"type");if(type.isBlank())continue;
                List<Volume> volumes=resolveVolumes(uuid,slices,new LinkedHashSet<>(),new HashMap<>());
                List<Point> points;String geometryError="";try{points=geometry.resolve(uuid);}catch(Exception e){points=List.of();geometryError=e.getMessage();}
                found.put(uuid,new Airspace(uuid,type,text(slice,"localType"),text(slice,"name"),text(slice,"designator"),text(slice,"classification"),points,List.copyOf(volumes),(Element)slice.cloneNode(true),geometryError));
            }
            airspaces=Map.copyOf(found);groups=loadGroups(found);
        }catch(Exception e){throw new IllegalStateException("Unable to read Airspace baseline: "+e.getMessage(),e);}
    }
    public Airspace get(String uuid){Airspace a=airspaces.get(uuid);if(a==null)throw new IllegalArgumentException("Airspace baseline not found: "+uuid);return a;}
    /** All resolved baseline airspaces, used by spatial association services. */
    public Collection<Airspace> all(){return airspaces.values();}
    public Group group(String id){return groups.stream().filter(g->g.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown ATSA.ACT airspace group: "+id));}
    public List<Airspace> resolve(String groupId,String selected){
        Group g=group(groupId);Set<String> ids=new LinkedHashSet<>(Arrays.stream(selected.split(",")).map(String::trim).filter(x->!x.isBlank()).toList());
        if(ids.isEmpty())throw new IllegalArgumentException("ATSA.ACT requires at least one airspace");
        if(!new HashSet<>(g.members()).containsAll(ids))throw new IllegalArgumentException("Selected airspaces must belong to group "+groupId);
        List<Airspace> result=ids.stream().map(this::get).toList();
        for(Airspace a:result)if(a.points().isEmpty())throw new IllegalArgumentException("Unable to resolve horizontal geometry for "+a.name()+" ("+a.uuid()+")"+(a.geometryError().isBlank()?"":": "+a.geometryError()));
        return result;
    }
    public List<Group> groups(){return groups;}
    public String json(){
        StringBuilder out=new StringBuilder("[");for(int i=0;i<groups.size();i++){if(i>0)out.append(',');Group g=groups.get(i);out.append("{\"id\":\"").append(esc(g.id())).append("\",\"name\":\"").append(esc(g.name())).append("\",\"members\":[");
            for(int j=0;j<g.members().size();j++){if(j>0)out.append(',');Airspace a=get(g.members().get(j));out.append("{\"uuid\":\"").append(a.uuid()).append("\",\"type\":\"").append(esc(a.type())).append("\",\"name\":\"").append(esc(a.name())).append("\",\"designator\":\"").append(esc(a.designator())).append("\",\"classification\":\"").append(esc(a.classification())).append("\",\"points\":[");
                for(int k=0;k<a.points().size();k++){if(k>0)out.append(',');Point p=a.points().get(k);out.append("{\"latitude\":").append(p.latitude()).append(",\"longitude\":").append(p.longitude()).append('}');}
                out.append("]}");}
            out.append("]}");}return out.append(']').toString();
    }
    private static List<Group> loadGroups(Map<String,Airspace> all)throws Exception{
        Properties p=new Properties();try(InputStream in=Files.newInputStream(GROUPS)){p.load(in);}Set<String> grouped=new HashSet<>();List<Group> result=new ArrayList<>();
        for(String id:new TreeSet<>(p.stringPropertyNames())){String[] parts=p.getProperty(id).split("\\|",2);if(parts.length!=2)throw new IllegalArgumentException("Invalid group "+id);List<String> ids=Arrays.stream(parts[1].split(",")).map(String::trim).toList();if(!all.keySet().containsAll(ids))throw new IllegalArgumentException("Group "+id+" references missing baseline");grouped.addAll(ids);result.add(new Group(id,parts[0],ids));}
        all.values().stream().filter(a->!grouped.contains(a.uuid())).sorted(Comparator.comparing(Airspace::name)).forEach(a->result.add(new Group("ASE_"+a.uuid(),a.name().isBlank()?a.designator():a.name(),List.of(a.uuid()))));
        return List.copyOf(result);
    }
    private static Element baselineSlice(Element feature){for(Element e:all(feature,"AirspaceTimeSlice"))if("BASELINE".equals(text(e,"interpretation")))return e;return null;}
    private static Element baselineGeoBorderSlice(Element feature){for(Element e:all(feature,"GeoBorderTimeSlice"))if("BASELINE".equals(text(e,"interpretation")))return e;return null;}
    private static List<Volume> resolveVolumes(String uuid,Map<String,Element> slices,LinkedHashSet<String> path,Map<String,List<Volume>> cache){
        if(cache.containsKey(uuid))return cache.get(uuid);Element slice=slices.get(uuid);if(slice==null)return List.of();
        if(!path.add(uuid))throw new IllegalArgumentException("Circular Airspace vertical dependency: "+String.join(" -> ",path)+" -> "+uuid);
        List<Volume> result=new ArrayList<>();for(Element volume:all(slice,"AirspaceVolume")){String lower=directText(volume,"lowerLimit"),upper=directText(volume,"upperLimit");String lu=directUom(volume,"lowerLimit"),uu=directUom(volume,"upperLimit");if(!lower.isBlank()&&!upper.isBlank())result.add(new Volume(lower,lu,upper,uu));}
        for(Element link:all(slice,"theAirspace")){String href=link.getAttributeNS("http://www.w3.org/1999/xlink","href");int p=href.lastIndexOf(':');String target=p<0?href:href.substring(p+1);if(!target.isBlank()&&!target.equals(uuid))result.addAll(resolveVolumes(target,slices,path,cache));}
        path.remove(uuid);List<Volume> value=List.copyOf(new LinkedHashSet<>(result));cache.put(uuid,value);return value;
    }
    private static List<Element> all(Element p,String local){NodeList n=p.getElementsByTagNameNS("*",local);List<Element> r=new ArrayList<>();for(int i=0;i<n.getLength();i++)r.add((Element)n.item(i));return r;}
    private static String text(Element p,String local){NodeList n=p.getElementsByTagNameNS("*",local);return n.getLength()==0?"":n.item(0).getTextContent().trim();}
    private static String directText(Element p,String local){for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))return e.getTextContent().trim();return "";}
    private static String directUom(Element p,String local){for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))return e.getAttribute("uom");return "";}
    private static String esc(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
    public record Point(double latitude,double longitude){}
    public record Volume(String lower,String lowerUom,String upper,String upperUom){}
    public record Airspace(String uuid,String type,String localType,String name,String designator,String classification,List<Point> points,List<Volume> volumes,Element baselineSlice,String geometryError){}
    public record Group(String id,String name,List<String> members){}
}
