package com.example.digitalnotam.scenario.atsanew;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtsaNewGeometrySupportTest {
    @Test
    void circleSectorProducesAnOperationalFootprint() {
        String json = """
                {"schemaVersion":"1.0","geometry":{
                  "type":"CIRCLE_SECTOR","gmlId":"sector-map-test","crs":"EPSG:4326",
                  "center":{"x":-27.4,"y":53.62},
                  "innerRadius":{"value":2,"uom":"NM"},
                  "outerRadius":{"value":10,"uom":"NM"},
                  "startAngle":15,"endAngle":120,"angleReference":"TRUE_NORTH"
                }}
                """;

        var analysis = new AtsaNewGeometrySupport().analyse(json);

        assertFalse(analysis.boundary().isEmpty());
        assertTrue(analysis.footprint().isValid());
        assertTrue(analysis.radiusNm() > 0);
    }

    @Test
    void graphicalArcAreaContractProducesAnOperationalFootprint() {
        String json = """
                {"schemaVersion":"1.0","geometry":{
                  "type":"POLYGON","gmlId":"arc-map-test","crs":"EPSG:4326","segments":[
                    {"type":"ARC_BY_EDGE","start":{"x":-27.55,"y":53.55},"through":{"x":-27.4,"y":53.72},"end":{"x":-27.25,"y":53.55}},
                    {"type":"GEODESIC","positions":[{"x":-27.25,"y":53.55},{"x":-27.55,"y":53.55}]}
                  ]
                }}
                """;

        var analysis = new AtsaNewGeometrySupport().analyse(json);

        assertFalse(analysis.boundary().isEmpty());
        assertTrue(analysis.footprint().isValid());
        assertTrue(analysis.radiusNm() > 0);
    }
}
