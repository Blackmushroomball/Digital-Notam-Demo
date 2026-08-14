package com.example.digitalnotam.scenario.atsanew;

import com.example.aixm.geometry.api.DefaultAixmGeometryService;
import com.example.aixm.geometry.json.GeometryJsonParser;
import com.example.aixm.geometry.model.GeometryModel.*;
import com.example.aixm.geometry.validation.GeometryValidator;
import com.example.digitalnotam.baseline.BaselineAirspaceCatalog;
import com.example.digitalnotam.baseline.BaselineAirportHeliportCatalog;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.operation.union.UnaryUnionOp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Spatial interpretation shared by ATSA.NEW validation, Item Q production and
 * automatic FIR/airport association.
 */
public final class AtsaNewGeometrySupport {
    private static final GeometryFactory JTS = new GeometryFactory();
    private final GeometryJsonParser parser = new GeometryJsonParser();
    private final GeometryValidator validator = new GeometryValidator();
    private final DefaultAixmGeometryService encoder = new DefaultAixmGeometryService();

    public Analysis analyse(String json) {
        var parsed = parser.parse(json);
        if (!parsed.issues().isEmpty())
            throw new IllegalArgumentException(parsed.issues().getFirst().path()+": "+parsed.issues().getFirst().message());
        Geometry geometry = parsed.geometry();
        var issues = validator.validate(geometry);
        if (!issues.isEmpty())
            throw new IllegalArgumentException(issues.getFirst().path()+": "+issues.getFirst().message());
        if (!(geometry instanceof Polygon || geometry instanceof Circle || geometry instanceof CircleSector || geometry instanceof Corridor))
            throw new IllegalArgumentException("ATSA.NEW geometry must be POLYGON, CIRCLE, CIRCLE_SECTOR or CORRIDOR");
        var encoded = encoder.encode(geometry);
        if (!encoded.valid())
            throw new IllegalArgumentException(encoded.issues().getFirst().message());
        List<Position> boundary = boundary(geometry);
        if (boundary.isEmpty()) throw new IllegalArgumentException("Unable to calculate ATSA.NEW geometry boundary");
        org.locationtech.jts.geom.Geometry footprint = switch (geometry) {
            case Polygon ignored -> polygon(boundary);
            case Circle ignored -> polygon(boundary);
            case CircleSector ignored -> polygon(boundary);
            case Corridor corridor -> corridorFootprint(corridor);
            default -> throw new IllegalArgumentException("Unsupported ATSA.NEW geometry");
        };
        // A corridor footprint is assembled from overlapping geodesic discs;
        // use its actual exterior for Q-radius and airport-distance calculations.
        if (geometry instanceof Corridor) boundary = positions(footprint.getBoundary().getCoordinates());
        double latitude = boundary.stream().mapToDouble(p -> p.y().doubleValue()).average().orElseThrow();
        double longitude = circularLongitudeMean(boundary);
        double radius = boundary.stream().mapToDouble(p -> distanceNm(latitude,longitude,p.y().doubleValue(),p.x().doubleValue())).max().orElse(0);
        return new Analysis(geometry,encoded.aixmXml(),List.copyOf(boundary),footprint,
                latitude,longitude,(int)Math.ceil(radius));
    }

    public Associations associate(Analysis analysis,double thresholdNm,
                                  Instant start,Instant end)throws Exception {
        BaselineAirspaceCatalog airspaces = new BaselineAirspaceCatalog();
        List<Fir> firs = airspaces.all().stream()
                .filter(a -> a.type().startsWith("FIR") && !a.points().isEmpty())
                .filter(a -> intersects(analysis.footprint(), a.points()))
                .map(a -> new Fir(a.uuid(),a.designator(),a.name()))
                .sorted(Comparator.comparing(Fir::designator)).toList();
        BaselineAirportHeliportCatalog airports = new BaselineAirportHeliportCatalog();
        List<Airport> nearby = new ArrayList<>();
        for (var summary : airports.list()) {
            double lat=Double.parseDouble(summary.latitude()),lon=Double.parseDouble(summary.longitude());
            org.locationtech.jts.geom.Point point=JTS.createPoint(new Coordinate(lon,lat));
            Coordinate nearest=DistanceOp.nearestPoints(analysis.footprint(),point)[0];
            double distance=analysis.footprint().covers(point)?0:distanceNm(lat,lon,nearest.y,nearest.x);
            if(distance<=thresholdNm){
                var baseline=airports.find(summary.designator(),start,end);
                nearby.add(new Airport(baseline.uuid(),summary.designator(),summary.name(),distance));
            }
        }
        nearby.sort(Comparator.comparingDouble(Airport::distanceNm));
        return new Associations(firs,List.copyOf(nearby));
    }

