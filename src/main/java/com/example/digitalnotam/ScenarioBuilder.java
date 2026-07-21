package com.example.digitalnotam;

import org.w3c.dom.Document;

interface ScenarioBuilder {
    String scenario();
    Document build(Notam notam) throws Exception;
    void validate(Document document, Notam notam);
}
