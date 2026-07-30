package com.example.aixm.geometry.api;

import java.util.List;

/** Result returned for both successful and rejected JSON requests. */
public record EncodingResult(
        boolean valid,
        String aixmXml,
        List<GeometryIssue> issues) {

    public EncodingResult {
        issues = List.copyOf(issues);
    }

    public static EncodingResult success(String xml) {
        return new EncodingResult(true, xml, List.of());
    }

    public static EncodingResult failure(List<GeometryIssue> issues) {
        return new EncodingResult(false, null, issues);
    }
}
