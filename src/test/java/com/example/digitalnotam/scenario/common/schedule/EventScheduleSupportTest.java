package com.example.digitalnotam.scenario.common.schedule;

import com.example.digitalnotam.domain.*;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.time.Instant;
import java.util.List;

public final class EventScheduleSupportTest {
    public static void main(String[]args)throws Exception{
        String now=Instant.now().toString();
        Notam base=new Notam("schedule","A0990/26","AD.CLS","schedule","","AIRPORT_HELIPORT","","","","","","",
                "2026-08-01T00:00:00Z","2026-08-08T00:00:00Z","52","32","5","N","E","QFALC","IV","NBO","A","","",
                "WEEKDAYS","MON","22:00","07:00","DRAFT",now,"");
        ScheduleData data=new ScheduleData("WEEKDAYS",List.of(
                new ScheduleEntry("","","MON","","22:00","07:00",false),
                new ScheduleEntry("","","FRI","","09:00","17:00",false)),List.of("2026-08-03"),"OPERATOR SCHEDULE NOTE");
        Notam n=base.withScheduleData(data);
        Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();Element owner=d.createElementNS(EventScheduleSupport.AIXM,"aixm:AirportHeliportAvailability");d.appendChild(owner);Element status=d.createElementNS(EventScheduleSupport.AIXM,"aixm:operationalStatus");owner.appendChild(status);
        EventScheduleSupport.append(d,owner,status,n,false);
        check(d.getElementsByTagNameNS(EventScheduleSupport.AIXM,"Timesheet").getLength()==3,"two event rows plus one exclusion expected");
        check("TUE".equals(d.getElementsByTagNameNS(EventScheduleSupport.AIXM,"dayTil").item(0).getTextContent()),"overnight weekday must end on next weekday");
        check("YES".equals(d.getElementsByTagNameNS(EventScheduleSupport.AIXM,"excluded").item(2).getTextContent()),"excluded date must be an excluded Timesheet");
        check("timeInterval".equals(d.getElementsByTagNameNS(EventScheduleSupport.AIXM,"propertyName").item(0).getTextContent()),"schedule note propertyName");
        check(EventScheduleSupport.formatItemD(n).contains("MON 2200-0700"),"Item D must contain structured rows");
        check(EventScheduleSupport.occurrences(n,Instant.parse(n.effectiveStart()),Instant.parse(n.effectiveEnd())).stream().noneMatch(x->x.start().toString().startsWith("2026-08-03")),"excluded date must be removed from occurrences");
        System.out.println("Common event schedule tests passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
