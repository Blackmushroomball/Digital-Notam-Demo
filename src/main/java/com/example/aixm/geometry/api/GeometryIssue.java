package com.example.aixm.geometry.api;

/** A machine-readable validation or encoding diagnostic. */
public record GeometryIssue(
        Severity severity,
        String rule,
        String path,
        String message) {
    public enum Severity { ERROR, WARNING }

    public static GeometryIssue error(String rule, String path, String message) {
        return new GeometryIssue(Severity.ERROR, rule, path, message);
    }
}
