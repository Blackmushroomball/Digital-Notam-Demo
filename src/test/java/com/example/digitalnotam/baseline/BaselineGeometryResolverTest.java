package com.example.digitalnotam.baseline;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

public final class BaselineGeometryResolverTest {
    public static void main(String[] args)throws Exception{
        donlonBaseline();
        referencesAndCycles();
        System.out.println("Airspace geometry resolver tests passed");
    }

    private static void donlonBaseline(){
        BaselineAirspaceCatalog catalog=new BaselineAirspaceCatalog();
        for(var group:catalog.groups())for(String id:group.members())check(!catalog.get(id).points().isEmpty(),catalog.get(id).name()+" geometry must resolve");
        var service=new AirspaceGeometryService();
        var ctr=service.calculate(List.of(named(catalog,"DONLON CTR")));
        check(ctr.radiusNm()==20,"DONLON CTR 35 km circle plus rounding allowance must produce 20 NM");
        check(ctr.minimumFl()==0&&ctr.maximumFl()==30,"DONLON CTR GND-3000 FT must produce 000/030");
        var combined=service.calculate(List.of(named(catalog,"MAGNETO TMA")));
        check(combined.minimumFl()==210&&combined.maximumFl()==460,"dependency vertical limits must aggregate");
        check(named(catalog,"BRAVO").points().size()>100,"ArcByCenterPoint must be densified");
    }

    private static void referencesAndCycles()throws Exception{
        Element border=parse("<aixm:GeoBorderTimeSlice xmlns:aixm='http://www.aixm.aero/schema/5.1.1' xmlns:gml='http://www.opengis.net/gml/3.2'><aixm:border><aixm:Curve><gml:segments><gml:GeodesicString><gml:posList>10 20 11 21</gml:posList></gml:GeodesicString></gml:segments></aixm:Curve></aixm:border></aixm:GeoBorderTimeSlice>");
        Element child=parse("<aixm:AirspaceTimeSlice xmlns:aixm='http://www.aixm.aero/schema/5.1.1' xmlns:gml='http://www.opengis.net/gml/3.2'><aixm:geometryComponent><aixm:horizontalProjection><gml:CircleByCenterPoint><gml:pos>10 20</gml:pos><gml:radius uom='NM'>5</gml:radius></gml:CircleByCenterPoint></aixm:horizontalProjection></aixm:geometryComponent></aixm:AirspaceTimeSlice>");
        Element parent=parse("<aixm:AirspaceTimeSlice xmlns:aixm='http://www.aixm.aero/schema/5.1.1' xmlns:gml='http://www.opengis.net/gml/3.2' xmlns:xlink='http://www.w3.org/1999/xlink'><aixm:geometryComponent><aixm:theAirspace xlink:href='urn:uuid:child'/><gml:curveMember xlink:href='urn:uuid:border'/></aixm:geometryComponent></aixm:AirspaceTimeSlice>");
        var resolver=new BaselineGeometryResolver(Map.of("parent",parent,"child",child),Map.of("border",border));
        check(resolver.resolve("parent").size()>=74,"Airspace and GeoBorder references must both resolve");
        Element a=parse("<aixm:AirspaceTimeSlice xmlns:aixm='http://www.aixm.aero/schema/5.1.1' xmlns:xlink='http://www.w3.org/1999/xlink'><aixm:theAirspace xlink:href='urn:uuid:b'/></aixm:AirspaceTimeSlice>");
        Element b=parse("<aixm:AirspaceTimeSlice xmlns:aixm='http://www.aixm.aero/schema/5.1.1' xmlns:xlink='http://www.w3.org/1999/xlink'><aixm:theAirspace xlink:href='urn:uuid:a'/></aixm:AirspaceTimeSlice>");
        try{new BaselineGeometryResolver(Map.of("a",a,"b",b),Map.of()).resolve("a");throw new AssertionError("cycle must fail");}
        catch(IllegalArgumentException expected){check(expected.getMessage().contains("Circular"),"cycle error must be explicit");}
    }

    private static BaselineAirspaceCatalog.Airspace named(BaselineAirspaceCatalog catalog,String name){
        return catalog.groups().stream().flatMap(g->g.members().stream()).distinct().map(catalog::get).filter(a->name.equals(a.name())).findFirst().orElseThrow();
    }
    private static Element parse(String xml)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
