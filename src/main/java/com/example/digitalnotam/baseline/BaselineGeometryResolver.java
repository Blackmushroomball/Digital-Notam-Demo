package com.example.digitalnotam.baseline;

import org.w3c.dom.*;
import java.util.*;

/**
 * Resolves the horizontal footprint of baseline Airspace features.
 * Coordinates are returned as WGS84 latitude/longitude boundary samples.
 */
final class BaselineGeometryResolver {
    private static final String XLINK="http://www.w3.org/1999/xlink";
    private static final double EARTH_NM=3440.065;
    private final Map<String,Element> airspaces;
    private final Map<String,Element> geoBorders;
    private final Map<String,List<BaselineAirspaceCatalog.Point>> cache=new HashMap<>();

    BaselineGeometryResolver(Map<String,Element> airspaces,Map<String,Element> geoBorders){
        this.airspaces=airspaces;this.geoBorders=geoBorders;
    }

    List<BaselineAirspaceCatalog.Point> resolve(String uuid){
        return resolve(uuid,new LinkedHashSet<>());
    }

    private List<BaselineAirspaceCatalog.Point> resolve(String uuid,LinkedHashSet<String> path){
        List<BaselineAirspaceCatalog.Point> known=cache.get(uuid);if(known!=null)return known;
        Element slice=airspaces.get(uuid);if(slice==null)throw new IllegalArgumentException("Referenced Airspace baseline not found: "+uuid);
        if(!path.add(uuid))throw new IllegalArgumentException("Circular Airspace geometry dependency: "+String.join(" -> ",path)+" -> "+uuid);
        List<BaselineAirspaceCatalog.Point> result=new ArrayList<>();
        collectGeometry(slice,result);
        for(Element link:all(slice,"theAirspace")){
            String target=uuid(link.getAttributeNS(XLINK,"href"));
            if(!target.isBlank()&&!target.equals(uuid))result.addAll(resolve(target,path));
        }
        collectGeoBorderReferences(slice,result);
        path.remove(uuid);
        List<BaselineAirspaceCatalog.Point> value=deduplicate(result);cache.put(uuid,value);return value;
    }

    private void collectGeometry(Element slice,List<BaselineAirspaceCatalog.Point> out){
        // Explicit linear/geodesic coordinates.
        for(Element list:all(slice,"posList")){
            if(hasAncestor(list,"CircleByCenterPoint")||hasAncestor(list,"ArcByCenterPoint"))continue;
            addCoordinateText(list.getTextContent(),out);
        }
        for(Element pos:all(slice,"pos")){
            if(hasAncestor(pos,"CircleByCenterPoint")||hasAncestor(pos,"ArcByCenterPoint")||hasAncestor(pos,"centreline"))continue;
            addCoordinateText(pos.getTextContent(),out);
        }
        // Full circles and arcs are sampled geodesically, including their extrema.
        for(Element circle:all(slice,"CircleByCenterPoint"))sampleCircle(circle,out);
        for(Element arc:all(slice,"ArcByCenterPoint"))sampleArc(arc,out);
        // A centreline represents a corridor; expand it by half of aixm:width.
        for(Element centreline:all(slice,"centreline")){
            Element volume=ancestor(centreline,"AirspaceVolume");if(volume==null)continue;
            Element width=direct(volume,"width");if(width==null||width.getTextContent().isBlank())continue;
            double radiusNm=distanceNm(width.getTextContent(),width.getAttribute("uom"))/2.0;
            List<BaselineAirspaceCatalog.Point> line=new ArrayList<>();
            for(Element list:all(centreline,"posList"))addCoordinateText(list.getTextContent(),line);
            for(Element pos:all(centreline,"pos"))addCoordinateText(pos.getTextContent(),line);
            for(var p:line)for(int bearing=0;bearing<360;bearing+=15)out.add(destination(p,bearing,radiusNm));
        }
    }

    private void collectGeoBorderReferences(Element slice,List<BaselineAirspaceCatalog.Point> out){
        NodeList nodes=slice.getElementsByTagNameNS("*","*");
        for(int i=0;i<nodes.getLength();i++){
            Element e=(Element)nodes.item(i);String href=e.getAttributeNS(XLINK,"href");if(href.isBlank())continue;
            Element border=geoBorders.get(uuid(href));if(border!=null)collectGeometry(border,out);
        }
    }

    private void sampleCircle(Element circle,List<BaselineAirspaceCatalog.Point> out){
        BaselineAirspaceCatalog.Point centre=centre(circle);
        Element radius=first(circle,"radius");if(radius==null)throw new IllegalArgumentException("CircleByCenterPoint has no radius");
        double nm=distanceNm(radius.getTextContent(),radius.getAttribute("uom"));
        for(int bearing=0;bearing<360;bearing+=5)out.add(destination(centre,bearing,nm));
    }

