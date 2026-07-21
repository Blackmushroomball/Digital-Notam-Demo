package com.example.digitalnotam.workflow;

import com.example.digitalnotam.domain.Notam;
import org.w3c.dom.Document;

public interface ScenarioBuilder {
    String scenario();
    Document build(Notam notam) throws Exception;
    void validate(Document document, Notam notam);
}
