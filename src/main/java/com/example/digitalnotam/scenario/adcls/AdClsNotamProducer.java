package com.example.digitalnotam.scenario.adcls;

import com.example.digitalnotam.baseline.BaselineAirportHeliportCatalog;
import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.xml.CommonDigitalNotamBuilder;

import org.w3c.dom.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class AdClsNotamProducer {
    private static final Map<String,String[]> Q_DEFAULTS=Map.of("QFALC",new String[]{"IV","NBO"},"QFPLC",new String[]{"IV","NBO"});
    private final BaselineAirportHeliportCatalog catalog=new BaselineAirportHeliportCatalog();

    public NotamFields produce(Document d,Notam n)throws Exception{
        var b=catalog.find(n.airport(),Instant.parse(n.effectiveStart()),Instant.parse(n.effectiveEnd()));
        String automaticQ="HP".equals(b.type())?"QFPLC":"QFALC";String[] defaults=Q_DEFAULTS.get(automaticQ);
        boolean overridden=!n.qOverrideReason().isBlank();
        if(overridden&&(n.qOverrideOperator().isBlank()||n.qOverrideAt().isBlank()))throw new IllegalArgumentException("人工修正 Q 行必须记录修改原因、操作员和修改时间");
        String q=overridden?n.qCode():automaticQ,traffic=overridden?n.traffic():defaults[0],purpose=overridden?n.purpose():defaults[1],scope=overridden?n.scope():"A";
        String coordinates=overridden?formatCoordinates(n.latitude(),n.latitudeHemisphere(),n.longitude(),n.longitudeHemisphere()):formatCoordinates(b.latitude(),hemisphere(b.latitude(),true),b.longitude(),hemisphere(b.longitude(),false));
        String radius=overridden?padRadius(n.radiusNm()):"005",lower=overridden?n.minimumFl():"000",upper=overridden?n.maximumFl():"999";
        String itemA=b.locationIndicatorIcao().isBlank()?b.designator().substring(0,2)+"XX":b.locationIndicatorIcao();
        Element eventSlice=CommonDigitalNotamBuilder.one(d,CommonDigitalNotamBuilder.EVENT,"EventTimeSlice");
        String begin=BaselineAirportHeliportCatalog.text(eventSlice,"beginPosition"),end=BaselineAirportHeliportCatalog.text(eventSlice,"endPosition");
        String itemB=notamTime(begin),itemC=endTime(end),itemD=schedule(d),itemE=itemE(d,b);
        for(String[] field:new String[][]{{"affectedFIR",b.firDesignator()},{"selectionCode",q},{"traffic",traffic},{"purpose",purpose},{"scope",scope},{"minimumFL",lower},{"maximumFL",upper},{"coordinates",coordinates},{"radius",radius},{"location",itemA},{"effectiveStart",itemB},{"effectiveEnd",itemC},{"estimatedEnd","NO"},{"permanent","NO"},{"text",itemE}})CommonDigitalNotamBuilder.setNotam(d,field[0],field[1]);
        return new NotamFields(b.firDesignator(),q,traffic,purpose,scope,lower,upper,coordinates,radius,itemA,itemB,itemC,itemD,itemE,"","");
    }
    private static String itemE(Document d,BaselineAirportHeliportCatalog.AirportBaseline b){String type=switch(b.type()){case "HP"->"HELIPORT";case "LS","OTHER"->"LANDING SITE";default->"AD";};StringBuilder out=new StringBuilder(type);if(b.locationIndicatorIcao().isBlank())out.append(' ').append(b.name());out.append(" CLOSED");Element closed=closed(d);List<String> notes=new ArrayList<>();String reason="";for(Element annotation:BaselineAirportHeliportCatalog.directChildren(closed,"annotation")){Element note=BaselineAirportHeliportCatalog.first(annotation,"Note");String value=BaselineAirportHeliportCatalog.text(note,"note");if("operationalStatus".equals(BaselineAirportHeliportCatalog.text(note,"propertyName")))reason=value;else if(!value.isBlank())notes.add(value);}if(!reason.isBlank())out.append(" DUE TO ").append(reason);out.append('.');for(String note:notes)out.append(System.lineSeparator()).append(note).append('.');return out.toString();}
    private static String schedule(Document d){Element closed=closed(d);List<Element> intervals=BaselineAirportHeliportCatalog.directChildren(closed,"timeInterval");if(intervals.isEmpty())return "";List<String> parts=new ArrayList<>();for(Element interval:intervals){Element ts=BaselineAirportHeliportCatalog.first(interval,"Timesheet");String day=BaselineAirportHeliportCatalog.text(ts,"day"),start=hhmm(BaselineAirportHeliportCatalog.text(ts,"startTime")),end=hhmmEnd(BaselineAirportHeliportCatalog.text(ts,"endTime"));String startDate=BaselineAirportHeliportCatalog.text(ts,"startDate"),endDate=BaselineAirportHeliportCatalog.text(ts,"endDate");if(!startDate.isBlank())parts.add(date(startDate)+(endDate.isBlank()||endDate.equals(startDate)?"":"-"+date(endDate))+" "+start+"-"+end);else if("ANY".equals(day))parts.add("DAILY "+start+"-"+end);else parts.add(day+" "+start+"-"+end);}return String.join(" ",parts);}
    private static Element closed(Document d){for(Element status:CommonDigitalNotamBuilder.all(d,CommonDigitalNotamBuilder.AIXM,"operationalStatus"))if("CLOSED".equals(status.getTextContent().trim()))return(Element)status.getParentNode();throw new IllegalArgumentException("AD.CLS 缺少 CLOSED availability");}
    private static String notamTime(String iso){return Instant.parse(iso).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyMMddHHmm"));}
    private static String endTime(String iso){ZonedDateTime z=Instant.parse(iso).atZone(ZoneOffset.UTC);if(z.toLocalTime().equals(LocalTime.MIDNIGHT))return z.minusDays(1).format(DateTimeFormatter.ofPattern("yyMMdd"))+"2359";return z.format(DateTimeFormatter.ofPattern("yyMMddHHmm"));}
    private static String date(String value){return MonthDay.parse(value,DateTimeFormatter.ofPattern("dd-MM")).format(DateTimeFormatter.ofPattern("MMM dd",Locale.ENGLISH)).toUpperCase(Locale.ROOT);}
    private static String hhmm(String value){return value.replace(":","");}private static String hhmmEnd(String value){return "00:00".equals(value)?"2359":hhmm(value);}
    private static String hemisphere(String value,boolean latitude){return Double.parseDouble(value)<0?(latitude?"S":"W"):(latitude?"N":"E");}
    private static String formatCoordinates(String lat,String latHem,String lon,String lonHem){return coordinate(lat,2,latHem)+coordinate(lon,3,lonHem);}private static String coordinate(String value,int width,String hem){double v=Math.abs(Double.parseDouble(value));int deg=(int)Math.floor(v),min=(int)Math.round((v-deg)*60);if(min==60){deg++;min=0;}return("%0"+width+"d%02d%s").formatted(deg,min,hem);}
    private static String padRadius(String value){return "%03d".formatted(Integer.parseInt(value));}
    public record NotamFields(String fir,String qCode,String traffic,String purpose,String scope,String lower,String upper,String coordinates,String radius,String itemA,String itemB,String itemC,String itemD,String itemE,String itemF,String itemG){}
}
