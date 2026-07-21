package com.example.digitalnotam;

import org.w3c.dom.*;
import java.util.*;

final class AdClsScenarioValidator {
    void validate(Document d,Notam n){
        single(d,CommonDigitalNotamBuilder.EVENT,"Event");single(d,CommonDigitalNotamBuilder.AIXM,"AirportHeliport");
        Element event=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.EVENT,"EventTimeSlice");
        expected(event,"interpretation","BASELINE");expected(event,"scenario","AD.CLS");expected(event,"version","2.0");
        Element slice=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.AIXM,"AirportHeliportTimeSlice");expected(slice,"interpretation","TEMPDELTA");
        int normal=0,closed=0;Element closedWrapper=null;
        for(Element w:BaselineAirportHeliportCatalog.directChildren(slice,"availability")){String s=BaselineAirportHeliportCatalog.text(w,"operationalStatus");if("NORMAL".equals(s))normal++;if("CLOSED".equals(s)){closed++;closedWrapper=w;}}
        if(normal==0)throw new IllegalArgumentException("AD.CLS TEMPDELTA 未保留基线 NORMAL availability");
        if(closed!=1)throw new IllegalArgumentException("AD.CLS 必须恰好包含一个 CLOSED availability");
        Element availability=BaselineAirportHeliportCatalog.first(closedWrapper,"AirportHeliportAvailability");
        boolean scheduled=!BaselineAirportHeliportCatalog.directChildren(availability,"timeInterval").isEmpty();
        if(scheduled=="CONTINUOUS".equals(n.scheduleMode()))throw new IllegalArgumentException("CLOSED availability 的 schedule 与输入不一致");
        for(Element annotation:BaselineAirportHeliportCatalog.directChildren(availability,"annotation")){
            Element note=BaselineAirportHeliportCatalog.first(annotation,"Note");
            if(note==null||!"REMARK".equals(BaselineAirportHeliportCatalog.text(note,"purpose")))throw new IllegalArgumentException("AD.CLS annotation 必须使用 purpose=REMARK");
            String property=BaselineAirportHeliportCatalog.text(note,"propertyName");if(!property.isBlank()&&!"operationalStatus".equals(property))throw new IllegalArgumentException("closure reason 必须关联 operationalStatus");
        }
        Element feature=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.EVENT,"Event");
        String eventId=feature.getAttributeNS(CommonDigitalNotamBuilder.GML,"id").replaceFirst("^uuid\\.","");
        String href=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.EVENT,"theEvent").getAttributeNS(CommonDigitalNotamBuilder.XLINK,"href");
        if(!("urn:uuid:"+eventId).equals(href))throw new IllegalArgumentException("theEvent 未指向本次 Event");
        Set<String> ids=new HashSet<>();NodeList all=d.getElementsByTagNameNS("*","*");for(int i=0;i<all.getLength();i++)checkId((Element)all.item(i),ids);
    }
    private static void checkId(Element e,Set<String> ids){if(e.hasAttributeNS(CommonDigitalNotamBuilder.GML,"id")){String id=e.getAttributeNS(CommonDigitalNotamBuilder.GML,"id");if(!ids.add(id))throw new IllegalArgumentException("重复的 gml:id: "+id);}}
    private static void single(Document d,String ns,String local){int n=d.getElementsByTagNameNS(ns,local).getLength();if(n!=1)throw new IllegalArgumentException("AD.CLS 必须恰好包含一个 "+local);}
    private static void expected(Element e,String local,String value){String actual=BaselineAirportHeliportCatalog.text(e,local);if(!value.equals(actual))throw new IllegalArgumentException(local+" 必须为 "+value);}
}
