package com.example.digitalnotam.domain;

public record NavUnsData(
        String navaidUuid,
        String impactMode,
        String equipmentUuid,
        String signalType,
        String operationalStatus,
        boolean signalStillEmitted) {
    public static NavUnsData empty(){return new NavUnsData("","","","","",false);}
}
