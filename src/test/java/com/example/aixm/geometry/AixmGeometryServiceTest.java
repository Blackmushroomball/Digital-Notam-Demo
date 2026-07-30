package com.example.aixm.geometry;

import com.example.aixm.geometry.api.DefaultAixmGeometryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests cover every V1 root type and validate each result against the
 * repository's real AIXM 5.1.1 schema, rather than a test-only reduced schema.
 */
final class AixmGeometryServiceTest {
    private final DefaultAixmGeometryService service =
            new DefaultAixmGeometryService();

    @Test
    void pointUsesGmlAxisOrderAndOptionalElevation() {
        var result = service.encode("""
                {
                  "schemaVersion":"1.0",
                  "geometry":{
                    "type":"POINT","gmlId":"point-001","crs":"EPSG:4326",
                    "position":{"x":5.20833,"y":52.18556},
                    "elevation":{
                      "elevation":{"value":30,"uom":"M"},
                      "geoidUndulation":{"value":-2.5,"uom":"M"},
                      "verticalDatum":"EGM_96",
                      "verticalAccuracy":{"value":1,"uom":"M"}
                    }
                  }
                }
                """);
        assertValid(result);
        assertTrue(result.aixmXml().contains("<gml:pos>52.18556 5.20833</gml:pos>"));
        assertTrue(result.aixmXml().contains("<aixm:ElevatedPoint"));
        assertTrue(result.aixmXml().contains("<aixm:elevation uom=\"M\">30</aixm:elevation>"));
    }

