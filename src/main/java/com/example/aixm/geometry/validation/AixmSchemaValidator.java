package com.example.aixm.geometry.validation;

import com.example.aixm.geometry.api.GeometryIssue;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Validates standalone AIXM object/geometry roots against the local 5.1.1 XSD. */
public final class AixmSchemaValidator {
    private final Path schemaPath;
    private volatile Schema schema;

    public AixmSchemaValidator(Path schemaPath) {
        this.schemaPath = schemaPath.toAbsolutePath().normalize();
    }

    public List<GeometryIssue> validate(Document document) {
        try {
            schema().newValidator().validate(new DOMSource(document));
            return List.of();
        } catch (SAXException ex) {
            return List.of(GeometryIssue.error(
                    "AIXM-XSD-001", "/", "AIXM 5.1.1 Schema校验失败: " + ex.getMessage()));
        } catch (IOException ex) {
            return List.of(GeometryIssue.error(
                    "AIXM-XSD-002", "/", "无法读取AIXM 5.1.1 Schema: " + ex.getMessage()));
        }
    }

    private Schema schema() throws SAXException, IOException {
        Schema known = schema;
        if (known != null) return known;
        synchronized (this) {
            if (schema == null) {
                if (!Files.isRegularFile(schemaPath)) {
                    throw new IOException("Schema不存在: " + schemaPath);
                }
                SchemaFactory factory =
                        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                // Imported schemas are local files relative to AIXM_Features.xsd.
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
                schema = factory.newSchema(schemaPath.toFile());
            }
            return schema;
        }
    }
}
