package com.example.digitalnotam.scenario.atsanew;

import java.util.Map;

/** ATSA.NEW type-to-Q-code mapping from the scenario production rules. */
public final class AtsaNewQCodeResolver {
    private static final Map<String,String> CODES=Map.ofEntries(
            Map.entry("CTR","QACCS"),Map.entry("ADIZ","QADCS"),
            Map.entry("CTA","QAECS"),Map.entry("UTA","QAHCS"),
            Map.entry("OCA","QAOCS"),Map.entry("AWY","QARCS"),
            Map.entry("TMA","QATCS"),Map.entry("ADV","QAVCS"),
            Map.entry("UADV","QAVCS"),Map.entry("ATZ","QAZCS"),
            Map.entry("FIR","QAFCS"));
    public String resolve(String type){return CODES.getOrDefault(type,"QXXXX");}
}