    private static List<Position> boundary(Geometry geometry) {
        return switch (geometry) {
            case Polygon p -> segments(p.segments());
            case Circle c -> circle(c.center(),c.radius(),0,360);
            case CircleSector s -> sector(s);
            case Corridor c -> positions(corridorFootprint(c).getBoundary().getCoordinates());
            default -> List.of();
        };
    }

    private static List<Position> segments(List<Segment> segments) {
        List<Position> result=new ArrayList<>();
        for(Segment segment:segments){
            switch(segment){
                case Geodesic g -> result.addAll(g.positions());
                case Parallel p -> {result.add(p.start());result.add(p.end());}
                case ArcByEdge a -> result.addAll(arcByEdge(a));
                case ArcByCenter a -> result.addAll(circle(a.center(),a.radius(),a.startAngle().doubleValue(),a.endAngle().doubleValue()));
            }
        }
        return deduplicate(result);
    }

    private static org.locationtech.jts.geom.Geometry corridorFootprint(Corridor corridor) {
        List<Position> centre=densify(segments(corridor.centreline().segments()),Math.max(250,
                com.example.aixm.geometry.json.GeometryJsonParser.metres(corridor.width())/8d));
        double halfMetres=com.example.aixm.geometry.json.GeometryJsonParser.metres(corridor.width())/2d;
        List<org.locationtech.jts.geom.Geometry> discs=new ArrayList<>();
        for(Position p:centre)discs.add(polygon(circle(p,new Measure(BigDecimal.valueOf(halfMetres),"M"),0,360)));
        return UnaryUnionOp.union(discs);
    }

    private static List<Position> circle(Position center,Measure radius,double start,double end) {
        while(end<=start)end+=360;
        int count=Math.max(12,(int)Math.ceil((end-start)/5d));List<Position> result=new ArrayList<>();
        double metres=com.example.aixm.geometry.json.GeometryJsonParser.metres(radius);
        for(int i=0;i<=count;i++)result.add(destination(center,start+(end-start)*i/count,metres));
        return result;
    }

    private static List<Position> sector(CircleSector sector) {
        double start=sector.startAngle().doubleValue(),end=sector.endAngle().doubleValue();
        while(end<=start)end+=360;
        List<Position> result=new ArrayList<>(circle(sector.center(),sector.outerRadius(),start,end));
        if(sector.innerRadius()==null||com.example.aixm.geometry.json.GeometryJsonParser.metres(sector.innerRadius())==0){
            result.add(sector.center());
        }else{
            List<Position> inner=circle(sector.center(),sector.innerRadius(),start,end);
            for(int i=inner.size()-1;i>=0;i--)result.add(inner.get(i));
        }
        result.add(result.getFirst());
        return result;
    }

