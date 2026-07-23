package com.example.digitalnotam.scenario.navuns;

import com.example.digitalnotam.baseline.BaselineNavaidCatalog;
import com.example.digitalnotam.scenario.common.QCodeDefaults;
import java.util.*;

public final class NavUnsQCodeResolver {
    private final QCodeDefaults defaults=new QCodeDefaults();
    public Mapping resolve(BaselineNavaidCatalog.Navaid n,List<BaselineNavaidCatalog.Equipment> affected,String navaidStatus){
        String subject=subject(n,affected),condition=switch(NavUnsStatusResolver.base(navaidStatus)){case"UNSERVICEABLE","PARTIAL"->"AS";case"ONTEST"->"CT";case"INTERRUPT"->"LS";default->"XX";};
        String q=subject+condition;String[] d=defaults.get(q);return new Mapping(q,d[0],d[1]);
    }
    private static String subject(BaselineNavaidCatalog.Navaid n,List<BaselineNavaidCatalog.Equipment> affected){
        Set<String> types=new HashSet<>(affected.stream().map(BaselineNavaidCatalog.Equipment::type).toList());
        if(Set.of("ILS","ILS_DME").contains(n.type())){
            if(types.containsAll(Set.of("Localizer","Glidepath")))return "QIC";
            if(types.equals(Set.of("DME")))return "QID";if(types.equals(Set.of("Glidepath")))return "QIG";if(types.equals(Set.of("Localizer")))return "QIL";
            if(types.equals(Set.of("MarkerBeacon"))){String p=n.components().stream().filter(c->types.contains(c.equipmentType())).map(BaselineNavaidCatalog.Component::markerPosition).findFirst().orElse("");return switch(p){case"INNER"->"QII";case"MIDDLE"->"QIM";case"OUTER"->"QIO";default->"QNF";};}
            if(types.equals(Set.of("NDB"))){String p=n.components().stream().filter(c->"NDB".equals(c.equipmentType())).map(BaselineNavaidCatalog.Component::markerPosition).findFirst().orElse("");return "MIDDLE".equals(p)?"QIY":"QIX";}
        }
        return switch(n.type()){case"MLS","MLS_DME"->"QIW";case"NDB","NDB_MKR"->{var e=affected.stream().filter(x->"NDB".equals(x.type())).findFirst().orElse(affected.get(0));yield"L".equals(e.equipmentClass())?"QNL":"QNB";}case"LOC","LOC_DME"->"QIN";case"DME"->"QND";case"MKR"->"QNF";case"VOR_DME"->"QNM";case"TACAN"->"QNN";case"VORTAC"->"QNT";case"VOR"->"QNV";case"DF"->"QNX";default->"QXX";};
    }
    public record Mapping(String qCode,String traffic,String purpose){}
}
