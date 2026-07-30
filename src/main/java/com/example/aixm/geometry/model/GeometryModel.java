package com.example.aixm.geometry.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public, XML-independent geometry model accepted by the AIXM encoder.
 *
 * <p>JSON coordinates always use mathematical x/y order: x is WGS-84 longitude
 * and y is WGS-84 latitude. The encoder reverses them when writing EPSG:4326
 * GML positions, whose axis order is latitude/longitude.</p>
 */
public final class GeometryModel {
    private GeometryModel() {}

    public static final String WGS84 = "EPSG:4326";

    public record Position(BigDecimal x, BigDecimal y) {}
    public record Measure(BigDecimal value, String uom) {}

    /** Optional AIXM 2.5D properties. A null property is simply not encoded. */
    public record Elevation(
            Measure elevation,
            Measure geoidUndulation,
            String verticalDatum,
            Measure verticalAccuracy) {
        public boolean present() {
            return elevation != null || geoidUndulation != null
                    || verticalDatum != null || verticalAccuracy != null;
        }
    }

    public sealed interface Geometry
            permits Point, Curve, Polygon, Circle, CircleSector, Corridor {
        String gmlId();
        String crs();
    }

    public record Point(String gmlId, String crs, Position position,
                        Elevation elevation) implements Geometry {}

    public record Curve(String gmlId, String crs, List<Segment> segments,
                        Elevation elevation) implements Geometry {}

    public record Polygon(String gmlId, String crs, List<Segment> segments,
                          Elevation elevation) implements Geometry {}

    public record Circle(String gmlId, String crs, Position center,
                         Measure radius, Elevation elevation) implements Geometry {}

    public enum AngleReference { TRUE_NORTH, MAGNETIC_NORTH }
    public enum Direction { EAST, WEST }
    public record MagneticVariation(BigDecimal value, Direction direction) {}

    public record CircleSector(
            String gmlId,
            String crs,
            Position center,
            Measure innerRadius,
            Measure outerRadius,
            BigDecimal startAngle,
            BigDecimal endAngle,
            AngleReference angleReference,
            MagneticVariation magneticVariation,
            Elevation elevation) implements Geometry {}

    /**
     * A corridor is encoded as an AirspaceVolume because width belongs to that
     * AIXM object, not to gml:Curve. Width is the full AIXM width, not half-width.
     */
    public record Corridor(String gmlId, String crs, Curve centreline,
                           Measure width) implements Geometry {}

    public sealed interface Segment
            permits Geodesic, Parallel, ArcByEdge, ArcByCenter {
        Position start();
        Position end();
    }

    public record Geodesic(List<Position> positions) implements Segment {
        @Override public Position start() { return positions.getFirst(); }
        @Override public Position end() { return positions.getLast(); }
    }

    public record Parallel(Position start, Position end) implements Segment {}

    /** Three control points define exactly one circular arc. */
    public record ArcByEdge(Position start, Position through,
                            Position end) implements Segment {}

    public record ArcByCenter(
            Position start,
            Position end,
            Position center,
            Measure radius,
            BigDecimal startAngle,
            BigDecimal endAngle) implements Segment {}
}
