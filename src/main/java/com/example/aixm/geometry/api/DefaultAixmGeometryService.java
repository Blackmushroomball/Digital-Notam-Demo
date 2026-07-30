package com.example.aixm.geometry.api;

import com.example.aixm.geometry.encoding.AixmFragmentEncoder;
import com.example.aixm.geometry.json.GeometryJsonParser;
import com.example.aixm.geometry.model.GeometryModel.Geometry;
import com.example.aixm.geometry.validation.AixmSchemaValidator;
import com.example.aixm.geometry.validation.GeometryValidator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Default orchestration service: JSON -> model -> rules -> XML -> AIXM XSD.
 *
 * <p>The class is stateless apart from the thread-safe cached XSD schema and
 * can therefore be shared by web requests.</p>
 */
public final class DefaultAixmGeometryService implements AixmGeometryService {
    private static final Path DEFAULT_SCHEMA = Path.of(
            "schemas", "aixm-5.1.1", "aixm-5.1.1", "AIXM_Features.xsd");

    private final GeometryJsonParser parser = new GeometryJsonParser();
    private final GeometryValidator validator = new GeometryValidator();
    private final AixmFragmentEncoder encoder = new AixmFragmentEncoder();
    private final AixmSchemaValidator schemaValidator;

    public DefaultAixmGeometryService() {
        this(DEFAULT_SCHEMA);
    }

    public DefaultAixmGeometryService(Path aixmFeaturesSchema) {
        schemaValidator = new AixmSchemaValidator(aixmFeaturesSchema);
    }

    @Override
    public EncodingResult encode(String json) {
        GeometryJsonParser.ParseResult parsed = parser.parse(json);
        if (!parsed.issues().isEmpty()) return EncodingResult.failure(parsed.issues());
        return encode(parsed.geometry());
    }

    @Override
    public List<GeometryIssue> validate(String json) {
        GeometryJsonParser.ParseResult parsed = parser.parse(json);
        if (!parsed.issues().isEmpty()) return parsed.issues();
        return validator.validate(parsed.geometry());
    }

    @Override
    public EncodingResult encode(Geometry geometry) {
        List<GeometryIssue> issues = new ArrayList<>(validator.validate(geometry));
        if (!issues.isEmpty()) return EncodingResult.failure(issues);
        try {
            var document = encoder.encode(geometry);
            issues.addAll(schemaValidator.validate(document));
            if (!issues.isEmpty()) return EncodingResult.failure(issues);
            return EncodingResult.success(encoder.serialize(document));
        } catch (Exception ex) {
            issues.add(GeometryIssue.error(
                    "AIXM-ENCODING-001", "/", "AIXM片段编码失败: " + ex.getMessage()));
            return EncodingResult.failure(issues);
        }
    }
}
