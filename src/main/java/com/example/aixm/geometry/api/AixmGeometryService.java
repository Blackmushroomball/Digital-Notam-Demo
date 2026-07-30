package com.example.aixm.geometry.api;

import com.example.aixm.geometry.model.GeometryModel.Geometry;

import java.util.List;

/**
 * Stable entry point for applications that need AIXM 5.1.1 geometry fragments.
 */
public interface AixmGeometryService {
    /** Parses, validates and encodes the versioned JSON request. */
    EncodingResult encode(String json);

    /** Validates JSON without producing XML. */
    List<GeometryIssue> validate(String json);

    /** Type-safe entry point for Java callers that already constructed a model. */
    EncodingResult encode(Geometry geometry);
}
