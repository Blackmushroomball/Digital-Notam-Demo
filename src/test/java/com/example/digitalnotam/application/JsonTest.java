package com.example.digitalnotam.application;

public final class JsonTest {
    public static void main(String[] args){var values=Json.parseObjectArray("{\"scenario\":\"AD.LIM\",\"restrictions\":[{\"limitationType\":\"RESERV\",\"operation\":\"ALL\",\"aircraftWeight\":12.5,\"aircraftWeightUom\":\"T\",\"aircraftWeightInterpretation\":\"AT_OR_ABOVE\",\"pprValue\":0},{\"limitationType\":\"FORBID\",\"operation\":\"OTHER:NIGHT_OPS\"}]}","restrictions");if(values.size()!=2||!"RESERV".equals(values.get(0).get("limitationType"))||!"OTHER:NIGHT_OPS".equals(values.get(1).get("operation")))throw new AssertionError("restrictions JSON parsing failed");if(!"12.5".equals(values.get(0).get("aircraftWeight"))||!"0".equals(values.get(0).get("pprValue")))throw new AssertionError("numeric restriction fields were not parsed");System.out.println("JSON restrictions tests passed");}
}
