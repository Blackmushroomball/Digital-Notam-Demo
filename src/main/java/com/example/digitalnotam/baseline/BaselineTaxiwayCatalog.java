package com.example.digitalnotam.baseline;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.util.*;

public final class BaselineTaxiwayCatalog {
    public BaselineTaxiwayCatalog() {}
    private static final Path EADD_TAXIWAYS=Path.of("data","virtual data","Donlon_2025","Donlon","DONLON original files","DONLON International","Donlon_EADD_Taxiway.xml");

    public Map<String,String> identifiers(String airport)throws Exception{
        if(!"EADD".equalsIgnoreCase(airport))throw new IllegalArgumentException("当前基线仅支持EADD");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
        Document d=f.newDocumentBuilder().parse(EADD_TAXIWAYS.toFile());NodeList taxiways=d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","Taxiway");Map<String,String> result=new LinkedHashMap<>();
        for(int i=0;i<taxiways.getLength();i++){Element taxiway=(Element)taxiways.item(i);String id=taxiway.getAttributeNS("http://www.opengis.net/gml/3.2","id").replaceFirst("^uuid\\.","");NodeList names=taxiway.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","designator");if(names.getLength()>0)result.put(names.item(0).getTextContent().trim(),id);}
        return result;
    }

    public String json(String airport)throws Exception{return "["+identifiers(airport).keySet().stream().map(x->"{\"designator\":\""+x+"\"}").reduce((a,b)->a+","+b).orElse("")+"]";}
}
