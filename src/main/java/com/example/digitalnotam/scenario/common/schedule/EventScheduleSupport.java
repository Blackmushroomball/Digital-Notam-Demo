package com.example.digitalnotam.scenario.common.schedule;

import com.example.digitalnotam.domain.*;
import org.w3c.dom.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Common absolute-time Digital NOTAM schedule rules.
 * Event based time definitions (SR/SS and relative offsets) are deliberately unsupported.
 */
public final class EventScheduleSupport {
    public static final String AIXM="http://www.aixm.aero/schema/5.1.1";
    private static final Set<String> TYPES=Set.of("CONTINUOUS","DAILY","DATES","WEEKDAYS");
    private static final Set<String> DAYS=Set.of("MON","TUE","WED","THU","FRI","SAT","SUN","ANY");
    private static final DateTimeFormatter AIXM_DATE=DateTimeFormatter.ofPattern("dd-MM");
    private EventScheduleSupport(){}

    public static ScheduleData resolve(Notam n){
        ScheduleData supplied=n.scheduleData();
        if(supplied!=null&&!supplied.entries().isEmpty()||supplied!=null&&"CONTINUOUS".equals(supplied.type()))return supplied;
        if("CONTINUOUS".equals(n.scheduleMode()))return new ScheduleData("CONTINUOUS",List.of(),List.of(),"");
        List<ScheduleEntry> rows=new ArrayList<>();
        if("WEEKDAYS".equals(n.scheduleMode())){
            for(String day:n.scheduleDay().split(","))if(!day.isBlank())rows.add(new ScheduleEntry("","",day.trim(),"",n.scheduleStart(),n.scheduleEnd(),false));
        }else{
            rows.add(new ScheduleEntry("DATES".equals(n.scheduleMode())?n.scheduleStartDate():"","DATES".equals(n.scheduleMode())?n.scheduleEndDate():"","ANY","",n.scheduleStart(),n.scheduleEnd(),false));
        }
        return new ScheduleData(n.scheduleMode(),rows,List.of(),"");
    }

    public static ScheduleData validate(Notam n){
        ScheduleData schedule=resolve(n);String type=schedule.type();
        if(!TYPES.contains(type))throw new IllegalArgumentException("不支持的时间表类型: "+type);
        if("CONTINUOUS".equals(type)){if(!schedule.entries().isEmpty()||!schedule.excludedDates().isEmpty())throw new IllegalArgumentException("连续事件不能包含 Timesheet 或排除日期");return schedule;}
        if(schedule.entries().isEmpty())throw new IllegalArgumentException(type+" 时间表至少需要一个时段");
        Instant b=Instant.parse(n.effectiveStart()),c=Instant.parse(n.effectiveEnd());if(!c.isAfter(b))throw new IllegalArgumentException("事件结束时间必须晚于开始时间");
        Set<String> unique=new HashSet<>();
        for(ScheduleEntry row:schedule.entries()){
            time(row.startTime(),"开始时间");if(!row.endOfDay())time(row.endTime(),"结束时间");
            if(row.endOfDay()&&!row.endTime().isBlank()&&!"00:00".equals(row.endTime()))throw new IllegalArgumentException("当天结束必须编码为次日 00:00");
            if("DAILY".equals(type)){
                if(!row.startDate().isBlank()||!row.endDate().isBlank()||!Set.of("","ANY").contains(row.day()))throw new IllegalArgumentException("Daily 不能混用日期或星期");
            }else if("DATES".equals(type)){
                if(row.startDate().isBlank())throw new IllegalArgumentException("Dates 条目必须填写日期");
                LocalDate start=date(row.startDate()),end=row.endDate().isBlank()?start:date(row.endDate());if(end.isBefore(start))throw new IllegalArgumentException("日期范围结束日期不能早于开始日期");
                if(start.isBefore(b.atZone(ZoneOffset.UTC).toLocalDate())||end.isAfter(c.atZone(ZoneOffset.UTC).toLocalDate()))throw new IllegalArgumentException("日期时间表必须位于事件 B/C 有效期内");
                if(!Set.of("","ANY").contains(row.day()))throw new IllegalArgumentException("Dates 的 day 必须为 ANY");
            }else{
                if(!DAYS.contains(row.day())||"ANY".equals(row.day()))throw new IllegalArgumentException("Weekdays 必须使用 MON-SUN");
                if(!row.startDate().isBlank()||!row.endDate().isBlank())throw new IllegalArgumentException("Weekdays 不能混用日期范围");
            }
            String key=type+"|"+row.startDate()+"|"+row.endDate()+"|"+row.day()+"|"+row.startTime()+"|"+effectiveEnd(row);
            if(!unique.add(key))throw new IllegalArgumentException("时间表包含重复 Timesheet");
        }
        List<ScheduleEntry> rows=schedule.entries();for(int i=0;i<rows.size();i++)for(int j=i+1;j<rows.size();j++)if(sameApplicability(type,rows.get(i),rows.get(j))&&timeOverlap(rows.get(i),rows.get(j)))throw new IllegalArgumentException("Timesheet "+(i+1)+" 与 "+(j+1)+" 的时间段重叠");
        if("DATES".equals(type)&&!schedule.excludedDates().isEmpty())throw new IllegalArgumentException("Dates 时间表不允许排除日期");
        Set<LocalDate> exclusions=new HashSet<>();for(String value:schedule.excludedDates()){LocalDate d=date(value);if(!exclusions.add(d))throw new IllegalArgumentException("排除日期重复: "+value);if(d.isBefore(b.atZone(ZoneOffset.UTC).toLocalDate())||d.isAfter(c.atZone(ZoneOffset.UTC).toLocalDate()))throw new IllegalArgumentException("排除日期必须位于事件 B/C 有效期内");}
        String itemD=formatItemD(schedule);if(itemD.length()>200)throw new IllegalArgumentException("Item D 超过 200 字符（当前 "+itemD.length()+"），请复制草稿并拆分为两个事件");
        return schedule;
    }

