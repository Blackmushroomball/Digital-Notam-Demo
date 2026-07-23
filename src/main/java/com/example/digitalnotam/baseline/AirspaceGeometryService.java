package com.example.digitalnotam.baseline;

import java.util.*;

public final class AirspaceGeometryService {
    public Result calculate(List<BaselineAirspaceCatalog.Airspace> airspaces){
        List<BaselineAirspaceCatalog.Point> points=airspaces.stream().flatMap(a->a.points().stream()).toList();
        if(points.isEmpty())throw new IllegalArgumentException("Selected Airspace has no resolvable horizontal geometry");
        double lat=points.stream().mapToDouble(BaselineAirspaceCatalog.Point::latitude).average().orElseThrow();
        double lon=points.stream().mapToDouble(BaselineAirspaceCatalog.Point::longitude).average().orElseThrow();
        // Re-centre on the furthest pair midpoint. This produces a stable enclosing circle;
        // the final radius is verified against every baseline geometry vertex.
        double best=-1;BaselineAirspaceCatalog.Point a=points.get(0),b=a;
        for(var x:points)for(var y:points){double d=nm(x.latitude(),x.longitude(),y.latitude(),y.longitude());if(d>best){best=d;a=x;b=y;}}
        lat=(a.latitude()+b.latitude())/2;lon=meanLongitude(a.longitude(),b.longitude());
        double radius=0;for(var p:points)radius=Math.max(radius,nm(lat,lon,p.latitude(),p.longitude()));
        int rounded=Math.min(999,(int)Math.ceil(radius+0.71));
        int min=999,max=0;for(var as:airspaces)for(var v:as.volumes()){min=Math.min(min,fl(v.lower(),v.lowerUom(),false));max=Math.max(max,fl(v.upper(),v.upperUom(),true));}
        if(min==999&&max==0)throw new IllegalArgumentException("Selected Airspace has no resolvable vertical limits");
        return new Result(lat,lon,rounded,Math.max(0,min),Math.min(999,max));
    }
    private static int fl(String value,String uom,boolean upper){String code=value.trim().toUpperCase(Locale.ROOT);if(Set.of("GND","SFC").contains(code))return 0;if(Set.of("UNL","UNLIMITED").contains(code))return 999;try{double x=Double.parseDouble(value);return switch(uom){case"FL"->(int)(upper?Math.ceil(x):Math.floor(x));case"FT"->(int)(upper?Math.ceil(x/100):Math.floor(x/100));case"M"->(int)(upper?Math.ceil(x*3.28084/100):Math.floor(x*3.28084/100));default->throw new IllegalArgumentException("Unsupported vertical UOM: "+uom);};}catch(NumberFormatException e){throw new IllegalArgumentException("Unsupported vertical limit: "+value+" "+uom);}}
    private static double nm(double a,double o,double b,double p){double r=3440.065,dLat=Math.toRadians(b-a),dLon=Math.toRadians(p-o),x=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(b))*Math.sin(dLon/2)*Math.sin(dLon/2);return 2*r*Math.asin(Math.min(1,Math.sqrt(x)));}
    private static double meanLongitude(double a,double b){double x=Math.cos(Math.toRadians(a))+Math.cos(Math.toRadians(b)),y=Math.sin(Math.toRadians(a))+Math.sin(Math.toRadians(b));return Math.toDegrees(Math.atan2(y,x));}
    public record Result(double latitude,double longitude,int radiusNm,int minimumFl,int maximumFl){}
}
