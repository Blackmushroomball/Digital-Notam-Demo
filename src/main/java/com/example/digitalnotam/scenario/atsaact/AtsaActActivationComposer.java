package com.example.digitalnotam.scenario.atsaact;

import com.example.digitalnotam.baseline.AirspaceGeometryService;
import com.example.digitalnotam.baseline.BaselineAirspaceCatalog;
import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.scenario.common.schedule.EventScheduleSupport;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Expands the baseline and event schedules into UTC intervals.  This is the
 * common source used by the ATSA.ACT preview and by publication validation.
 */
public final class AtsaActActivationComposer {
    private static final Set<String> WEEKDAYS=Set.of("MON","TUE","WED","THU","FRI","SAT","SUN");
    private static final Map<String,DayOfWeek> DAY=Map.of(
            "MON",DayOfWeek.MONDAY,"TUE",DayOfWeek.TUESDAY,"WED",DayOfWeek.WEDNESDAY,
            "THU",DayOfWeek.THURSDAY,"FRI",DayOfWeek.FRIDAY,"SAT",DayOfWeek.SATURDAY,"SUN",DayOfWeek.SUNDAY);
    private final AirspaceGeometryService geometry=new AirspaceGeometryService();
    private final SpecialDates specialDates=new SpecialDates();

    public Composition compose(Notam n,List<BaselineAirspaceCatalog.Airspace> airspaces,Instant requestedStart,Instant requestedEnd){
        Instant eventStart=Instant.parse(n.effectiveStart()),eventEnd=Instant.parse(n.effectiveEnd());
        if(!eventEnd.isAfter(eventStart))throw new IllegalArgumentException("ATSA.ACT end time must be after start time");
        Instant start=max(eventStart,requestedStart==null?eventStart:requestedStart);
        Instant end=min(eventEnd,requestedEnd==null?eventEnd:requestedEnd);
        if(!end.isAfter(start))throw new IllegalArgumentException("Preview period does not overlap B-C validity");
        List<AirspaceState> states=new ArrayList<>();List<Issue> issues=new ArrayList<>();
        for(var airspace:airspaces)states.add(composeAirspace(n,airspace,start,end,issues));
        String hash=hash(n,states);
        return new Composition(start,end,List.copyOf(states),List.copyOf(issues),hash,
                issues.stream().noneMatch(Issue::blocking));
    }

    public Composition composeForPublication(Notam n,List<BaselineAirspaceCatalog.Airspace> airspaces){
        Composition result=compose(n,airspaces,Instant.parse(n.effectiveStart()),Instant.parse(n.effectiveEnd()));
        result.issues().stream().filter(Issue::blocking).findFirst().ifPresent(i->{throw new IllegalArgumentException(i.message());});
        return result;
    }

    private AirspaceState composeAirspace(Notam n,BaselineAirspaceCatalog.Airspace airspace,Instant start,Instant end,List<Issue> issues){
        var vertical=geometry.calculate(List.of(airspace));
        List<Raw> baseline=baseline(airspace,start,end,issues);
        List<Raw> event=event(n,start,end);
        TreeSet<Instant> boundaries=new TreeSet<>(List.of(start,end));
        baseline.forEach(x->{boundaries.add(x.start);boundaries.add(x.end);});
        event.forEach(x->{boundaries.add(x.start);boundaries.add(x.end);});
        List<Instant> points=new ArrayList<>(boundaries);List<Interval> output=new ArrayList<>();
        for(int i=0;i+1<points.size();i++){
            Instant a=points.get(i),b=points.get(i+1);if(!b.isAfter(a))continue;
            List<Raw> eventAt=covering(event,a),baseAt=covering(baseline,a);
            String source,status;boolean conflict=false;
            if(!eventAt.isEmpty()){source="EVENT";status=n.activationStatus();}
            else if(baseAt.isEmpty()){source="GAP";status="UNKNOWN";issues.add(new Issue("GAP",airspace.uuid(),a,b,"No resolvable baseline activation state",false));}
            else{
                source="BASELINE";Set<String> values=new LinkedHashSet<>();baseAt.forEach(x->values.add(x.status));
                conflict=values.size()>1;status=String.join("/",values);
                if(conflict)issues.add(new Issue("CONFLICT",airspace.uuid(),a,b,"Overlapping baseline activation states: "+status,true));
            }
            addMerged(output,new Interval(a,b,status,source,vertical.minimumFl(),vertical.maximumFl(),conflict));
        }
        String label=(airspace.designator()+" "+airspace.name()).trim();
        return new AirspaceState(airspace.uuid(),label,vertical.minimumFl(),vertical.maximumFl(),List.copyOf(output));
    }