    public static List<Element> append(Document d,Element owner,Node before,Notam n,boolean invert){
        ScheduleData schedule=validate(n);if("CONTINUOUS".equals(schedule.type()))return List.of();
        for(Node node=owner.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element e&&Set.of("annotation","specialDateAuthority","operationalStatus","status","activity","levels","usage","extension").contains(e.getLocalName())){before=node;break;}
        List<Element> result=new ArrayList<>();
        for(ScheduleEntry row:schedule.entries())result.add(insert(d,owner,before,row,invert));
        if(!invert)for(String excluded:schedule.excludedDates()){
            LocalDate day=date(excluded);ScheduleEntry row=new ScheduleEntry(day.toString(),day.plusDays(1).toString(),"ANY","ANY","00:00","00:00",false);
            result.add(insert(d,owner,before,row,true));
        }
        if(!invert&&!schedule.note().isBlank())owner.insertBefore(note(d,schedule.note()),before);
        return List.copyOf(result);
    }
    public static void excludeEventFromBaseline(Document d,Element availabilityWrapper,Notam n){
        if("CONTINUOUS".equals(n.scheduleMode()))return;
        NodeList statuses=availabilityWrapper.getElementsByTagNameNS(AIXM,"operationalStatus");if(statuses.getLength()==0)throw new IllegalArgumentException("基线 availability 缺少 operationalStatus");
        Element status=(Element)statuses.item(0),owner=(Element)status.getParentNode();append(d,owner,status,n,true);
    }
    public static void validateScheduleNote(Element owner,Notam n){
        int count=0;for(Node node=owner.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element wrapper&&"annotation".equals(wrapper.getLocalName())){
            NodeList notes=wrapper.getElementsByTagNameNS(AIXM,"Note");if(notes.getLength()==0)continue;Element note=(Element)notes.item(0);String property=text(note,"propertyName");
            if("timeInterval".equals(property)){count++;if(!"REMARK".equals(text(note,"purpose")))throw new IllegalArgumentException("schedule note 必须使用 purpose=REMARK");}
        }
        boolean expected=n.scheduleData()!=null&&!n.scheduleData().note().isBlank();if(expected&&count!=1)throw new IllegalArgumentException("schedule note 必须关联 timeInterval");if(!expected&&count!=0)throw new IllegalArgumentException("未输入 schedule note 时不能生成 timeInterval annotation");
    }

