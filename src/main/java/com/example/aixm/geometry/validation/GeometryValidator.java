package com.example.aixm.geometry.validation;

import com.example.aixm.geometry.api.GeometryIssue;
import com.example.aixm.geometry.json.GeometryJsonParser;
import com.example.aixm.geometry.model.GeometryModel.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.operation.valid.IsValidOp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Applies the V1 WGS-84 and geometry rules before any XML is created. */
public final class GeometryValidator {
    private static final Pattern NCNAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9._-]*");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final double CONTINUITY_TOLERANCE_DEGREES = 1e-8;
    private static final GeometryFactory JTS = new GeometryFactory();

    public List<GeometryIssue> validate(Geometry geometry) {
        List<GeometryIssue> issues = new ArrayList<>();
        if (geometry == null) {
            issues.add(error("GEOM-001", "/geometry", "几何对象不能为空"));
            return issues;
        }
        validateCrs(geometry.crs(), "/geometry/crs", issues);
        Set<String> ids = new HashSet<>();
        validateId(geometry.gmlId(), "/geometry/gmlId", ids, issues);

        switch (geometry) {
            case Point p -> {
                position(p.position(), "/geometry/position", issues);
                elevation(p.elevation(), "/geometry/elevation", issues);
            }
            case Curve c -> {
                segments(c.segments(), "/geometry/segments", false, issues);
                elevation(c.elevation(), "/geometry/elevation", issues);
            }
            case Polygon p -> polygon(p, "/geometry", ids, issues);
            case Circle c -> {
                position(c.center(), "/geometry/center", issues);
                positive(c.radius(), "/geometry/radius", issues);
                elevation(c.elevation(), "/geometry/elevation", issues);
            }
            case CircleSector s -> sector(s, "/geometry", issues);
            case Corridor c -> corridor(c, ids, issues);
        }
        return List.copyOf(issues);
    }

    private void curve(Curve curve, String path, Set<String> ids,
                       List<GeometryIssue> issues) {
        validateCrs(curve.crs(), path + "/crs", issues);
        validateId(curve.gmlId(), path + "/gmlId", ids, issues);
        segments(curve.segments(), path + "/segments", false, issues);
        elevation(curve.elevation(), path + "/elevation", issues);
    }

    private void polygon(Polygon polygon, String path, Set<String> ids,
                         List<GeometryIssue> issues) {
        List<Segment> segments = polygon.segments();
        segments(segments, path + "/segments", true, issues);
        elevation(polygon.elevation(), path + "/elevation", issues);
        if (segments == null || segments.isEmpty() || hasNullPositions(segments)) return;

        List<Position> controls = boundaryControls(segments);
        if (controls.size() < 4) {
            issues.add(error("GEOM-POLYGON-002", path + "/segments",
                    "多边形至少需要三个不同顶点并闭合"));
            return;
        }
        Coordinate[] coordinates = controls.stream()
                .map(p -> new Coordinate(p.x().doubleValue(), p.y().doubleValue()))
                .toArray(Coordinate[]::new);
        try {
            LinearRing ring = JTS.createLinearRing(coordinates);
            var polygonGeometry = JTS.createPolygon(ring);
            var validation = new IsValidOp(polygonGeometry);
            if (!validation.isValid()) {
                issues.add(error("GEOM-POLYGON-003", path + "/segments",
                        "多边形拓扑无效: " + validation.getValidationError().getMessage()));
            }
        } catch (IllegalArgumentException ex) {
            issues.add(error("GEOM-POLYGON-003", path + "/segments",
                    "多边形拓扑无效: " + ex.getMessage()));
        }
    }

    private void sector(CircleSector s, String path, List<GeometryIssue> issues) {
        position(s.center(), path + "/center", issues);
        positive(s.outerRadius(), path + "/outerRadius", issues);
        if (s.innerRadius() != null) {
            positive(s.innerRadius(), path + "/innerRadius", issues);
            if (validMeasure(s.innerRadius()) && validMeasure(s.outerRadius())
                    && GeometryJsonParser.metres(s.innerRadius())
                    >= GeometryJsonParser.metres(s.outerRadius())) {
                issues.add(error("GEOM-SECTOR-001", path + "/innerRadius",
                        "innerRadius必须小于outerRadius"));
            }
        }
        angle(s.startAngle(), path + "/startAngle", issues);
        angle(s.endAngle(), path + "/endAngle", issues);
        if (s.angleReference() == null) {
            issues.add(error("GEOM-SECTOR-002", path + "/angleReference",
                    "angleReference不能为空"));
        } else if (s.angleReference() == AngleReference.MAGNETIC_NORTH
                && (s.magneticVariation() == null
                || s.magneticVariation().value() == null
                || s.magneticVariation().direction() == null)) {
            issues.add(error("GEOM-SECTOR-003", path + "/magneticVariation",
                    "磁北角必须提供完整的磁差值和方向"));
        }
        elevation(s.elevation(), path + "/elevation", issues);
    }

    private void corridor(Corridor c, Set<String> ids, List<GeometryIssue> issues) {
        positive(c.width(), "/geometry/width", issues);
        if (c.centreline() == null) {
            issues.add(error("GEOM-CORRIDOR-001", "/geometry/centreline",
                    "centreline不能为空"));
            return;
        }
        if (!java.util.Objects.equals(c.crs(), c.centreline().crs())) {
            issues.add(error("GEOM-CORRIDOR-002", "/geometry/centreline/crs",
                    "centreline CRS必须与Corridor CRS一致"));
        }
        curve(c.centreline(), "/geometry/centreline", ids, issues);
    }

    private void segments(List<Segment> segments, String path, boolean closed,
                          List<GeometryIssue> issues) {
        if (segments == null || segments.isEmpty()) {
            issues.add(error("GEOM-LINE-001", path, "至少需要一个线段"));
            return;
        }
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            String p = path + "/" + i;
            if (s == null) continue;
            position(s.start(), p + "/start", issues);
            position(s.end(), p + "/end", issues);
            switch (s) {
                case Geodesic g -> {
                    if (g.positions() == null || g.positions().size() < 2) {
                        issues.add(error("GEOM-GEODESIC-001", p + "/positions",
                                "测地线至少需要两个坐标"));
                    } else {
                        for (int j = 0; j < g.positions().size(); j++) {
                            position(g.positions().get(j), p + "/positions/" + j, issues);
                        }
                    }
                }
                case Parallel parallel -> {
                    if (parallel.start() != null && parallel.end() != null
                            && parallel.start().y() != null && parallel.end().y() != null
                            && parallel.start().y().subtract(parallel.end().y()).abs()
                            .doubleValue() > CONTINUITY_TOLERANCE_DEGREES) {
                        issues.add(error("GEOM-PARALLEL-001", p,
                                "沿纬线段的两个端点纬度必须相同"));
                    }
                }
                case ArcByEdge arc -> validateArcByEdge(arc, p, issues);
                case ArcByCenter arc -> {
                    position(arc.center(), p + "/center", issues);
                    positive(arc.radius(), p + "/radius", issues);
                    angle(arc.startAngle(), p + "/startAngle", issues);
                    angle(arc.endAngle(), p + "/endAngle", issues);
                }
            }
            if (i > 0 && !same(segments.get(i - 1).end(), s.start())) {
                issues.add(error("GEOM-LINE-002", p + "/start",
                        "当前线段起点与上一线段终点不连续"));
            }
        }
        if (closed && !hasNullPositions(segments)
                && !same(segments.getLast().end(), segments.getFirst().start())) {
            issues.add(error("GEOM-POLYGON-001", path,
                    "多边形最后一个线段的终点必须等于第一个线段的起点"));
        }
    }

    private void validateArcByEdge(ArcByEdge arc, String path,
                                   List<GeometryIssue> issues) {
        position(arc.through(), path + "/through", issues);
        if (arc.start() == null || arc.through() == null || arc.end() == null) return;
        if (same(arc.start(), arc.through()) || same(arc.through(), arc.end())
                || same(arc.start(), arc.end())) {
            issues.add(error("GEOM-ARC-EDGE-001", path,
                    "三点圆弧的三个控制点必须互不相同"));
            return;
        }
        double area2 = (arc.through().x().doubleValue() - arc.start().x().doubleValue())
                * (arc.end().y().doubleValue() - arc.start().y().doubleValue())
                - (arc.through().y().doubleValue() - arc.start().y().doubleValue())
                * (arc.end().x().doubleValue() - arc.start().x().doubleValue());
        if (Math.abs(area2) < 1e-12) {
            issues.add(error("GEOM-ARC-EDGE-002", path,
                    "三点圆弧的三个控制点不能共线"));
        }
    }

    private void elevation(Elevation e, String path, List<GeometryIssue> issues) {
        if (e == null) return;
        measure(e.elevation(), path + "/elevation", false, issues);
        measure(e.geoidUndulation(), path + "/geoidUndulation", true, issues);
        measure(e.verticalAccuracy(), path + "/verticalAccuracy", false, issues);
        if (e.verticalDatum() != null && e.verticalDatum().isBlank()) {
            issues.add(error("GEOM-ELEVATION-001", path + "/verticalDatum",
                    "verticalDatum不能为空字符串"));
        }
    }

    private void positive(Measure m, String path, List<GeometryIssue> issues) {
        measure(m, path, false, issues);
        if (m != null && m.value() != null && m.value().compareTo(ZERO) <= 0) {
            issues.add(error("GEOM-MEASURE-001", path + "/value", "量值必须大于0"));
        }
    }

    private void measure(Measure m, String path, boolean signed,
                         List<GeometryIssue> issues) {
        if (m == null) return;
        if (m.value() == null) {
            issues.add(error("GEOM-MEASURE-002", path + "/value", "量值不能为空"));
        } else if (!signed && m.value().compareTo(ZERO) < 0) {
            issues.add(error("GEOM-MEASURE-003", path + "/value", "量值不能为负数"));
        }
        if (m.uom() == null || !Set.of("M", "m", "KM", "km", "NM", "[nmi_i]",
                "FT", "ft", "[ft_i]").contains(m.uom())) {
            issues.add(error("GEOM-MEASURE-004", path + "/uom", "不支持的距离单位"));
        }
    }

    private void angle(BigDecimal value, String path, List<GeometryIssue> issues) {
        if (value == null) {
            issues.add(error("GEOM-ANGLE-001", path, "角度不能为空"));
        }
    }

    private void position(Position p, String path, List<GeometryIssue> issues) {
        if (p == null || p.x() == null || p.y() == null) {
            issues.add(error("GEOM-POSITION-001", path, "坐标x和y不能为空"));
            return;
        }
        if (p.x().compareTo(BigDecimal.valueOf(-180)) < 0
                || p.x().compareTo(BigDecimal.valueOf(180)) > 0) {
            issues.add(error("GEOM-POSITION-002", path + "/x", "经度必须位于[-180,180]"));
        }
        if (p.y().compareTo(BigDecimal.valueOf(-90)) < 0
                || p.y().compareTo(BigDecimal.valueOf(90)) > 0) {
            issues.add(error("GEOM-POSITION-003", path + "/y", "纬度必须位于[-90,90]"));
        }
    }

    private void validateCrs(String crs, String path, List<GeometryIssue> issues) {
        if (!Set.of("EPSG:4326", "urn:ogc:def:crs:EPSG::4326").contains(crs)) {
            issues.add(error("GEOM-CRS-001", path,
                    "V1只接受WGS-84（EPSG:4326）输入"));
        }
    }

    private void validateId(String id, String path, Set<String> ids,
                            List<GeometryIssue> issues) {
        if (id == null || !NCNAME.matcher(id).matches()
                || id.toLowerCase().startsWith("xml")) {
            issues.add(error("GEOM-ID-001", path, "gml:id必须是合法的XML NCName"));
        } else if (!ids.add(id)) {
            issues.add(error("GEOM-ID-002", path, "同一片段中的gml:id不能重复"));
        }
    }

    private static boolean validMeasure(Measure m) {
        return m != null && m.value() != null && m.uom() != null
                && !Double.isNaN(GeometryJsonParser.metres(m));
    }

    private static boolean same(Position a, Position b) {
        return a != null && b != null && a.x() != null && a.y() != null
                && b.x() != null && b.y() != null
                && Math.abs(a.x().doubleValue() - b.x().doubleValue())
                <= CONTINUITY_TOLERANCE_DEGREES
                && Math.abs(a.y().doubleValue() - b.y().doubleValue())
                <= CONTINUITY_TOLERANCE_DEGREES;
    }

    private static boolean hasNullPositions(List<Segment> segments) {
        return segments.stream().anyMatch(s -> s == null
                || s.start() == null || s.end() == null);
    }

    /**
     * JTS validates the explicit control polygon. Curved segment semantics are
     * additionally checked by their dedicated validators before encoding.
     */
    private static List<Position> boundaryControls(List<Segment> segments) {
        List<Position> result = new ArrayList<>();
        result.add(segments.getFirst().start());
        for (Segment s : segments) {
            if (s instanceof Geodesic g && g.positions().size() > 2) {
                result.addAll(g.positions().subList(1, g.positions().size()));
            } else if (s instanceof ArcByEdge a) {
                result.add(a.through());
                result.add(a.end());
            } else {
                result.add(s.end());
            }
        }
        return result;
    }

    private static GeometryIssue error(String rule, String path, String message) {
        return GeometryIssue.error(rule, path, message);
    }
}