    private List<Raw> baseline(BaselineAirspaceCatalog.Airspace airspace,Instant start,Instant end,List<Issue> issues){
        List<Raw> result=new ArrayList<>();
        for(Element wrapper:direct(airspace.baselineSlice(),"activation")){
            Element activation=first(wrapper,"AirspaceActivation");if(activation==null)continue;
            String status=text(activation,"status");if(status.isBlank())status="UNKNOWN";
            List<Raw> included=new ArrayList<>(),excluded=new ArrayList<>();
            List<Element> sheets=descendants(activation,"Timesheet");
            if(sheets.isEmpty())included.add(new Raw(start,end,status));
            for(Element sheet:sheets){
                String reference=text(sheet,"timeReference");
                if(!reference.isBlank()&&!"UTC".equals(reference)){
                    issues.add(new Issue("UNSUPPORTED_TIME_REFERENCE",airspace.uuid(),start,end,"Baseline timeReference "+reference+" is not supported",true));continue;
                }
                List<Raw> expanded=expandSheet(sheet,status,start,end,airspace.uuid(),issues);
                ("YES".equals(text(sheet,"excluded"))?excluded:included).addAll(expanded);
            }
            List<Raw> activationResult=included;
            for(Raw cut:excluded)activationResult=subtract(activationResult,cut);
            result.addAll(activationResult);
        }
        return result;
    }

