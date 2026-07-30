package com.example.aixm.geometry.json;

import com.example.aixm.geometry.api.GeometryIssue;
import com.example.aixm.geometry.model.GeometryModel.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.geographiclib.GeodesicData;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Converts the versioned JSON contract into the XML-independent geometry model.
 *
 * <p>The parser reports JSON paths instead of leaking Jackson exceptions to a
 * caller. Semantic geometry checks remain in {@code GeometryValidator}.</p>
 */
public final class GeometryJsonParser {
    private static final ObjectMapper JSON = new ObjectMapper();

    public ParseResult parse(String source) {
        List<GeometryIssue> issues = new ArrayList<>();
        if (source == null || source.isBlank()) {
            issues.add(error("JSON-001", "/", "JSON输入不能为空"));
            return new ParseResult(null, issues);
        }
        try {
            JsonNode root = JSON.readTree(source);
            if (!"1.0".equals(text(root, "schemaVersion"))) {
                issues.add(error("JSON-002", "/schemaVersion", "schemaVersion必须为1.0"));
            }
            JsonNode node = root.get("geometry");
            if (node == null || !node.isObject()) {
                issues.add(error("JSON-003", "/geometry", "geometry对象不能为空"));
                return new ParseResult(null, issues);
            }
            Geometry geometry = geometry(node, "/geometry", issues);
            return new ParseResult(issues.isEmpty() ? geometry : null, issues);
        } catch (JsonProcessingException ex) {
            issues.add(error("JSON-004", "/", "JSON语法错误: " + ex.getOriginalMessage()));
            return new ParseResult(null, issues);
        } catch (RuntimeException ex) {
            issues.add(error("JSON-005", "/", "无法解析几何输入: " + ex.getMessage()));
            return new ParseResult(null, issues);
        }
    }

    private Geometry geometry(JsonNode n, String path, List<GeometryIssue> issues) {
        String type = requiredText(n, "type", path, issues);
        String crs = requiredText(n, "crs", path, issues);
        String id = id(n, type == null ? "geometry" : type.toLowerCase(Locale.ROOT));
        if (type == null) return null;
        return switch (type) {
            case "POINT" -> new Point(id, crs, position(n.get("position"), path + "/position", issues),
                    elevation(n.get("elevation"), path + "/elevation", issues));
            case "LINE" -> new Curve(id, crs, segments(n.get("segments"), path + "/segments", issues),
                    elevation(n.get("elevation"), path + "/elevation", issues));
            case "POLYGON" -> new Polygon(id, crs, segments(n.get("segments"), path + "/segments", issues),
                    elevation(n.get("elevation"), path + "/elevation", issues));
            case "CIRCLE" -> new Circle(id, crs,
                    position(n.get("center"), path + "/center", issues),
                    measure(n.get("radius"), path + "/radius", issues),
                    elevation(n.get("elevation"), path + "/elevation", issues));
            case "CIRCLE_SECTOR" -> sector(id, crs, n, path, issues);
            case "CORRIDOR" -> corridor(id, crs, n, path, issues);
            default -> {
                issues.add(error("JSON-006", path + "/type", "不支持的几何类型: " + type));
                yield null;
            }
        };
    }

    private CircleSector sector(String id, String crs, JsonNode n, String path,
                                List<GeometryIssue> issues) {
        String reference = requiredText(n, "angleReference", path, issues);
        AngleReference angleReference = enumValue(
                AngleReference.class, reference, path + "/angleReference", issues);
        MagneticVariation variation = null;
        if (n.hasNonNull("magneticVariation")) {
            JsonNode v = n.get("magneticVariation");
            Direction direction = enumValue(Direction.class, text(v, "direction"),
                    path + "/magneticVariation/direction", issues);
            variation = new MagneticVariation(decimal(v, "value",
                    path + "/magneticVariation/value", issues), direction);
        }
        return new CircleSector(id, crs,
                position(n.get("center"), path + "/center", issues),
                optionalMeasure(n.get("innerRadius"), path + "/innerRadius", issues),
                measure(n.get("outerRadius"), path + "/outerRadius", issues),
                decimal(n, "startAngle", path + "/startAngle", issues),
                decimal(n, "endAngle", path + "/endAngle", issues),
                angleReference, variation,
                elevation(n.get("elevation"), path + "/elevation", issues));
    }