    private static Position destination(Position center,double bearing,double metres){
        var d=net.sf.geographiclib.Geodesic.WGS84.Direct(center.y().doubleValue(),center.x().doubleValue(),bearing,metres);
        return new Position(BigDecimal.valueOf(d.lon2),BigDecimal.valueOf(d.lat2));
    }
    /**
     * Samples the unique circular arc through its three edge control points.
     * A local tangent plane is used only to solve the circumcentre; generated
     * coordinates remain WGS-84 and the arc direction is chosen to pass through
     * the middle control point, as required by ACG_Arc_by_Edge.
     */
    private static List<Position> arcByEdge(ArcByEdge arc){
        double lat0=(arc.start().y().doubleValue()+arc.through().y().doubleValue()+arc.end().y().doubleValue())/3d;
        double lon0=circularLongitudeMean(List.of(arc.start(),arc.through(),arc.end()));
        double scaleX=111_320d*Math.cos(Math.toRadians(lat0)),scaleY=111_320d;
        double[] a=xy(arc.start(),lon0,lat0,scaleX,scaleY),b=xy(arc.through(),lon0,lat0,scaleX,scaleY),c=xy(arc.end(),lon0,lat0,scaleX,scaleY);
        double divisor=2*(a[0]*(b[1]-c[1])+b[0]*(c[1]-a[1])+c[0]*(a[1]-b[1]));
        if(Math.abs(divisor)<1e-6)return List.of(arc.start(),arc.through(),arc.end());
        double aa=a[0]*a[0]+a[1]*a[1],bb=b[0]*b[0]+b[1]*b[1],cc=c[0]*c[0]+c[1]*c[1];
        double cx=(aa*(b[1]-c[1])+bb*(c[1]-a[1])+cc*(a[1]-b[1]))/divisor;
        double cy=(aa*(c[0]-b[0])+bb*(a[0]-c[0])+cc*(b[0]-a[0]))/divisor;
        double start=Math.atan2(a[1]-cy,a[0]-cx),through=Math.atan2(b[1]-cy,b[0]-cx),end=Math.atan2(c[1]-cy,c[0]-cx);
        double ccw=positive(end-start),throughCcw=positive(through-start);
        double sweep=throughCcw<=ccw+1e-9?ccw:ccw-2*Math.PI;
        int count=Math.max(8,(int)Math.ceil(Math.abs(sweep)/Math.toRadians(2)));double radius=Math.hypot(a[0]-cx,a[1]-cy);
        List<Position> result=new ArrayList<>();for(int i=0;i<=count;i++){double angle=start+sweep*i/count;result.add(new Position(BigDecimal.valueOf(lon0+(cx+radius*Math.cos(angle))/scaleX),BigDecimal.valueOf(lat0+(cy+radius*Math.sin(angle))/scaleY)));}return result;
    }
    private static double[] xy(Position p,double lon0,double lat0,double scaleX,double scaleY){return new double[]{(p.x().doubleValue()-lon0)*scaleX,(p.y().doubleValue()-lat0)*scaleY};}
    private static double positive(double angle){double full=2*Math.PI;angle%=full;return angle<0?angle+full:angle;}
    private static double distanceNm(double lat1,double lon1,double lat2,double lon2){return net.sf.geographiclib.Geodesic.WGS84.Inverse(lat1,lon1,lat2,lon2).s12/1852d;}
    private static double circularLongitudeMean(List<Position> values){double x=0,y=0;for(Position p:values){double r=Math.toRadians(p.x().doubleValue());x+=Math.cos(r);y+=Math.sin(r);}return Math.toDegrees(Math.atan2(y,x));}
    private static org.locationtech.jts.geom.Polygon polygon(List<Position> values){List<Coordinate> c=new ArrayList<>();for(Position p:values)c.add(new Coordinate(p.x().doubleValue(),p.y().doubleValue()));if(!c.getFirst().equals2D(c.getLast()))c.add(new Coordinate(c.getFirst()));return JTS.createPolygon(c.toArray(Coordinate[]::new));}
    private static List<Position> densify(List<Position> values,double stepMetres){
        if(values.size()<2)return values;List<Position> out=new ArrayList<>();
        for(int i=0;i<values.size()-1;i++){
            Position a=values.get(i),b=values.get(i+1);var inverse=net.sf.geographiclib.Geodesic.WGS84.Inverse(a.y().doubleValue(),a.x().doubleValue(),b.y().doubleValue(),b.x().doubleValue());
            int parts=Math.max(1,(int)Math.ceil(inverse.s12/stepMetres));
            for(int j=0;j<parts;j++)out.add(destination(a,inverse.azi1,inverse.s12*j/parts));
        }
        out.add(values.getLast());return deduplicate(out);
    }
    private static List<Position> positions(Coordinate[] values){List<Position> out=new ArrayList<>();for(Coordinate c:values)out.add(new Position(BigDecimal.valueOf(c.x),BigDecimal.valueOf(c.y)));return deduplicate(out);}
    private static boolean intersects(org.locationtech.jts.geom.Geometry shape,List<BaselineAirspaceCatalog.Point> points){if(points.size()<3)return false;List<Coordinate> c=new ArrayList<>();for(var p:points)c.add(new Coordinate(p.longitude(),p.latitude()));if(!c.getFirst().equals2D(c.getLast()))c.add(new Coordinate(c.getFirst()));try{return shape.intersects(JTS.createPolygon(c.toArray(Coordinate[]::new)));}catch(Exception e){return false;}}
    private static List<Position> deduplicate(List<Position> values){List<Position> out=new ArrayList<>();for(Position p:values)if(out.isEmpty()||!same(out.getLast(),p))out.add(p);return out;}
    private static boolean same(Position a,Position b){return Math.abs(a.x().doubleValue()-b.x().doubleValue())<1e-9&&Math.abs(a.y().doubleValue()-b.y().doubleValue())<1e-9;}

    public record Analysis(Geometry geometry,String aixmXml,List<Position> boundary,
                           org.locationtech.jts.geom.Geometry footprint,
                           double latitude,double longitude,int radiusNm){}
    public record Fir(String uuid,String designator,String name){}
    public record Airport(String uuid,String designator,String name,double distanceNm){}
    public record Associations(List<Fir> firs,List<Airport> airports){}
}
