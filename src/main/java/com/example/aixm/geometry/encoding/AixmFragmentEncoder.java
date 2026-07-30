package com.example.aixm.geometry.encoding;

import com.example.aixm.geometry.json.GeometryJsonParser;
import com.example.aixm.geometry.model.GeometryModel.*;
import net.sf.geographiclib.GeodesicData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Encodes validated models as standalone AIXM 5.1.1 geometry fragments. */
public final class AixmFragmentEncoder {
    public static final String AIXM = "http://www.aixm.aero/schema/5.1.1";
    public static final String GML = "http://www.opengis.net/gml/3.2";
    public static final String SRS = "urn:ogc:def:crs:EPSG::4326";

    public Document encode(Geometry geometry) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().newDocument();
        Element root = switch (geometry) {
            case Point p -> point(document, p);
            case Curve c -> curve(document, c);
            case Polygon p -> surface(document, p.gmlId(), p.segments(), p.elevation());
            case Circle c -> circle(document, c);
            case CircleSector s -> sector(document, s);
            case Corridor c -> corridor(document, c);
        };
        declareNamespaces(root);
        document.appendChild(root);
        return document;
    }

    public String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter output = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return output.toString().trim();
    }

    private Element point(Document d, Point point) {
        boolean elevated = present(point.elevation());
        Element root = aixm(d, elevated ? "ElevatedPoint" : "Point");
        geometryAttributes(root, point.gmlId());
        addPosition(root, point.position());
        addElevation(root, point.elevation());
        return root;
    }

    private Element curve(Document d, Curve curve) {
        boolean elevated = present(curve.elevation());
        Element root = aixm(d, elevated ? "ElevatedCurve" : "Curve");
        geometryAttributes(root, curve.gmlId());
        Element segments = child(root, GML, "gml:segments");
        for (Segment segment : curve.segments()) {
            segments.appendChild(segment(d, segment));
        }
        addElevation(root, curve.elevation());
        return root;
    }

    private Element surface(Document d, String id, List<Segment> boundary,
                            Elevation elevation) {
        Element root = aixm(d, present(elevation) ? "ElevatedSurface" : "Surface");
        geometryAttributes(root, id);
        Element patches = child(root, GML, "gml:patches");
        Element patch = child(patches, GML, "gml:PolygonPatch");
        Element exterior = child(patch, GML, "gml:exterior");
        Element ring = child(exterior, GML, "gml:Ring");
        for (Segment part : boundary) {
            Element member = child(ring, GML, "gml:curveMember");
            Element memberCurve = child(member, GML, "gml:Curve");
            geometryAttributes(memberCurve, generated("curve"));
            Element segments = child(memberCurve, GML, "gml:segments");
            segments.appendChild(segment(d, part));
        }
        addElevation(root, elevation);
        return root;
    }

    private Element circle(Document d, Circle circle) {
        Element root = aixm(d, present(circle.elevation())
                ? "ElevatedSurface" : "Surface");
        geometryAttributes(root, circle.gmlId());
        Element patches = child(root, GML, "gml:patches");
        Element patch = child(patches, GML, "gml:PolygonPatch");
        Element exterior = child(patch, GML, "gml:exterior");
        Element ring = child(exterior, GML, "gml:Ring");
        Element member = child(ring, GML, "gml:curveMember");
        Element curve = child(member, GML, "gml:Curve");
        geometryAttributes(curve, generated("curve"));
        Element segments = child(curve, GML, "gml:segments");
        Element fullCircle = child(segments, GML, "gml:CircleByCenterPoint");
        fullCircle.setAttribute("numArc", "1");
        addPosition(fullCircle, circle.center());
        addMeasure(fullCircle, GML, "gml:radius", circle.radius());
        addElevation(root, circle.elevation());
        return root;
    }

    private Element sector(Document d, CircleSector sector) {
        double start = trueAngle(sector.startAngle(), sector);
        double end = trueAngle(sector.endAngle(), sector);
        Position outerStart = destination(sector.center(), sector.outerRadius(), start);
        Position outerEnd = destination(sector.center(), sector.outerRadius(), end);
        List<Segment> boundary;
        if (sector.innerRadius() == null) {
            boundary = List.of(
                    new Geodesic(List.of(sector.center(), outerStart)),
                    new ArcByCenter(outerStart, outerEnd, sector.center(),
                            sector.outerRadius(), decimal(start), decimal(end)),
                    new Geodesic(List.of(outerEnd, sector.center())));
        } else {
            Position innerStart = destination(sector.center(), sector.innerRadius(), start);
            Position innerEnd = destination(sector.center(), sector.innerRadius(), end);
            // The inner arc is traversed in reverse to close an annular sector.
            boundary = List.of(
                    new Geodesic(List.of(innerStart, outerStart)),
                    new ArcByCenter(outerStart, outerEnd, sector.center(),
                            sector.outerRadius(), decimal(start), decimal(end)),
                    new Geodesic(List.of(outerEnd, innerEnd)),
                    new ArcByCenter(innerEnd, innerStart, sector.center(),
                            sector.innerRadius(), decimal(end), decimal(start)));
        }
        return surface(d, sector.gmlId(), boundary, sector.elevation());
    }

    private Element corridor(Document d, Corridor corridor) {
        Element root = aixm(d, "AirspaceVolume");
        root.setAttributeNS(GML, "gml:id", corridor.gmlId());
        // AIXM width means full corridor width; no half-width conversion occurs.
        addMeasure(root, AIXM, "aixm:width", corridor.width());
        Element centreline = child(root, AIXM, "aixm:centreline");
        centreline.appendChild(curve(d, corridor.centreline()));
        return root;
    }

    private Element segment(Document d, Segment value) {
        return switch (value) {
            case Geodesic g -> {
                Element segment = gml(d, "GeodesicString");
                Element list = child(segment, GML, "gml:posList");
                list.setTextContent(positionList(g.positions()));
                yield segment;
            }
            case Parallel p -> {
                Element segment = gml(d, "LineStringSegment");
                Element list = child(segment, GML, "gml:posList");
                list.setTextContent(positionList(List.of(p.start(), p.end())));
                yield segment;
            }
            case ArcByEdge a -> {
                Element segment = gml(d, "Arc");
                segment.setAttribute("numArc", "1");
                addPosition(segment, a.start());
                addPosition(segment, a.through());
                addPosition(segment, a.end());
                yield segment;
            }
            case ArcByCenter a -> {
                Element segment = gml(d, "ArcByCenterPoint");
                segment.setAttribute("numArc", "1");
                // GML encodes centre/radius/angles; derived endpoints are only
                // retained in the model for continuity validation.
                addPosition(segment, a.center());
                addMeasure(segment, GML, "gml:radius", a.radius());
                addAngle(segment, "startAngle", a.startAngle());
                addAngle(segment, "endAngle", a.endAngle());
                yield segment;
            }
        };
    }

    private void addElevation(Element root, Elevation value) {
        if (!present(value)) return;
        addOptionalMeasure(root, "elevation", value.elevation());
        addOptionalMeasure(root, "geoidUndulation", value.geoidUndulation());
        if (value.verticalDatum() != null) {
            child(root, AIXM, "aixm:verticalDatum")
                    .setTextContent(value.verticalDatum());
        }
        addOptionalMeasure(root, "verticalAccuracy", value.verticalAccuracy());
    }

    private void addOptionalMeasure(Element parent, String local, Measure value) {
        if (value != null) addMeasure(parent, AIXM, "aixm:" + local, value);
    }

    private void addMeasure(Element parent, String ns, String qualifiedName,
                            Measure measure) {
        Element element = child(parent, ns, qualifiedName);
        element.setAttribute("uom", measure.uom());
        element.setTextContent(number(measure.value()));
    }

    private void addAngle(Element parent, String local, BigDecimal value) {
        Element angle = child(parent, GML, "gml:" + local);
        angle.setAttribute("uom", "deg");
        angle.setTextContent(number(normalize(value)));
    }

    private void addPosition(Element parent, Position position) {
        child(parent, GML, "gml:pos").setTextContent(gmlPosition(position));
    }

    private static String positionList(List<Position> positions) {
        return positions.stream().map(AixmFragmentEncoder::gmlPosition)
                .reduce((a, b) -> a + " " + b).orElse("");
    }

    private static String gmlPosition(Position position) {
        // EPSG:4326 GML axis order is latitude (y), longitude (x).
        return number(position.y()) + " " + number(position.x());
    }

    private static double trueAngle(BigDecimal source, CircleSector sector) {
        double angle = source.doubleValue();
        if (sector.angleReference() == AngleReference.MAGNETIC_NORTH) {
            double variation = sector.magneticVariation().value().doubleValue();
            angle += sector.magneticVariation().direction() == Direction.EAST
                    ? variation : -variation;
        }
        return normalize(angle);
    }

    private static Position destination(Position center, Measure radius, double bearing) {
        GeodesicData d = net.sf.geographiclib.Geodesic.WGS84.Direct(
                center.y().doubleValue(), center.x().doubleValue(),
                bearing, GeometryJsonParser.metres(radius));
        return new Position(decimal(d.lon2), decimal(d.lat2));
    }

    private static BigDecimal normalize(BigDecimal value) {
        return decimal(normalize(value.doubleValue()));
    }

    private static double normalize(double value) {
        double result = value % 360d;
        return result < 0 ? result + 360d : result;
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }

    private static boolean present(Elevation e) {
        return e != null && e.present();
    }

    private static void geometryAttributes(Element element, String id) {
        element.setAttributeNS(GML, "gml:id", id);
        element.setAttribute("srsName", SRS);
        element.setAttribute("srsDimension", "2");
    }

    private static void declareNamespaces(Element root) {
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:aixm", AIXM);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:gml", GML);
    }

    private static Element child(Element parent, String ns, String qualifiedName) {
        Element result = parent.getOwnerDocument().createElementNS(ns, qualifiedName);
        parent.appendChild(result);
        return result;
    }

    private static Element aixm(Document d, String local) {
        return d.createElementNS(AIXM, "aixm:" + local);
    }

    private static Element gml(Document d, String local) {
        return d.createElementNS(GML, "gml:" + local);
    }

    private static String generated(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