    private Corridor corridor(String id, String crs, JsonNode n, String path,
                              List<GeometryIssue> issues) {
        JsonNode line = n.get("centreline");
        if (line == null || !line.isObject()) {
            issues.add(error("JSON-007", path + "/centreline", "centreline对象不能为空"));
            return new Corridor(id, crs, null,
                    measure(n.get("width"), path + "/width", issues));
        }
        String lineCrs = text(line, "crs");
        if (lineCrs == null) lineCrs = crs;
        Curve centreline = new Curve(id(line, "curve"), lineCrs,
                segments(line.get("segments"), path + "/centreline/segments", issues),
                elevation(line.get("elevation"), path + "/centreline/elevation", issues));
        return new Corridor(id, crs, centreline,
                measure(n.get("width"), path + "/width", issues));
    }

    private List<Segment> segments(JsonNode n, String path, List<GeometryIssue> issues) {
        List<Segment> result = new ArrayList<>();
        if (n == null || !n.isArray() || n.isEmpty()) {
            issues.add(error("JSON-008", path, "segments必须是非空数组"));
            return result;
        }
        for (int i = 0; i < n.size(); i++) {
            JsonNode s = n.get(i);
            String p = path + "/" + i;
            String type = requiredText(s, "type", p, issues);
            if (type == null) continue;
            switch (type) {
                case "GEODESIC" -> result.add(new Geodesic(
                        positions(s.get("positions"), p + "/positions", issues)));
                case "PARALLEL" -> result.add(new Parallel(
                        position(s.get("start"), p + "/start", issues),
                        position(s.get("end"), p + "/end", issues)));
                case "ARC_BY_EDGE" -> result.add(new ArcByEdge(
                        position(s.get("start"), p + "/start", issues),
                        position(s.get("through"), p + "/through", issues),
                        position(s.get("end"), p + "/end", issues)));
                case "ARC_BY_CENTER" -> result.add(arcByCenter(s, p, issues));
                default -> issues.add(error("JSON-009", p + "/type",
                        "不支持的线段类型: " + type));
            }
        }
        return result;
    }

    private ArcByCenter arcByCenter(JsonNode n, String path, List<GeometryIssue> issues) {
        Position center = position(n.get("center"), path + "/center", issues);
        Measure radius = measure(n.get("radius"), path + "/radius", issues);
        BigDecimal startAngle = decimal(n, "startAngle", path + "/startAngle", issues);
        BigDecimal endAngle = decimal(n, "endAngle", path + "/endAngle", issues);
        Position start = destination(center, radius, startAngle);
        Position end = destination(center, radius, endAngle);
        return new ArcByCenter(start, end, center, radius, startAngle, endAngle);
    }

    private List<Position> positions(JsonNode n, String path, List<GeometryIssue> issues) {
        List<Position> result = new ArrayList<>();
        if (n == null || !n.isArray()) {
            issues.add(error("JSON-010", path, "positions必须是数组"));
            return result;
        }
        for (int i = 0; i < n.size(); i++) {
            result.add(position(n.get(i), path + "/" + i, issues));
        }
        return result;
    }

    private Position position(JsonNode n, String path, List<GeometryIssue> issues) {
        if (n == null || !n.isObject()) {
            issues.add(error("JSON-011", path, "坐标对象不能为空"));
            return null;
        }
        return new Position(coordinate(n, "x", path + "/x", CoordinateParser.Axis.LONGITUDE, issues),
                coordinate(n, "y", path + "/y", CoordinateParser.Axis.LATITUDE, issues));
    }

