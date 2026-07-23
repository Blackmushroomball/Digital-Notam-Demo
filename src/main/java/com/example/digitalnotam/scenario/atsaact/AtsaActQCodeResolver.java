package com.example.digitalnotam.scenario.atsaact;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

public final class AtsaActQCodeResolver {
    private static final Path CONFIG=Path.of("config","scenarios","atsa-act-q-code.properties");
    private final Properties values=new Properties();
    public AtsaActQCodeResolver(){
        try(InputStream in=Files.newInputStream(CONFIG)){values.load(in);}
        catch(Exception e){throw new IllegalStateException("Unable to load "+CONFIG+": "+e.getMessage(),e);}
    }
    public Mapping resolve(String baselineType,String status){
        String type=normalize(baselineType);
        String raw=values.getProperty(type+"."+status);
        if(raw==null)raw=values.getProperty("OTHER."+status);
        String[] p=raw.split(",",-1);
        if(p.length!=3||!p[0].matches("Q[A-Z]{4}")||p[1].isBlank()||p[2].isBlank())
            throw new IllegalArgumentException("ATSA.ACT Q mapping is incomplete for "+type+" "+status);
        return new Mapping(p[0],p[1],p[2]);
    }
    static String normalize(String type){
        if(type==null)return "OTHER";
        return switch(type){case"CTR_P"->"CTR";case"CTA_P"->"CTA";case"UTA_P"->"UTA";case"OCA_P"->"OCA";case"TMA_P"->"TMA";case"ATZ_P"->"ATZ";case"FIR_P"->"FIR";default->Set.of("CTR","ADIZ","CTA","FIR","UTA","OCA","TMA","ADV","UADV","ATZ","RAS").contains(type)?type:"OTHER";};
    }
    public record Mapping(String qCode,String traffic,String purpose){}
}