    private static Element insert(Document d,Element owner,Node before,ScheduleEntry input,boolean excluded){
        Element wrapper=e(d,"timeInterval"),sheet=id(d,"Timesheet");wrapper.appendChild(sheet);
        add(d,sheet,"timeReference","UTC");
        LocalDate start=input.startDate().isBlank()?null:date(input.startDate()),end=input.endDate().isBlank()?start:date(input.endDate());
        boolean overnight=input.endOfDay()||LocalTime.parse(input.endTime()).compareTo(LocalTime.parse(input.startTime()))<=0;
        if(start!=null){add(d,sheet,"startDate",start.format(AIXM_DATE));if(input.endOfDay()&&end.equals(start))end=end.plusDays(1);add(d,sheet,"endDate",end.format(AIXM_DATE));}
        String day=input.day().isBlank()?"ANY":input.day();add(d,sheet,"day",day);
        String dayTil=input.dayTil();if(dayTil.isBlank()&&overnight)dayTil="ANY".equals(day)?"ANY":nextDay(day);if(!dayTil.isBlank())add(d,sheet,"dayTil",dayTil);
        add(d,sheet,"startTime",input.startTime());add(d,sheet,"endTime",input.endOfDay()?"00:00":input.endTime());
        add(d,sheet,"daylightSavingAdjust","NO");add(d,sheet,"excluded",excluded?"YES":"NO");owner.insertBefore(wrapper,before);return wrapper;
    }

    public static String formatItemD(Notam n){return formatItemD(validate(n));}
    public static String formatItemD(ScheduleData schedule){
        if("CONTINUOUS".equals(schedule.type()))return "";
        List<String> parts=new ArrayList<>();
        Map<String,List<String>> weekdayGroups=new LinkedHashMap<>();
        for(ScheduleEntry row:schedule.entries()){
            String span=hhmm(row.startTime())+"-"+(row.endOfDay()?"2400":hhmm(row.endTime()));
            if("DAILY".equals(schedule.type()))parts.add("DAILY "+span);
            else if("DATES".equals(schedule.type())){LocalDate a=date(row.startDate()),b=row.endDate().isBlank()?a:date(row.endDate());parts.add(notamDate(a)+(a.equals(b)?"":"-"+notamDate(b))+" "+span);}
            else weekdayGroups.computeIfAbsent(span,x->new ArrayList<>()).add(row.day());
        }
        weekdayGroups.forEach((span,days)->parts.add(compactDays(days)+" "+span));
        if(!schedule.excludedDates().isEmpty())parts.add("EXC "+String.join(" ",schedule.excludedDates().stream().map(EventScheduleSupport::date).map(EventScheduleSupport::notamDate).toList()));
        return String.join(" ",parts);
    }

