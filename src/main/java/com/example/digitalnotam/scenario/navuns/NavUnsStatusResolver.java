package com.example.digitalnotam.scenario.navuns;

import com.example.digitalnotam.baseline.BaselineNavaidCatalog;
import com.example.digitalnotam.domain.NavUnsData;
import java.util.*;

public final class NavUnsStatusResolver {
    private static final Set<String> INPUT=Set.of("UNSERVICEABLE","ONTEST","INTERRUPT","FALSE_INDICATION","IN_CONSTRUCTION");
    private static final Map<String,Map<String,String>> TYPE_CHANGE=Map.of(
            "VOR_DME",Map.of("VOR","DME","DME","VOR"),
            "NDB_MKR",Map.of("MarkerBeacon","NDB","NDB","MKR"),
            "VORTAC",Map.of("VOR","TACAN","TACAN","VOR"),
            "NDB_DME",Map.of("DME","NDB","NDB","DME"),
            "ILS",Map.of("Glidepath","LOC"),
            "ILS_DME",Map.of("Glidepath","LOC_DME"));

    public Resolution resolve(BaselineNavaidCatalog.Navaid n,List<BaselineNavaidCatalog.Equipment> affected,NavUnsData input){
        validate(n,affected,input);String equipmentStatus=input.operationalStatus();
        boolean allPrimary="ALL_PRIMARY".equals(input.impactMode());
        String navaidStatus=allPrimary?equipmentStatus:switch(base(equipmentStatus)){
            case"FALSE_INDICATION"->"FALSE_INDICATION";case"ONTEST"->"ONTEST";case"UNSERVICEABLE"->"PARTIAL";
            case"INTERRUPT"->"INTERRUPT";case"PARTIAL","IN_CONSTRUCTION"->"PARTIAL";default->equipmentStatus;
        };
        String temporaryType=n.type();
        if(!allPrimary&&Set.of("UNSERVICEABLE","ONTEST","FALSE_INDICATION","IN_CONSTRUCTION").contains(base(equipmentStatus))){
            temporaryType=TYPE_CHANGE.getOrDefault(n.type(),Map.of()).getOrDefault(affected.get(0).type(),n.type());
        }
        if("SIGNAL".equals(input.impactMode())){navaidStatus="PARTIAL";temporaryType=n.type();}
        return new Resolution(equipmentStatus,navaidStatus,temporaryType,List.copyOf(affected));
    }
    private static void validate(BaselineNavaidCatalog.Navaid n,List<BaselineNavaidCatalog.Equipment> affected,NavUnsData x){
        String status=x.operationalStatus(),base=base(status);
        if(!INPUT.contains(base)&&!"PARTIAL".equals(base)&&!status.startsWith("OTHER:"))throw new IllegalArgumentException("NAV.UNS operationalStatus is not allowed by ER-05: "+status);
        if(status.startsWith("OTHER:")&&status.substring(6).isBlank())throw new IllegalArgumentException("OTHER requires a concrete value");
        if(Set.of("OPERATIONAL","CONDITIONAL","FALSE_POSSIBLE","DISPLACED").contains(base))throw new IllegalArgumentException(base+" cannot be used as NAV.UNS event input");
        if("PARTIAL".equals(base)&&!("SIGNAL".equals(x.impactMode())&&Set.of("TACAN","VORTAC").contains(n.type())))throw new IllegalArgumentException("PARTIAL input is only allowed for a TACAN/VORTAC signal event");
        if("SIGNAL".equals(x.impactMode())){
            if(!Set.of("TACAN","VORTAC").contains(n.type())||affected.size()!=1||!"TACAN".equals(affected.get(0).type()))throw new IllegalArgumentException("signalType is only allowed for the TACAN component of TACAN/VORTAC");
            if(!Set.of("AZIMUTH","DISTANCE").contains(x.signalType()))throw new IllegalArgumentException("signalType must be AZIMUTH or DISTANCE");
        }else if(!x.signalType().isBlank())throw new IllegalArgumentException("signalType requires SIGNAL impact mode");
        if("UNSERVICEABLE".equals(base)&&x.signalStillEmitted())throw new IllegalArgumentException("ER-06 requires ONTEST when the equipment still emits a signal");
        if("ONTEST".equals(base)&&!x.signalStillEmitted())throw new IllegalArgumentException("ONTEST requires confirmation that the equipment still emits a test signal");
    }
    static String base(String value){return value!=null&&value.startsWith("OTHER:")?"OTHER":value;}
    public record Resolution(String equipmentStatus,String navaidStatus,String temporaryNavaidType,List<BaselineNavaidCatalog.Equipment> affectedEquipment){}
}
