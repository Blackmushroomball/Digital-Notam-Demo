package com.example.digitalnotam.scenario.adlim;

import com.example.digitalnotam.baseline.BaselineAirportHeliportCatalog;
import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.xml.CommonDigitalNotamBuilder;
import org.w3c.dom.*;
import java.util.*;

final class AdLimScenarioValidator {
    private final AdLimConfiguration config;
    AdLimScenarioValidator(AdLimConfiguration config){this.config=config;}
    void validate(Document d,Notam n){
        Element event=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.EVENT,"EventTimeSlice");expected(event,"interpretation","BASELINE");expected(event,"scenario","AD.LIM");expected(event,"version","2.0");
        Element slice=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.AIXM,"AirportHeliportTimeSlice");expected(slice,"interpretation","TEMPDELTA");int normal=0;List<Element> eventAvailabilities=new ArrayList<>();
        for(Element w:BaselineAirportHeliportCatalog.directChildren(slice,"availability")){String s=BaselineAirportHeliportCatalog.text(w,"operationalStatus");if("NORMAL".equals(s))normal++;if("LIMITED".equals(s)||"OTHER:EXTENDED".equals(s))eventAvailabilities.add(BaselineAirportHeliportCatalog.first(w,"AirportHeliportAvailability"));}
        if(normal==0)throw new IllegalArgumentException("AD.LIM TEMPDELTA 未保留基线 NORMAL availability");if(eventAvailabilities.size()!=1)throw new IllegalArgumentException("AD.LIM 必须恰好包含一个事件 availability");Element availability=eventAvailabilities.get(0);NodeList usages=availability.getElementsByTagNameNS(CommonDigitalNotamBuilder.AIXM,"AirportHeliportUsage");if(usages.getLength()<1)throw new IllegalArgumentException("AD.LIM 必须至少包含一个 AirportHeliportUsage");String status=BaselineAirportHeliportCatalog.text(availability,"operationalStatus");for(int i=0;i<usages.getLength();i++){Element usage=(Element)usages.item(i);String type=BaselineAirportHeliportCatalog.text(usage,"type"),expectedStatus="PERMIT".equals(type)?"OTHER:EXTENDED":"LIMITED";if(!expectedStatus.equals(status))throw new IllegalArgumentException("availability 的 usage type 与 operationalStatus 不一致");String operation=BaselineAirportHeliportCatalog.text(usage,"operation");if(!config.operations.contains(operation)&&!operation.matches("OTHER:[A-Z0-9][A-Z0-9_\\-]*"))throw new IllegalArgumentException("operation 不在受控配置中");Element combination=BaselineAirportHeliportCatalog.first(usage,"ConditionCombination"),flight=BaselineAirportHeliportCatalog.first(usage,"FlightCharacteristic"),aircraft=BaselineAirportHeliportCatalog.first(usage,"AircraftCharacteristic");if((flight!=null||aircraft!=null)&&combination==null)throw new IllegalArgumentException("Flight/Aircraft 必须编码在 ConditionCombination 中");if(combination!=null){String expected=flight!=null&&aircraft!=null?"AND":"NONE";expected(combination,"logicalOperator",expected);}}
        Set<String> ids=new HashSet<>();NodeList all=d.getElementsByTagNameNS("*","*");for(int i=0;i<all.getLength();i++){Element e=(Element)all.item(i);if(e.hasAttributeNS(CommonDigitalNotamBuilder.GML,"id")&&!ids.add(e.getAttributeNS(CommonDigitalNotamBuilder.GML,"id")))throw new IllegalArgumentException("重复的 gml:id");}
    }
    private static void expected(Element e,String local,String value){if(!value.equals(BaselineAirportHeliportCatalog.text(e,local)))throw new IllegalArgumentException(local+" 必须为 "+value);}
}