    /** Expands the structured schedule into concrete UTC occurrences for previews and baseline composition. */
    public static List<Occurrence> occurrences(Notam n,Instant viewStart,Instant viewEnd){
        ScheduleData schedule=validate(n);Instant eventStart=Instant.parse(n.effectiveStart()),eventEnd=Instant.parse(n.effectiveEnd());
        Instant start=viewStart.isAfter(eventStart)?viewStart:eventStart,end=viewEnd.isBefore(eventEnd)?viewEnd:eventEnd;if(!end.isAfter(start))return List.of();
        if("CONTINUOUS".equals(schedule.type()))return List.of(new Occurrence(start,end));
        Set<LocalDate> excluded=new HashSet<>(schedule.excludedDates().stream().map(EventScheduleSupport::date).toList());List<Occurrence> out=new ArrayList<>();
        for(ScheduleEntry row:schedule.entries()){
            LocalDate from,to;
            if("DATES".equals(schedule.type())){from=date(row.startDate());to=row.endDate().isBlank()?from:date(row.endDate());}
            else{from=start.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);to=end.atZone(ZoneOffset.UTC).toLocalDate();}
            for(LocalDate day=from;!day.isAfter(to);day=day.plusDays(1)){
                if(excluded.contains(day))continue;
                if("WEEKDAYS".equals(schedule.type())&&!row.day().equals(day.getDayOfWeek().name().substring(0,3)))continue;
                LocalTime a=LocalTime.parse(row.startTime()),b=row.endOfDay()?LocalTime.MIDNIGHT:LocalTime.parse(row.endTime());
                Instant x=day.atTime(a).toInstant(ZoneOffset.UTC),y=(row.endOfDay()||!b.isAfter(a)?day.plusDays(1):day).atTime(b).toInstant(ZoneOffset.UTC);
                x=x.isAfter(start)?x:start;y=y.isBefore(end)?y:end;if(y.isAfter(x))out.add(new Occurrence(x,y));
            }
        }
        out.sort(Comparator.comparing(Occurrence::start));List<Occurrence> merged=new ArrayList<>();for(Occurrence x:out){if(merged.isEmpty()||x.start().isAfter(merged.get(merged.size()-1).end()))merged.add(x);else{Occurrence p=merged.remove(merged.size()-1);merged.add(new Occurrence(p.start(),x.end().isAfter(p.end())?x.end():p.end()));}}return List.copyOf(merged);
    }
    public record Occurrence(Instant start,Instant end){}

    private static Element note(Document d,String value){Element w=e(d,"annotation"),n=id(d,"Note");w.appendChild(n);add(d,n,"propertyName","timeInterval");add(d,n,"purpose","REMARK");Element tw=e(d,"translatedNote"),l=id(d,"LinguisticNote"),text=add(d,l,"note",value);text.setAttribute("lang","ENG");tw.appendChild(l);n.appendChild(tw);return w;}
    private static Element e(Document d,String local){return d.createElementNS(AIXM,"aixm:"+local);}
    private static Element id(Document d,String local){Element e=e(d,local);e.setAttributeNS("http://www.opengis.net/gml/3.2","gml:id","id_"+UUID.randomUUID());return e;}
    private static Element add(Document d,Element p,String local,String value){Element e=e(d,local);e.setTextContent(value);p.appendChild(e);return e;}
    private static String text(Element parent,String local){NodeList values=parent.getElementsByTagNameNS(AIXM,local);return values.getLength()==0?"":values.item(0).getTextContent().trim();}
    private static void time(String value,String label){if(value==null||!value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))throw new IllegalArgumentException(label+"必须为明确的 HH:mm UTC，不支持 SR/SS 或相对时间");}
    private static LocalDate date(String value){return LocalDate.parse(value);}
    private static String effectiveEnd(ScheduleEntry row){return row.endOfDay()?"24:00":row.endTime();}
    private static boolean sameApplicability(String type,ScheduleEntry a,ScheduleEntry b){
        if("DAILY".equals(type))return true;if("WEEKDAYS".equals(type))return a.day().equals(b.day());
        LocalDate a1=date(a.startDate()),a2=a.endDate().isBlank()?a1:date(a.endDate()),b1=date(b.startDate()),b2=b.endDate().isBlank()?b1:date(b.endDate());return !a2.isBefore(b1)&&!b2.isBefore(a1);
    }
    private static boolean timeOverlap(ScheduleEntry a,ScheduleEntry b){int as=minutes(a.startTime()),ae=endMinutes(a),bs=minutes(b.startTime()),be=endMinutes(b);for(int shift:new int[]{-1440,0,1440})if(as<be+shift&&bs+shift<ae)return true;return false;}
    private static int minutes(String value){LocalTime t=LocalTime.parse(value);return t.getHour()*60+t.getMinute();}
    private static int endMinutes(ScheduleEntry row){if(row.endOfDay())return 1440;int start=minutes(row.startTime()),end=minutes(row.endTime());return end<=start?end+1440:end;}
    private static String hhmm(String value){return value.replace(":","");}
    private static String notamDate(LocalDate value){return value.format(DateTimeFormatter.ofPattern("dd MMM",Locale.ENGLISH)).toUpperCase(Locale.ENGLISH);}
    private static String nextDay(String day){List<String>d=List.of("MON","TUE","WED","THU","FRI","SAT","SUN");int i=d.indexOf(day);if(i<0)throw new IllegalArgumentException("非法星期: "+day);return d.get((i+1)%7);}
    private static String compactDays(List<String> values){List<String> order=List.of("MON","TUE","WED","THU","FRI","SAT","SUN"),days=values.stream().distinct().sorted(Comparator.comparingInt(order::indexOf)).toList();List<String> out=new ArrayList<>();for(int i=0;i<days.size();){int j=i;while(j+1<days.size()&&order.indexOf(days.get(j+1))==order.indexOf(days.get(j))+1)j++;out.add(j-i>=2?days.get(i)+"-"+days.get(j):j==i?days.get(i):days.get(i)+" "+days.get(j));i=j+1;}return String.join(" ",out);}
}
