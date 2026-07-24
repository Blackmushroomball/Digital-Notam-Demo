package com.example.digitalnotam.domain;

import java.util.List;

/** Structured event schedule. SR/SS and relative-time definitions are intentionally outside this version. */
public record ScheduleData(
        String type,
        List<ScheduleEntry> entries,
        List<String> excludedDates,
        String note) {
    public ScheduleData {
        type=type==null?"":type.trim();
        entries=entries==null?List.of():List.copyOf(entries);
        excludedDates=excludedDates==null?List.of():excludedDates.stream().filter(x->x!=null&&!x.isBlank()).map(String::trim).toList();
        note=note==null?"":note.trim();
    }
    public static ScheduleData empty(){return new ScheduleData("",List.of(),List.of(),"");}
}
