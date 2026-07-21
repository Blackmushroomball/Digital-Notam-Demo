package com.example.digitalnotam.baseline;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.util.*;

public final class BaselineRunwayCatalog {
    public BaselineRunwayCatalog() {}
    private static final Path EADD_RUNWAYS=Path.of("data","virtual data","Donlon_2025","Donlon","DONLON original files","DONLON International","Donlon_EADD_Runway.xml");
    private static final Set<String> RWY_CLS_SUPPORTED=Set.of("09R/27L");

    public String json(String airport)throws Exception{
        if(!"EADD".equalsIgnoreCase(airport))throw new IllegalArgumentException("当前基线仅支持EADD");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
        Document d=f.newDocumentBuilder().parse(EADD_RUNWAYS.toFile());NodeList runways=d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","Runway");List<String> rows=new ArrayList<>();
        for(int i=0;i<runways.getLength();i++){Element runway=(Element)runways.item(i);NodeList names=runway.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","designator");if(names.getLength()==0)continue;String name=names.item(0).getTextContent().trim();rows.add("{\"designator\":\""+name+"\",\"supported\":"+RWY_CLS_SUPPORTED.contains(name)+"}");}
        return "["+String.join(",",rows)+"]";
    }
}
