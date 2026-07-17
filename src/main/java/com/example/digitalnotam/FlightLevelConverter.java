package com.example.digitalnotam;

final class FlightLevelConverter {
    private FlightLevelConverter() {}

    static String toFl(String meters, boolean upper) {
        if (meters == null || meters.isBlank()) return upper ? "999" : "000";
        final double value;
        try { value = Double.parseDouble(meters); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("飞行高度必须为公制米数值"); }
        if (!Double.isFinite(value) || value < 0 || value > 16000)
            throw new IllegalArgumentException("飞行高度必须在 0 至 16000 米之间（换算表范围）");
        long fl = Math.round(value * 3.28 / 100.0);
        if (fl > 998) throw new IllegalArgumentException("换算后的飞行高度层超出 FL998");
        return "%03d".formatted(fl);
    }
}