    private BigDecimal coordinate(JsonNode n,String field,String path,CoordinateParser.Axis axis,
                                  List<GeometryIssue> issues){
        JsonNode value=n==null?null:n.get(field);
        if(value!=null&&value.isNumber())return value.decimalValue();
        if(value!=null&&value.isTextual())try{return CoordinateParser.parse(value.textValue(),axis);}
        catch(IllegalArgumentException e){issues.add(error("JSON-014",path,e.getMessage()));return null;}
        issues.add(error("JSON-014",path,"必须是十进制度数值或度分秒字符串"));return null;
    }

    private Elevation elevation(JsonNode n, String path, List<GeometryIssue> issues) {
        if (n == null || n.isNull()) return null;
        if (!n.isObject()) {
            issues.add(error("JSON-012", path, "elevation必须是对象"));
            return null;
        }
        return new Elevation(
                optionalMeasure(n.get("elevation"), path + "/elevation", issues),
                optionalMeasure(n.get("geoidUndulation"), path + "/geoidUndulation", issues),
                text(n, "verticalDatum"),
                optionalMeasure(n.get("verticalAccuracy"), path + "/verticalAccuracy", issues));
    }

    private Measure optionalMeasure(JsonNode n, String path, List<GeometryIssue> issues) {
        return n == null || n.isNull() ? null : measure(n, path, issues);
    }

    private Measure measure(JsonNode n, String path, List<GeometryIssue> issues) {
        if (n == null || !n.isObject()) {
            issues.add(error("JSON-013", path, "量值必须包含value和uom"));
            return null;
        }
        String uom = requiredText(n, "uom", path, issues);
        return new Measure(decimal(n, "value", path + "/value", issues), uom);
    }

    private BigDecimal decimal(JsonNode n, String field, String path,
                               List<GeometryIssue> issues) {
        JsonNode value = n == null ? null : n.get(field);
        if (value == null || !value.isNumber()) {
            issues.add(error("JSON-014", path, "必须是数值"));
            return null;
        }
        return value.decimalValue();
    }

    private String requiredText(JsonNode n, String field, String path,
                                List<GeometryIssue> issues) {
        String value = text(n, field);
        if (value == null || value.isBlank()) {
            issues.add(error("JSON-015", path + "/" + field, "不能为空"));
            return null;
        }
        return value;
    }

    private static String text(JsonNode n, String field) {
        JsonNode value = n == null ? null : n.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static String id(JsonNode n, String prefix) {
        String supplied = text(n, "gmlId");
        return supplied == null || supplied.isBlank()
                ? prefix + "-" + UUID.randomUUID()
                : supplied;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String path,
                                             List<GeometryIssue> issues) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            issues.add(error("JSON-016", path, "不支持的取值: " + value));
            return null;
        }
    }

    /**
     * Arc end positions are derived on the WGS-84 ellipsoid. They are used for
     * segment continuity validation and are not emitted as extra GML controls.
     */
    private static Position destination(Position center, Measure radius, BigDecimal bearing) {
        if (center == null || radius == null || radius.value() == null || bearing == null) return null;
        double metres = metres(radius);
        GeodesicData d = net.sf.geographiclib.Geodesic.WGS84.Direct(
                center.y().doubleValue(), center.x().doubleValue(),
                bearing.doubleValue(), metres);
        return new Position(BigDecimal.valueOf(d.lon2), BigDecimal.valueOf(d.lat2));
    }

    public static double metres(Measure measure) {
        double value = measure.value().doubleValue();
        return switch (measure.uom()) {
            case "M", "m" -> value;
            case "KM", "km" -> value * 1_000d;
            case "NM", "[nmi_i]" -> value * 1_852d;
            case "FT", "ft", "[ft_i]" -> value * 0.3048d;
            default -> Double.NaN;
        };
    }

    private static GeometryIssue error(String rule, String path, String message) {
        return GeometryIssue.error(rule, path, message);
    }

    public record ParseResult(Geometry geometry, List<GeometryIssue> issues) {
        public ParseResult {
            issues = List.copyOf(issues);
        }
    }
}
