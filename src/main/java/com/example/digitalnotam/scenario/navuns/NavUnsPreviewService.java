package com.example.digitalnotam.scenario.navuns;

import com.example.digitalnotam.baseline.BaselineNavaidCatalog;
import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.scenario.common.schedule.EventScheduleSupport;
import java.time.*;
import java.util.*;

public final class NavUnsPreviewService {
    private static final Map<String,DayOfWeek> DAYS=Map.of("MON",DayOfWeek.MONDAY,"TUE",DayOfWeek.TUESDAY,"WED",DayOfWeek.WEDNESDAY,"THU",DayOfWeek.THURSDAY,"FRI",DayOfWeek.FRIDAY,"SAT",DayOfWeek.SATURDAY,"SUN",DayOfWeek.SUNDAY);
    private final BaselineNavaidCatalog catalog;
    private final NavUnsStatusResolver resolver=new NavUnsStatusResolver();
    public NavUnsPreviewService(BaselineNavaidCatalog catalog){this.catalog=catalog;}
    public Preview compose(Notam n,Instant requestedStart,Instant requestedEnd){
        var nav=catalog.require(n.navUnsData().navaidUuid());var affected=catalog.affected(nav,n.navUnsData().impactMode(),n.navUnsData().equipmentUuid());var resolution=resolver.resolve(nav,affected,n.navUnsData());
        Instant eventStart=Instant.parse(n.effectiveStart()),eventEnd=Instant.parse(n.effectiveEnd()),start=max(eventStart,requestedStart),end=min(eventEnd,requestedEnd);if(!end.isAfter(start))throw new IllegalArgumentException("Preview period does not overlap B-C validity");
        List<Span> event=eventSpans(n,start,end);List<Row> rows=new ArrayList<>();rows.add(row(nav.uuid(),display(nav),"NAVAID",nav.baselineStatuses().get(0),resolution.navaidStatus(),event,start,end));
        for(var e:affected){String baseline=e.baselineStatuses().get(0);if("UNKNOWN".equals(baseline))baseline=nav.baselineStatuses().get(0);rows.add(row(e.uuid(),(e.designator()+" "+e.type()).trim(),"EQUIPMENT",baseline,resolution.equipmentStatus(),event,start,end));}
        if("SIGNAL".equals(n.navUnsData().impactMode())){String other="AZIMUTH".equals(n.navUnsData().signalType())?"DISTANCE":"AZIMUTH";rows.add(row(affected.get(0).uuid()+":"+other,other+" SIGNAL","SIGNAL","OPERATIONAL","OPERATIONAL",List.of(),start,end));}
        int shared="ALL_PRIMARY".equals(n.navUnsData().impactMode())?1:catalog.usingAsPrimary(affected.get(0).uuid()).size();
        return new Preview(start,end,List.copyOf(rows),resolution.temporaryNavaidType(),Math.max(1,shared));
    }
    private static Row row(String id,String label,String kind,String baseline,String eventStatus,List<Span> events,Instant start,Instant end){List<Interval> out=new ArrayList<>();Instant cursor=start;for(Span s:events){if(s.start.isAfter(cursor))out.add(new Interval(cursor,s.start,baseline,"BASELINE"));out.add(new Interval(s.start,s.end,eventStatus,"EVENT"));cursor=s.end;}if(cursor.isBefore(end))out.add(new Interval(cursor,end,baseline,"BASELINE"));return new Row(id,label,kind,List.copyOf(out));}
    private static List<Span> eventSpans(Notam n,Instant start,Instant end){
        return EventScheduleSupport.occurrences(n,start,end).stream().map(x->new Span(x.start(),x.end())).toList();
    }
    public String json(Preview p){StringBuilder out=new StringBuilder("{\"viewStart\":\"").append(p.start).append("\",\"viewEnd\":\"").append(p.end).append("\",\"temporaryNavaidType\":\"").append(esc(p.temporaryNavaidType)).append("\",\"eventCount\":").append(p.eventCount).append(",\"rows\":[");for(int i=0;i<p.rows.size();i++){if(i>0)out.append(',');Row r=p.rows.get(i);out.append("{\"id\":\"").append(esc(r.id)).append("\",\"label\":\"").append(esc(r.label)).append("\",\"kind\":\"").append(r.kind).append("\",\"intervals\":[");for(int j=0;j<r.intervals.size();j++){if(j>0)out.append(',');Interval x=r.intervals.get(j);out.append("{\"start\":\"").append(x.start).append("\",\"end\":\"").append(x.end).append("\",\"status\":\"").append(esc(x.status)).append("\",\"source\":\"").append(x.source).append("\"}");}out.append("]}");}return out.append("]}").toString();}
    private static String display(BaselineNavaidCatalog.Navaid n){return(n.designator()+" "+n.type().replace('_','/')+" "+n.name()).trim();}
    private static String esc(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}private static Instant max(Instant a,Instant b){return a.isAfter(b)?a:b;}private static Instant min(Instant a,Instant b){return a.isBefore(b)?a:b;}
    private record Span(Instant start,Instant end){}
    public record Interval(Instant start,Instant end,String status,String source){}
    public record Row(String id,String label,String kind,List<Interval> intervals){}
    public record Preview(Instant start,Instant end,List<Row> rows,String temporaryNavaidType,int eventCount){}
}