    private void sampleArc(Element arc,List<BaselineAirspaceCatalog.Point> out){
        BaselineAirspaceCatalog.Point centre=centre(arc);
        Element radius=first(arc,"radius"),start=first(arc,"startAngle"),end=first(arc,"endAngle");
        if(radius==null||start==null||end==null)throw new IllegalArgumentException("ArcByCenterPoint is missing radius/startAngle/endAngle");
        requireDegrees(start);requireDegrees(end);
        double nm=distanceNm(radius.getTextContent(),radius.getAttribute("uom"));
        double from=Double.parseDouble(start.getTextContent()),to=Double.parseDouble(end.getTextContent());
        while(to<from)to+=360;
        int steps=Math.max(1,(int)Math.ceil((to-from)/2.0));
        for(int i=0;i<=steps;i++)out.add(destination(centre,from+(to-from)*i/steps,nm));
    }

    private static BaselineAirspaceCatalog.Point centre(Element shape){
        Element pos=first(shape,"pos");if(pos==null)pos=first(shape,"posList");
        if(pos==null)throw new IllegalArgumentException(shape.getLocalName()+" has no centre coordinate");
        String[] values=pos.getTextContent().trim().split("\\s+");
        if(values.length<2)throw new IllegalArgumentException(shape.getLocalName()+" has an invalid centre coordinate");
        return point(values[0],values[1]);
    }

    private static void addCoordinateText(String text,List<BaselineAirspaceCatalog.Point> out){
        String[] values=text.trim().split("\\s+");if(values.length%2!=0)throw new IllegalArgumentException("Odd number of ordinates in GML coordinate list");
        for(int i=0;i<values.length;i+=2)out.add(point(values[i],values[i+1]));
    }

    private static BaselineAirspaceCatalog.Point point(String latitude,String longitude){
        double lat=Double.parseDouble(latitude),lon=Double.parseDouble(longitude);
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat<-90||lat>90||lon<-180||lon>180)throw new IllegalArgumentException("Invalid WGS84 coordinate: "+latitude+" "+longitude);
        return new BaselineAirspaceCatalog.Point(lat,lon);
    }

    private static BaselineAirspaceCatalog.Point destination(BaselineAirspaceCatalog.Point start,double bearingDegrees,double distanceNm){
        double angular=distanceNm/EARTH_NM,bearing=Math.toRadians(bearingDegrees);
        double lat1=Math.toRadians(start.latitude()),lon1=Math.toRadians(start.longitude());
        double lat2=Math.asin(Math.sin(lat1)*Math.cos(angular)+Math.cos(lat1)*Math.sin(angular)*Math.cos(bearing));
        double lon2=lon1+Math.atan2(Math.sin(bearing)*Math.sin(angular)*Math.cos(lat1),Math.cos(angular)-Math.sin(lat1)*Math.sin(lat2));
        return new BaselineAirspaceCatalog.Point(Math.toDegrees(lat2),normalizeLongitude(Math.toDegrees(lon2)));
    }

    private static double distanceNm(String value,String uom){
        double amount=Double.parseDouble(value.trim());if(!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("Invalid geometry distance: "+value);
        return switch(uom){case"[nmi_i]","NM"->amount;case"km","KM"->amount/1.852;case"m","M"->amount/1852.0;case"FT","ft"->amount/6076.11549;default->throw new IllegalArgumentException("Unsupported geometry distance UOM: "+uom);};
    }

    private static void requireDegrees(Element angle){String uom=angle.getAttribute("uom");if(!uom.isBlank()&&!Set.of("deg","DEG").contains(uom))throw new IllegalArgumentException("Unsupported arc angle UOM: "+uom);}
    private static double normalizeLongitude(double value){double x=((value+180)%360+360)%360-180;return x==-180?180:x;}
    private static String uuid(String href){int p=href.lastIndexOf(':');return p<0?href:href.substring(p+1);}
    private static boolean hasAncestor(Node node,String local){return ancestor(node,local)!=null;}
    private static Element ancestor(Node node,String local){for(Node p=node.getParentNode();p instanceof Element e;p=p.getParentNode())if(local.equals(e.getLocalName()))return e;return null;}
    private static Element direct(Element p,String local){for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))return e;return null;}
    private static Element first(Element p,String local){NodeList n=p.getElementsByTagNameNS("*",local);return n.getLength()==0?null:(Element)n.item(0);}
    private static List<Element> all(Element p,String local){NodeList n=p.getElementsByTagNameNS("*",local);List<Element> r=new ArrayList<>();for(int i=0;i<n.getLength();i++)r.add((Element)n.item(i));return r;}
    private static List<BaselineAirspaceCatalog.Point> deduplicate(List<BaselineAirspaceCatalog.Point> values){
        Map<String,BaselineAirspaceCatalog.Point> unique=new LinkedHashMap<>();for(var p:values)unique.put(String.format(Locale.ROOT,"%.9f,%.9f",p.latitude(),p.longitude()),p);return List.copyOf(unique.values());
    }
}
