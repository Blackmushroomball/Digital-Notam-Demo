package com.example.digitalnotam;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

final class BaselineAirportHeliportCatalog {
    static final String AIXM="http://www.aixm.aero/schema/5.1.1", GML="http://www.opengis.net/gml/3.2", XLINK="http://www.w3.org/1999/xlink";
    private final Properties config=new Properties();
    BaselineAirportHeliportCatalog(){try(InputStream in=Files.newInputStream(Path.of("config","donlon-baseline.properties"))){config.load(in);}catch(Exception e){throw new IllegalStateException("无法读取机场基线配置: "+e.getMessage(),e);}}

    List<AirportSummary> list()throws Exception{List<AirportSummary> result=new ArrayList<>();for(String code:required("airports").split(",")){AirportBaseline b=findLatest(code.trim());result.add(new AirportSummary(b.designator(),b.name(),b.type(),b.locationIndicatorIcao(),b.latitude(),b.longitude(),b.firDesignator(),b.firSource()));}return result;}
    String json()throws Exception{return "["+list().stream().map(a->"{\"designator\":\""+a.designator()+"\",\"name\":\""+a.name()+"\",\"type\":\""+a.type()+"\",\"locationIndicatorICAO\":\""+a.locationIndicatorIcao()+"\",\"latitude\":\""+a.latitude()+"\",\"longitude\":\""+a.longitude()+"\",\"fir\":\""+a.firDesignator()+"\",\"firSource\":\""+a.firSource()+"\"}").reduce((a,b)->a+","+b).orElse("")+"]";}
    AirportBaseline find(String designator,Instant start,Instant end)throws Exception{return read(designator,start,end);}
    private AirportBaseline findLatest(String designator)throws Exception{return read(designator,null,null);}

    private AirportBaseline read(String designator,Instant start,Instant end)throws Exception{
        String code=designator.toUpperCase(Locale.ROOT);String dataset=required("airport."+code+".dataset");Document d=parse(Path.of(dataset));
        NodeList features=d.getElementsByTagNameNS(AIXM,"AirportHeliport");
        for(int i=0;i<features.getLength();i++){Element feature=(Element)features.item(i);NodeList slices=feature.getElementsByTagNameNS(AIXM,"AirportHeliportTimeSlice");
            for(int j=slices.getLength()-1;j>=0;j--){Element slice=(Element)slices.item(j);if(!"BASELINE".equals(text(slice,"interpretation"))||!code.equalsIgnoreCase(text(slice,"designator")))continue;if(start!=null&&!covers(slice,start,end))continue;
                List<Element> normal=new ArrayList<>();for(Element wrapper:directChildren(slice,"availability"))if("NORMAL".equals(text(wrapper,"operationalStatus")))normal.add(wrapper);
                String[] arp=arp(slice);FirReference fir=resolveFir(slice,code);
                return new AirportBaseline(code,text(slice,"name"),text(slice,"type"),nilAwareText(slice,"locationIndicatorICAO"),feature.getAttributeNS(GML,"id").replaceFirst("^uuid\\.",""),arp[0],arp[1],fir.designator(),fir.uuid(),fir.source(),List.copyOf(normal));}}
        throw new IllegalArgumentException("没有覆盖通告有效期的机场 BASELINE TimeSlice: "+code);
    }
    private FirReference resolveFir(Element slice,String airport){
        NodeList relations=slice.getElementsByTagNameNS("*","*");for(int i=0;i<relations.getLength();i++){Element relation=(Element)relations.item(i);if(!relation.hasAttributeNS(XLINK,"href"))continue;String href=relation.getAttributeNS(XLINK,"href"),title=relation.getAttributeNS(XLINK,"title");if(title.toUpperCase(Locale.ROOT).matches(".*\\bFIR\\b.*"))return new FirReference(title.split("\\s+")[0],href.replaceFirst("^urn:uuid:",""),"BASELINE_RELATION");}
        String designator=config.getProperty("airport."+airport+".fir.designator","").trim(),uuid=config.getProperty("airport."+airport+".fir.uuid","").trim();
        if(designator.isEmpty()||uuid.isEmpty())throw new IllegalArgumentException("机场基线没有 FIR 关联，受控映射也缺失: "+airport);
        return new FirReference(designator,uuid,"CONTROLLED_MAPPING");
    }
    private static String[] arp(Element slice){Element arp=first(slice,"ARP"),pos=arp==null?null:first(arp,"pos");if(pos==null)throw new IllegalArgumentException("机场基线缺少 ARP 坐标: "+text(slice,"designator"));String[] p=pos.getTextContent().trim().split("\\s+");return new String[]{p[0],p[1]};}
    private static boolean covers(Element slice,Instant start,Instant end){Element valid=first(slice,"validTime"),begin=valid==null?null:first(valid,"beginPosition"),finish=valid==null?null:first(valid,"endPosition");if(begin==null||Instant.parse(begin.getTextContent().trim()).isAfter(start))return false;return finish==null||"unknown".equals(finish.getAttribute("indeterminatePosition"))||!Instant.parse(finish.getTextContent().trim()).isBefore(end);}
    private static Document parse(Path path)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");return f.newDocumentBuilder().parse(path.toFile());}
    private String required(String key){String value=config.getProperty(key,"").trim();if(value.isEmpty())throw new IllegalStateException("机场基线配置缺少: "+key);return value;}
    private static String nilAwareText(Element parent,String local){Element e=first(parent,local);return e==null||"true".equals(e.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance","nil"))?"":e.getTextContent().trim();}
    static List<Element> directChildren(Element parent,String local){List<Element> r=new ArrayList<>();for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))r.add(e);return r;}
    static Element first(Element parent,String local){NodeList n=parent.getElementsByTagNameNS("*",local);return n.getLength()==0?null:(Element)n.item(0);}
    static String text(Element parent,String local){Element e=first(parent,local);return e==null?"":e.getTextContent().trim();}
    record AirportBaseline(String designator,String name,String type,String locationIndicatorIcao,String uuid,String latitude,String longitude,String firDesignator,String firUuid,String firSource,List<Element> normalAvailabilities){}
    record AirportSummary(String designator,String name,String type,String locationIndicatorIcao,String latitude,String longitude,String firDesignator,String firSource){}
    private record FirReference(String designator,String uuid,String source){}
}
