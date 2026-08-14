package com.example.digitalnotam.baseline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineAirspaceCatalogJsonTest {
    @Test
    void mapPayloadContainsResolvedBoundaryPoints() throws Exception {
        String json = new BaselineAirspaceCatalog().json();
        var groups = new ObjectMapper().readTree(json);

        assertTrue(groups.isArray());
        assertTrue(groups.size() > 0);
        assertTrue(json.contains("\"points\":[{"));
        assertTrue(json.contains("\"latitude\":"));
        assertTrue(json.contains("\"longitude\":"));
    }
}
