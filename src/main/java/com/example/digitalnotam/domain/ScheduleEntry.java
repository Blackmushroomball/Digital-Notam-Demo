package com.example.digitalnotam.domain;

/** One absolute-time Digital NOTAM Timesheet input row. */
public record ScheduleEntry(
        String startDate,
        String endDate,
        String day,
        String dayTil,
        String startTime,
        String endTime,
        boolean endOfDay) {
    public ScheduleEntry {
        startDate=value(startDate);endDate=value(endDate);day=value(day);dayTil=value(dayTil);
        startTime=value(startTime);endTime=value(endTime);
    }
    private static String value(String value){return value==null?"":value.trim();}
}
