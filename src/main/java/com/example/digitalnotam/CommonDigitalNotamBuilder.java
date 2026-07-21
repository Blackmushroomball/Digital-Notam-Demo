package com.example.digitalnotam;

import org.w3c.dom.*;
import org.xml.sax.InputSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class CommonDigitalNotamBuilder {
    static final String AIXM="http://www.aixm.aero/schema/5.1.1",EVENT="http://www.aixm.aero/schema/5.1.1/event",GML="http://www.opengis.net/gml/3.2",XLINK="http://www.w3.org/1999/xlink";

    Document populate(Path blueprint,Notam n)throws Exception{
        Document d=parse(Files.readString(blueprint,StandardCharsets.UTF_8));String[] number=n.number().split("/");
        setFirst(d,"series",number[0].substring(0,1));setFirst(d,"number",number[0].substring(1));setFirst(d,"year","20"+number[1]);
        setFirst(d,"scenario",n.scenario());setFirst(d,"name",n.title());setFirst(d,"text",n.condition());
        replaceAll(d,"beginPosition",n.effectiveStart());replaceAll(d,"endPosition",n.effectiveEnd());
        setFirst(d,"effectiveStart",date(n.effectiveStart()));setFirst(d,"effectiveEnd",date(n.effectiveEnd()));
        for(String[] x:new String[][]{{"issued",n.publishedAt()},{"selectionCode",n.qCode()},{"traffic",n.traffic()},{"purpose",n.purpose()},{"scope",n.scope()},{"minimumFL",n.minimumFl()},{"maximumFL",n.maximumFl()},{"coordinates",coordinates(n)},{"radius","%03d".formatted(Integer.parseInt(n.radiusNm()))}})setNotam(d,x[0],x[1]);
        return d;
    }

    void regenerateIds(Document d,String airportUuid,String designator,String name){
        NodeList all=d.getElementsByTagNameNS("*","*");for(int i=0;i<all.getLength();i++){Element e=(Element)all.item(i);if(e.hasAttributeNS(GML,"id"))e.setAttributeNS(GML,"gml:id","id_"+UUID.randomUUID());}
        d.getDocumentElement().setAttributeNS(GML,"gml:id","DN_AD.CLS_"+UUID.randomUUID());
        String eventUuid=UUID.randomUUID().toString();Element event=one(d,EVENT,"Event");event.setAttributeNS(GML,"gml:id","uuid."+eventUuid);one(event,GML,"identifier").setTextContent(eventUuid);
        Element airport=one(d,AIXM,"AirportHeliport");airport.setAttributeNS(GML,"gml:id","uuid."+airportUuid);one(airport,GML,"identifier").setTextContent(airportUuid);
        for(Element link:all(d,EVENT,"theEvent")){link.setAttributeNS(XLINK,"xlink:href","urn:uuid:"+eventUuid);link.setAttributeNS(XLINK,"xlink:title",designator+" "+name+" DNOTAM AD.CLS");}
    }

    static Document parse(String xml)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));}
    static Element one(Document d,String ns,String local){NodeList n=d.getElementsByTagNameNS(ns,local);if(n.getLength()==0)throw new IllegalArgumentException("XML 缺少元素: "+local);return(Element)n.item(0);}
    static Element one(Element e,String ns,String local){NodeList n=e.getElementsByTagNameNS(ns,local);if(n.getLength()==0)throw new IllegalArgumentException("XML 缺少元素: "+local);return(Element)n.item(0);}
    static List<Element> all(Document d,String ns,String local){NodeList n=d.getElementsByTagNameNS(ns,local);List<Element> r=new ArrayList<>();for(int i=0;i<n.getLength();i++)r.add((Element)n.item(i));return r;}
    static void setFirst(Document d,String local,String value){NodeList n=d.getElementsByTagNameNS("*",local);if(n.getLength()==0)throw new IllegalArgumentException("XML 缺少元素: "+local);n.item(0).setTextContent(value);}
    static void setNotam(Document d,String local,String value){one(one(d,EVENT,"NOTAM"),EVENT,local).setTextContent(value);}
    static void replaceAll(Document d,String local,String value){NodeList n=d.getElementsByTagNameNS("*",local);for(int i=0;i<n.getLength();i++)n.item(i).setTextContent(value);}
    private static String date(String iso){return Instant.parse(iso).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyMMddHHmm"));}
    private static String coordinates(Notam n){return coordinate(n.latitude(),2,n.latitudeHemisphere())+coordinate(n.longitude(),3,n.longitudeHemisphere());}
    private static String coordinate(String value,int width,String hem){double v=Double.parseDouble(value);int d=(int)Math.floor(v),m=(int)Math.round((v-d)*60);if(m==60){d++;m=0;}return("%0"+width+"d%02d%s").formatted(d,m,hem);}
}