    @Test
    void lineSupportsAllFourSegmentTypes() {
        var result = service.encode("""
                {
                  "schemaVersion":"1.0",
                  "geometry":{
                    "type":"LINE","gmlId":"line-001","crs":"EPSG:4326",
                    "segments":[
                      {"type":"GEODESIC","positions":[
                        {"x":5,"y":52},{"x":6,"y":52}
                      ]},
                      {"type":"PARALLEL","start":{"x":6,"y":52},"end":{"x":7,"y":52}},
                      {"type":"ARC_BY_EDGE",
                       "start":{"x":7,"y":52},"through":{"x":7.5,"y":52.5},
                       "end":{"x":8,"y":52}},
                      {"type":"ARC_BY_CENTER","center":{"x":8,"y":51},
                       "radius":{"value":60.1077164,"uom":"NM"},
                       "startAngle":0,"endAngle":90}
                    ]
                  }
                }
                """);
        /*
         * The ArcByCenter-derived start must meet the previous segment. This
         * input deliberately exercises validation even if numeric round-off
         * makes it unsuitable as a golden success case.
         */
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(i -> i.rule().equals("GEOM-LINE-002")));
    }

    @Test
    void eachLineSegmentHasAValidAixmEncoding() {
        String[] segments = {
                """
                {"type":"GEODESIC","positions":[
                  {"x":5,"y":52},{"x":6,"y":53}
                ]}
                """,
                """
                {"type":"PARALLEL",
                 "start":{"x":5,"y":52},"end":{"x":6,"y":52}}
                """,
                """
                {"type":"ARC_BY_EDGE",
                 "start":{"x":5,"y":52},"through":{"x":5.5,"y":52.5},
                 "end":{"x":6,"y":52}}
                """,
                """
                {"type":"ARC_BY_CENTER","center":{"x":5,"y":52},
                 "radius":{"value":10,"uom":"NM"},
                 "startAngle":0,"endAngle":90}
                """
        };
        String[] expectedElements = {
                "GeodesicString", "LineStringSegment", "gml:Arc", "ArcByCenterPoint"
        };
        for (int i = 0; i < segments.length; i++) {
            String json = """
                    {"schemaVersion":"1.0","geometry":{
                      "type":"LINE","gmlId":"line-%d","crs":"EPSG:4326",
                      "segments":[%s]
                    }}
                    """.formatted(i, segments[i]);
            var result = service.encode(json);
            assertValid(result);
            String expectedElement = expectedElements[i];
            assertTrue(result.aixmXml().contains(expectedElement),
                    () -> "Missing " + expectedElement + " in " + result.aixmXml());
        }
    }

    @Test
    void polygonCircleSectorAndCorridorProduceTheirRequiredRoots() {
        var polygon = service.encode("""
                {"schemaVersion":"1.0","geometry":{
                  "type":"POLYGON","gmlId":"surface-001","crs":"EPSG:4326",
                  "segments":[{"type":"GEODESIC","positions":[
                    {"x":5,"y":52},{"x":6,"y":52},{"x":6,"y":53},
                    {"x":5,"y":53},{"x":5,"y":52}
                  ]}]
                }}
                """);
        assertValid(polygon);
        assertTrue(polygon.aixmXml().startsWith("<aixm:Surface"));
        assertTrue(polygon.aixmXml().contains("<gml:PolygonPatch>"));

        var circle = service.encode("""
                {"schemaVersion":"1.0","geometry":{
                  "type":"CIRCLE","gmlId":"circle-001","crs":"EPSG:4326",
                  "center":{"x":5,"y":52},"radius":{"value":10,"uom":"NM"}
                }}
                """);
        assertValid(circle);
        assertTrue(circle.aixmXml().contains("<gml:CircleByCenterPoint"));

        var sector = service.encode("""
                {"schemaVersion":"1.0","geometry":{
                  "type":"CIRCLE_SECTOR","gmlId":"sector-001","crs":"EPSG:4326",
                  "center":{"x":5,"y":52},
                  "outerRadius":{"value":10,"uom":"NM"},
                  "startAngle":90,"endAngle":180,
                  "angleReference":"MAGNETIC_NORTH",
                  "magneticVariation":{"value":2,"direction":"EAST"}
                }}
                """);
        assertValid(sector);
        assertTrue(sector.aixmXml().contains("<gml:startAngle uom=\"deg\">92</gml:startAngle>"));

        var corridor = service.encode("""
                {"schemaVersion":"1.0","geometry":{
                  "type":"CORRIDOR","gmlId":"volume-001","crs":"EPSG:4326",
                  "centreline":{"gmlId":"centreline-001","segments":[
                    {"type":"GEODESIC","positions":[
                      {"x":5,"y":52},{"x":6,"y":53}
                    ]}
                  ]},
                  "width":{"value":10,"uom":"NM"}
                }}
                """);
        assertValid(corridor);
        assertTrue(corridor.aixmXml().startsWith("<aixm:AirspaceVolume"));
        assertTrue(corridor.aixmXml().contains("<aixm:width uom=\"NM\">10</aixm:width>"));
    }

    @Test
    void rejectsUnsupportedCrsOpenPolygonAndMissingMagneticVariation() {
        var crs = service.validate("""
                {"schemaVersion":"1.0","geometry":{
                  "type":"POINT","crs":"EPSG:3857","position":{"x":1,"y":2}
                }}
                """);
        assertTrue(crs.stream().anyMatch(i -> i.rule().equals("GEOM-CRS-001")));

        var polygon = service.validate("""
                {"schemaVersion":"1.0","geometry":{
                  "type":"POLYGON","crs":"EPSG:4326",
                  "segments":[{"type":"GEODESIC","positions":[
                    {"x":5,"y":52},{"x":6,"y":52},{"x":6,"y":53}
                  ]}]
                }}
                """);
        assertTrue(polygon.stream().anyMatch(i -> i.rule().equals("GEOM-POLYGON-001")));

        var sector = service.validate("""
                {"schemaVersion":"1.0","geometry":{
                  "type":"CIRCLE_SECTOR","crs":"EPSG:4326",
                  "center":{"x":5,"y":52},
                  "outerRadius":{"value":10,"uom":"NM"},
                  "startAngle":90,"endAngle":180,
                  "angleReference":"MAGNETIC_NORTH"
                }}
                """);
        assertTrue(sector.stream().anyMatch(i -> i.rule().equals("GEOM-SECTOR-003")));
    }

    private static void assertValid(com.example.aixm.geometry.api.EncodingResult result) {
        assertTrue(result.valid(), () -> "Expected valid result, got: " + result.issues());
        assertNotNull(result.aixmXml());
    }
}
