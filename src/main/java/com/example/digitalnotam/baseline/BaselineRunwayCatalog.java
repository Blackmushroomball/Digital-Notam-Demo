package com.example.digitalnotam.baseline;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class BaselineRunwayCatalog {
    private static final String AIXM="http://www.aixm.aero/schema/5.1.1",GML="http://www.opengis.net/gml/3.2",XLINK="http://www.w3.org/1999/xlink";
    private static final Path RUNWAYS=Path.of("data","virtual data","Donlon_2025","Donlon","DONLON original files","DONLON International","Donlon_EADD_Runway.xml");
    private static final Path DIRECTIONS=Path.of("data","virtual data","Donlon_2025","Donlon","DONLON original files","DONLON International","Donlon_EADD_RunwayDirection.xml");

    public List<RunwayBaseline> list(String airport,Instant start,Instant end)throws Exception{
        if(!"EADD".equalsIgnoreCase(airport))throw new IllegalArgumentException("RWY.CLS 第一版仅支持 EADD");
        Map<String,List<RunwayDirectionBaseline>> directions=readDirections(start,end);List<RunwayBaseline> result=new ArrayList<>();Document d=parse(RUNWAYS);
        NodeList features=d.getElementsByTagNameNS(AIXM,"Runway");for(int i=0;i<features.getLength();i++){Element feature=(Element)features.item(i),slice=baselineSlice(feature,"RunwayTimeSlice",start,end);if(slice==null)continue;String airportUuid=href(slice,"associatedAirportHeliport");if(!"1b54b2d6-a5ff-4e57-94c2-f4047a381c64".equals(airportUuid))continue;String uuid=uuid(feature),type=text(slice,"type");if(!Set.of("RWY","FATO").contains(type))continue;List<String> surfaces=new ArrayList<>();NodeList compositions=slice.getElementsByTagNameNS(AIXM,"composition");for(int j=0;j<compositions.getLength();j++)surfaces.add(compositions.item(j).getTextContent().trim());result.add(new RunwayBaseline(uuid,text(slice,"designator"),type,List.copyOf(surfaces),List.copyOf(directions.getOrDefault(uuid,List.of()))));}
        result.sort(Comparator.comparing(RunwayBaseline::designator));return result;
    }
    public RunwayBaseline require(String airport,String uuid,Instant start,Instant end)throws Exception{return list(airport,start,end).stream().filter(r->r.uuid().equals(uuid)).findFirst().orElseThrow(()->new IllegalArgumentException("所选跑道不属于 EADD 有效基线: "+uuid));}
    public ResolvedTarget resolve(String airport,String runwayUuid,String targetType,String directionUuid,Instant start,Instant end)throws Exception{RunwayBaseline runway=require(airport,runwayUuid,start,end);List<RunwayDirectionBaseline> selected;if("RUNWAY".equals(targetType)){selected=runway.directions();if(selected.isEmpty())throw new IllegalArgumentException("所选跑道没有关联 RunwayDirection");}else if("RUNWAY_DIRECTION".equals(targetType)){selected=runway.directions().stream().filter(x->x.uuid().equals(directionUuid)).toList();if(selected.size()!=1)throw new IllegalArgumentException("所选 RunwayDirection 不属于该跑道");}else throw new IllegalArgumentException("RWY.CLS 关闭对象必须为 RUNWAY 或 RUNWAY_DIRECTION");return new ResolvedTarget(runway,selected.stream().sorted(directionComparator()).toList());}
    public String json(String airport)throws Exception{return "["+list(airport,null,null).stream().map(this::json).reduce((a,b)->a+","+b).orElse("")+"]";}
    private String json(RunwayBaseline r){return "{\"uuid\":\""+r.uuid()+"\",\"designator\":\""+r.designator()+"\",\"type\":\""+r.type()+"\",\"surfaceCompositions\":["+r.surfaceCompositions().stream().map(x->"\""+x+"\"").reduce((a,b)->a+","+b).orElse("")+"],\"directions\":["+r.directions().stream().map(x->"{\"uuid\":\""+x.uuid()+"\",\"designator\":\""+x.designator()+"\"}").reduce((a,b)->a+","+b).orElse("")+"]}";}

    private static Map<String,List<RunwayDirectionBaseline>> readDirections(Instant start,Instant end)throws Exception{Map<String,List<RunwayDirectionBaseline>> result=new HashMap<>();Document d=parse(DIRECTIONS);NodeList features=d.getElementsByTagNameNS(AIXM,"RunwayDirection");for(int i=0;i<features.getLength();i++){Element feature=(Element)features.item(i),slice=baselineSlice(feature,"RunwayDirectionTimeSlice",start,end);if(slice==null)continue;String runway=href(slice,"usedRunway");List<Element> normal=new ArrayList<>();for(Element wrapper:BaselineAirportHeliportCatalog.directChildren(slice,"availability"))if("NORMAL".equals(BaselineAirportHeliportCatalog.text(wrapper,"operationalStatus")))normal.add(wrapper);int sequence=Integer.parseInt(text(slice,"sequenceNumber"));result.computeIfAbsent(runway,k->new ArrayList<>()).add(new RunwayDirectionBaseline(uuid(feature),text(slice,"designator"),runway,sequence,List.copyOf(normal)));}for(List<RunwayDirectionBaseline> values:result.values())values.sort(directionComparator());return result;}
    private static Comparator<RunwayDirectionBaseline> directionComparator(){return Comparator.comparingInt((RunwayDirectionBaseline x)->Integer.parseInt(x.designator().replaceAll("\\D.*$",""))).thenComparing(RunwayDirectionBaseline::designator);}
    private static Element baselineSlice(Element feature,String local,Instant start,Instant end){NodeList slices=feature.getElementsByTagNameNS(AIXM,local);for(int i=slices.getLength()-1;i>=0;i--){Element slice=(Element)slices.item(i);if("BASELINE".equals(text(slice,"interpretation"))&&(start==null||covers(slice,start,end)))return slice;}return null;}
    private static boolean covers(Element slice,Instant start,Instant end){Element valid=BaselineAirportHeliportCatalog.first(slice,"validTime"),begin=valid==null?null:BaselineAirportHeliportCatalog.first(valid,"beginPosition"),finish=valid==null?null:BaselineAirportHeliportCatalog.first(valid,"endPosition");if(begin==null||Instant.parse(begin.getTextContent().trim()).isAfter(start))return false;return finish==null||"unknown".equals(finish.getAttribute("indeterminatePosition"))||!Instant.parse(finish.getTextContent().trim()).isBefore(end);}
    private static String href(Element slice,String local){Element e=BaselineAirportHeliportCatalog.first(slice,local);return e==null?"":e.getAttributeNS(XLINK,"href").replaceFirst("^urn:uuid:","");}
    private static String uuid(Element feature){return feature.getAttributeNS(GML,"id").replaceFirst("^uuid\\.","");}
    private static String text(Element e,String local){return BaselineAirportHeliportCatalog.text(e,local);}
    private static Document parse(Path path)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");return f.newDocumentBuilder().parse(path.toFile());}
    public record RunwayBaseline(String uuid,String designator,String type,List<String> surfaceCompositions,List<RunwayDirectionBaseline> directions){}
    public record RunwayDirectionBaseline(String uuid,String designator,String runwayUuid,int sequenceNumber,List<Element> normalAvailabilities){}
    public record ResolvedTarget(RunwayBaseline runway,List<RunwayDirectionBaseline> directions){}
}