    private List<Raw> expandSheet(Element sheet,String status,Instant start,Instant end,String uuid,List<Issue> issues){
        String day=text(sheet,"day"),dayTil=text(sheet,"dayTil");
        if(day.isBlank())day="ANY";Set<DayOfWeek> selected=new LinkedHashSet<>();
        if("ANY".equals(day))selected.addAll(Arrays.asList(DayOfWeek.values()));
        else if(WEEKDAYS.contains(day)){
            DayOfWeek from=DAY.get(day),to=DAY.get(dayTil);
            selected.add(from);while(to!=null&&!selected.contains(to)){from=from.plus(1);selected.add(from);}
        }else if(Set.of("WORK_DAY","HOL","AFT_WORK_DAY","AFT_HOL").contains(day)){
            // These codes are resolved from Donlon_SpecialDate.xml; they are not
            // approximated as weekdays/weekends.
        }else{
            issues.add(new Issue("UNRESOLVED_CALENDAR_CODE",uuid,start,end,
                    "Baseline calendar code "+day+(dayTil.isBlank()?"":"-"+dayTil)+" requires a SpecialDate calendar",true));
            return List.of();
        }
        LocalTime from=time(text(sheet,"startTime"),LocalTime.MIN),to=time(text(sheet,"endTime"),LocalTime.MIDNIGHT);
        LocalDate first=start.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1),last=end.atZone(ZoneOffset.UTC).toLocalDate();
        MonthDay minDate=monthDay(text(sheet,"startDate")),maxDate=monthDay(text(sheet,"endDate"));
        List<Raw> result=new ArrayList<>();
        for(LocalDate date=first;!date.isAfter(last);date=date.plusDays(1)){
            boolean selectedDate=selected.contains(date.getDayOfWeek());
            if(selected.isEmpty())selectedDate=matchesSpecialRange(date,day,dayTil);
            if(!selectedDate||!inSeason(date,minDate,maxDate))continue;
            Instant a=date.atTime(from).toInstant(ZoneOffset.UTC),b=(to.isAfter(from)?date:date.plusDays(1)).atTime(to).toInstant(ZoneOffset.UTC);
            a=max(a,start);b=min(b,end);if(b.isAfter(a))result.add(new Raw(a,b,status));
        }
        return result;
    }

    private List<Raw> event(Notam n,Instant start,Instant end){
        return EventScheduleSupport.occurrences(n,start,end).stream().map(x->new Raw(x.start(),x.end(),n.activationStatus())).toList();
    }

    private static List<Raw> subtract(List<Raw> input,Raw cut){List<Raw> out=new ArrayList<>();for(Raw x:input){if(!x.end.isAfter(cut.start)||!cut.end.isAfter(x.start)){out.add(x);continue;}if(cut.start.isAfter(x.start))out.add(new Raw(x.start,min(x.end,cut.start),x.status));if(x.end.isAfter(cut.end))out.add(new Raw(max(x.start,cut.end),x.end,x.status));}return out;}
    private static List<Raw> covering(List<Raw> values,Instant instant){return values.stream().filter(x->!instant.isBefore(x.start)&&instant.isBefore(x.end)).toList();}
    private static void addMerged(List<Interval> out,Interval value){if(!out.isEmpty()){Interval last=out.get(out.size()-1);if(last.end.equals(value.start)&&last.status.equals(value.status)&&last.source.equals(value.source)&&last.conflict==value.conflict){out.set(out.size()-1,new Interval(last.start,value.end,last.status,last.source,last.lowerFl,last.upperFl,last.conflict));return;}}out.add(value);}
    private static LocalTime time(String value,LocalTime fallback){if(value==null||value.isBlank())return fallback;if("24:00".equals(value))return LocalTime.MIDNIGHT;return LocalTime.parse(value.length()==5?value:value.substring(0,5));}
    private static MonthDay monthDay(String value){if(value==null||value.isBlank())return null;return MonthDay.parse(value,DateTimeFormatter.ofPattern("dd-MM"));}
    private static boolean inSeason(LocalDate d,MonthDay from,MonthDay to){if(from==null||to==null)return true;MonthDay x=MonthDay.from(d);return from.compareTo(to)<=0?x.compareTo(from)>=0&&x.compareTo(to)<=0:x.compareTo(from)>=0||x.compareTo(to)<=0;}
    private boolean matchesSpecialRange(LocalDate date,String from,String to){
        if(matchesSpecial(date,from))return true;
        if(to==null||to.isBlank())return false;
        if(matchesSpecial(date,to))return true;
        // The only special ranges in the supplied Airspace baseline are
        // WORK_DAY-AFT_WORK_DAY and HOL-AFT_HOL. Their endpoints describe the
        // named day and the immediately following day.
        return ("AFT_WORK_DAY".equals(to)&&matchesSpecial(date.minusDays(1),"WORK_DAY"))
                ||("AFT_HOL".equals(to)&&matchesSpecial(date.minusDays(1),"HOL"));
    }
    private boolean matchesSpecial(LocalDate date,String code){return switch(code){case"HOL"->specialDates.isHoliday(date);case"WORK_DAY"->date.getDayOfWeek().getValue()<=5&&!specialDates.isHoliday(date);case"AFT_HOL"->specialDates.isHoliday(date.minusDays(1));case"AFT_WORK_DAY"->date.minusDays(1).getDayOfWeek().getValue()<=5&&!specialDates.isHoliday(date.minusDays(1));default->false;};}
    private static List<String> csv(String value){return value==null?List.of():Arrays.stream(value.split(",")).map(String::trim).filter(x->!x.isBlank()).toList();}
    private static List<Element> direct(Element p,String local){List<Element> out=new ArrayList<>();for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&local.equals(e.getLocalName()))out.add(e);return out;}
    private static List<Element> descendants(Element p,String local){var nodes=p.getElementsByTagNameNS("*",local);List<Element> out=new ArrayList<>();for(int i=0;i<nodes.getLength();i++)out.add((Element)nodes.item(i));return out;}
    private static Element first(Element p,String local){var n=p.getElementsByTagNameNS("*",local);return n.getLength()==0?null:(Element)n.item(0);}
    private static String text(Element p,String local){Element e=first(p,local);return e==null?"":e.getTextContent().trim();}
    private static Instant max(Instant a,Instant b){return a.isAfter(b)?a:b;}private static Instant min(Instant a,Instant b){return a.isBefore(b)?a:b;}
    private static String hash(Notam n,List<AirspaceState> states){try{StringBuilder value=new StringBuilder(n.activationStatus()).append('|').append(n.scheduleMode());for(var s:states)for(var i:s.intervals)value.append('|').append(s.uuid).append('|').append(i);return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}

    private record Raw(Instant start,Instant end,String status){}
    public record Interval(Instant start,Instant end,String status,String source,int lowerFl,int upperFl,boolean conflict){}
    public record AirspaceState(String uuid,String label,int lowerFl,int upperFl,List<Interval> intervals){}
    public record Issue(String code,String airspaceUuid,Instant start,Instant end,String message,boolean blocking){}
    public record Composition(Instant viewStart,Instant viewEnd,List<AirspaceState> airspaces,List<Issue> issues,String compositionHash,boolean publishable){}

    private static final class SpecialDates{
        private static final Path FILE=Path.of("data","virtual data","Donlon_2025","Donlon","DONLON original files","Common","Donlon_SpecialDate.xml");
        private final Set<MonthDay> recurring=new HashSet<>();private final Set<LocalDate> exact=new HashSet<>();
        SpecialDates(){try{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");var d=f.newDocumentBuilder().parse(FILE.toFile());var slices=d.getElementsByTagNameNS("*","SpecialDateTimeSlice");for(int i=0;i<slices.getLength();i++){Element s=(Element)slices.item(i);if(!"HOL".equals(text(s,"type")))continue;MonthDay md=MonthDay.parse(text(s,"dateDay"),DateTimeFormatter.ofPattern("dd-MM"));String year=text(s,"dateYear");if(year.isBlank())recurring.add(md);else exact.add(md.atYear(Integer.parseInt(year)));}}catch(Exception e){throw new IllegalStateException("Unable to read SpecialDate baseline: "+e.getMessage(),e);}}
        boolean isHoliday(LocalDate date){return exact.contains(date)||recurring.contains(MonthDay.from(date));}
    }
}
