package com.example.digitalnotam;

import org.w3c.dom.*;
import org.xml.sax.InputSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.*;
import javax.xml.validation.SchemaFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

final class DigitalNotamPipeline {
    private static final Set<String> SCENARIOS = Set.of("AD.CLS","AD.LIM","RWY.CLS","RWY.LIM","TWY.CLS","TWY.LIM");
    private static final Path SAMPLES = Path.of("data","virtual data","Donlon_2025","Donlon","Digital NOTAM");
    private static final Map<String,String> BLUEPRINT = Map.of(
            "AD.CLS","DN_AD.CLS_1_ad_closed.xml", "AD.LIM","DN_AD.LIM_1_closed_except_for.xml",
            "RWY.CLS","DN_RWY.CLS_1_full_runway_closure.xml", "RWY.LIM","DN_RWY.LIM_1_closed_except_for_takeoff.xml",
            "TWY.CLS","DN_TWY.CLS_1_single_twy_closure.xml", "TWY.LIM","DN_TWY.LIM_1_closed_except_for.xml");
    private static final Map<String,String> RUNWAY_DIRECTION_BY_ID=Map.of("uuid.5d6513d4-a62a-49e1-9e26-0b8cbf320daf","09R","uuid.ee6019d6-29f7-404d-8cee-b6819f325aed","27L");
    private final Path store = Path.of("data","notams").toAbsolutePath().normalize();

    List<Notam> restorePublished() {
        if (!Files.isDirectory(store)) return List.of();
        List<Notam> restored = new ArrayList<>();
        try (var files = Files.list(store)) {
            files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .sorted().forEach(path -> {
                        try { restored.add(restorePublished(path)); }
                        catch (Exception e) { System.err.println("Skip invalid NOTAM XML " + path.getFileName() + ": " + e.getMessage()); }
                    });
        } catch (IOException e) {
            System.err.println("Unable to scan data/notams: " + e.getMessage());
        }
        return restored;
    }

    private Notam restorePublished(Path path) throws Exception {
        Document d = parse(Files.readString(path, StandardCharsets.UTF_8));
        if (d.getElementsByTagNameNS("*", "Event").getLength() == 0) return restoreLegacyPublished(path, d);
        validateRules(d);
        String scenario = text(d,"scenario");
        if (!SCENARIOS.contains(scenario)) throw new IllegalArgumentException("不支持场景 " + scenario);
        String series = text(d,"series").toUpperCase(Locale.ROOT);
        String digits = text(d,"number");
        String year = text(d,"year");
        if (!series.matches("[ACD]") || !digits.matches("\\d{1,4}") || !year.matches("\\d{2}|\\d{4}"))
            throw new IllegalArgumentException("通告编号字段无效");
        String number = series + String.format("%04d", Integer.parseInt(digits)) + "/" + year.substring(year.length()-2);
        String start = firstIso(d,"beginPosition");
        String end = firstIso(d,"endPosition");
        Element schedule = eventSchedule(d);
        String scheduleMode=schedule==null?"CONTINUOUS":"SCHEDULED";
        String scheduleDay = scheduleDays(lastEventAvailability(d));
        String scheduleStart = childText(schedule,"startTime",timeOf(start));
        String scheduleEnd = childText(schedule,"endTime",timeOf(end));
        String coordinates=text(d,"coordinates"); String[] coordinateParts=parseCoordinates(coordinates);
        String minimum=text(d,"minimumFL"), maximum=text(d,"maximumFL");
        String modified = validInstantOr(text(d,"issued"),Files.getLastModifiedTime(path).toInstant().toString());
        String featureType = scenario.startsWith("AD.") ? "AIRPORT_HELIPORT" : scenario.startsWith("RWY.") ? "RUNWAY" : "TAXIWAY";
        return new Notam(UUID.nameUUIDFromBytes(number.getBytes(StandardCharsets.UTF_8)).toString(), number, scenario,
                defaultIfBlank(text(d,"name"), number), defaultIfBlank(text(d,"location"), "EADD"), featureType,
                text(d,"text"),runwayFromTitle(d),taxiwaysFromText(text(d,"text")),"","","", start, end, coordinateParts[0], coordinateParts[1], defaultIfBlank(text(d,"radius"),"0"), coordinateParts[2], coordinateParts[3],
                defaultIfBlank(text(d,"selectionCode"),"QXXXX"), defaultIfBlank(text(d,"traffic"),"IV"), defaultIfBlank(text(d,"purpose"),"NBO"), defaultIfBlank(text(d,"scope"),"A"),
                metersOfFl(minimum,false), metersOfFl(maximum,true), scheduleMode,scheduleDay, scheduleStart, scheduleEnd,
                "PUBLISHED", modified, modified);
    }

    private Notam restoreLegacyPublished(Path path, Document d) throws Exception {
        String file = path.getFileName().toString();
        var matcher = java.util.regex.Pattern.compile("^([ACD])(\\d{4})_(\\d{2})\\.xml$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(file);
        if (!matcher.matches()) throw new IllegalArgumentException("旧格式XML文件名不符合 SNNNN_YY.xml");
        String number = matcher.group(1).toUpperCase(Locale.ROOT) + matcher.group(2) + "/" + matcher.group(3);
        String feature;
        String scenario;
        if (d.getElementsByTagNameNS("*", "AirportHeliport").getLength() > 0) { feature="AIRPORT_HELIPORT"; scenario="AD.CLS"; }
        else if (d.getElementsByTagNameNS("*", "Runway").getLength() > 0) { feature="RUNWAY"; scenario="RWY.CLS"; }
        else if (d.getElementsByTagNameNS("*", "Taxiway").getLength() > 0) { feature="TAXIWAY"; scenario="TWY.CLS"; }
        else throw new IllegalArgumentException("无法识别旧格式航空要素");
        String start=firstIso(d,"beginPosition"), end=firstIso(d,"endPosition");
        String note=text(d,"note"), title=number, condition=note;
        int separator=note.indexOf(" - ");
        if(separator>=0){title=note.substring(0,separator).trim();condition=note.substring(separator+3).trim();}
        String airport=defaultIfBlank(text(d,"designator"),"EADD");
        String modified=Files.getLastModifiedTime(path).toInstant().toString();
        return new Notam(UUID.nameUUIDFromBytes(number.getBytes(StandardCharsets.UTF_8)).toString(),number,scenario,title,airport,feature,
                condition,"","","","","",start,end,"","","","N","E","QXXXX","IV","NBO","A","","","CONTINUOUS","ANY",timeOf(start),timeOf(end),"PUBLISHED",modified,modified);
    }

    String publish(Notam n) throws Exception {
        if (!SCENARIOS.contains(n.scenario())) throw new IllegalArgumentException("不支持的场景: " + n.scenario());
        Document d = parse(Files.readString(SAMPLES.resolve(BLUEPRINT.get(n.scenario())), StandardCharsets.UTF_8));
        String[] number = n.number().split("[/]");
        setFirst(d,"series", number[0].substring(0,1)); setFirst(d,"number", number[0].substring(1));
        setFirst(d,"year", "20" + number[1]); setFirst(d,"scenario", n.scenario());
        validatePublicationTime(n);
        setNotamField(d,"issued",n.publishedAt());
        setFirst(d,"name", n.title()); setFirst(d,"text", n.condition());
        replaceAll(d,"beginPosition", n.effectiveStart()); replaceAll(d,"endPosition", n.effectiveEnd());
        setFirst(d,"effectiveStart", notamDate(n.effectiveStart())); setFirst(d,"effectiveEnd", notamDate(n.effectiveEnd()));
        setNotamField(d,"affectedFIR","EAAD");
        setNotamField(d,"selectionCode",n.qCode()); setNotamField(d,"traffic",n.traffic()); setNotamField(d,"purpose",n.purpose()); setNotamField(d,"scope",n.scope());
        setNotamField(d,"minimumFL",n.minimumFl()); setNotamField(d,"maximumFL",n.maximumFl());
        setNotamField(d,"coordinates",formatCoordinates(n.latitude(),n.latitudeHemisphere(),n.longitude(),n.longitudeHemisphere()));
        setNotamField(d,"radius",String.format("%03d",Integer.parseInt(n.radiusNm())));
        if("RWY.CLS".equals(n.scenario()))applyRunwayClosure(d,n);else if("TWY.CLS".equals(n.scenario()))applyTaxiwayClosure(d,n);else if("SCHEDULED".equals(n.scheduleMode()))applySchedule(d,n.scheduleDay(),n.scheduleStart(),n.scheduleEnd());else removeGenericEventSchedule(d);
        replaceHeaderComments(d, n.scenario());
        validateRules(d); validateXsd(d); String xml = serialize(d); save(n.number(), xml); return xml;
    }

    Imported importXml(String xml) throws Exception {
        Document d=parse(xml); validateRules(d); validateXsd(d);
        String scenario=text(d,"scenario"), series=text(d,"series"), num=text(d,"number"), year=text(d,"year");
        if(!SCENARIOS.contains(scenario)) throw new IllegalArgumentException("首期不支持场景: "+scenario);
        String number=series+String.format("%04d",Integer.parseInt(num))+"/"+year.substring(year.length()-2);
        replaceHeaderComments(d, scenario);
        save(number, serialize(d));
        String[] c=parseCoordinates(text(d,"coordinates")); Element schedule=eventSchedule(d);String scheduleMode=schedule==null?"CONTINUOUS":"SCHEDULED";
        return new Imported(number,scenario,text(d,"name"),text(d,"location"),text(d,"text"),iso(text(d,"effectiveStart")),iso(text(d,"effectiveEnd")),validInstantOr(text(d,"issued"),Instant.now().toString()),
                c[0],c[1],defaultIfBlank(text(d,"radius"),"0"),c[2],c[3],text(d,"selectionCode"),text(d,"traffic"),text(d,"purpose"),text(d,"scope"),
                metersOfFl(text(d,"minimumFL"),false),metersOfFl(text(d,"maximumFL"),true),scheduleMode,scheduleDays(lastEventAvailability(d)),childText(schedule,"startTime","00:00"),childText(schedule,"endTime","23:59"));
    }

    String transform(String xml, String scenario) throws Exception {
        TransformerFactory f=TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl",getClass().getClassLoader());
        f.setURIResolver(new DonlonResolver());
        Path xsl=Path.of("utils","NOTAM-Production-Templates","xslt-scenarios",scenario+"_CNOTAM_text_generation.xslt").toAbsolutePath();
        Transformer t=f.newTransformer(new StreamSource(xsl.toFile())); StringWriter out=new StringWriter();
        t.transform(new StreamSource(new StringReader(xml)),new StreamResult(out));
        Document d=parse(xml); String qCode=text(d,"selectionCode"), minimum=text(d,"minimumFL"), maximum=text(d,"maximumFL");
        String q="Q) "+String.join("/",text(d,"affectedFIR"),qCode,text(d,"traffic"),text(d,"purpose"),text(d,"scope"),minimum,maximum,text(d,"coordinates")+text(d,"radius"));
        String result=out.toString().replaceFirst("(?m)^Q\\) [^\\r\\n]*",java.util.regex.Matcher.quoteReplacement(q));
        String dItem=scheduleItem(d);
        if(result.matches("(?s).*(?m)^D\\).*"))result=result.replaceFirst("(?m)^D\\) [^\\r\\n]*",java.util.regex.Matcher.quoteReplacement("D) "+dItem));
        else if(!"-".equals(dItem))result=result.replaceFirst("(?m)^E\\)",java.util.regex.Matcher.quoteReplacement("D) "+dItem+System.lineSeparator()+"E)"));
        String eItem=text(d,"text");
        if(result.matches("(?s).*(?m)^E\\).*"))result=result.replaceFirst("(?m)^E\\) [^\\r\\n]*",java.util.regex.Matcher.quoteReplacement("E) "+eItem));
        result=result.replaceAll("(?m)^[FG]\\) [^\\r\\n]*(?:\\R|$)","").stripTrailing();
        boolean warningOrRestriction=qCode.startsWith("QW")||qCode.startsWith("QR");
        String fItem=warningOrRestriction?"FL"+minimum:"-", gItem=warningOrRestriction?"FL"+maximum:"-";
        return result+System.lineSeparator()+"F) "+fItem+System.lineSeparator()+"G) "+gItem;
    }

    private void save(String number,String xml)throws IOException{Files.createDirectories(store);Path p=store.resolve(number.replace('/','_')+".xml");Files.writeString(p,xml,StandardCharsets.UTF_8);}
    private static Document parse(String xml)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));}
    private static void validateRules(Document d){if(!"AIXMBasicMessage".equals(d.getDocumentElement().getLocalName()))throw new IllegalArgumentException("根元素必须为 AIXMBasicMessage");for(String x:List.of("Event","EventTimeSlice","scenario","NOTAM","series","number","year"))if(d.getElementsByTagNameNS("*",x).getLength()==0)throw new IllegalArgumentException("缺少必需元素: "+x);Element availability=lastEventAvailability(d);if(availability!=null)for(Node p=availability.getFirstChild();p!=null;p=p.getNextSibling())if(p instanceof Element e&&"timeInterval".equals(e.getLocalName())){NodeList sheets=e.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","Timesheet");for(int i=0;i<sheets.getLength();i++){String day=childText((Element)sheets.item(i),"day","");if(Set.of("WORK_DAY","BEF_WORK_DAY","AFT_WORK_DAY","HOL","BEF_HOL","AFT_HOL").contains(day))throw new IllegalArgumentException("Digital NOTAM事件时间表不允许使用含糊日期代码: "+day);}}}
    private static void applySchedule(Document d,String day,String start,String end){
        List<String> days=parseScheduleDays(day);
        if(!start.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")||!end.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))throw new IllegalArgumentException("D项时间必须为HH:mm格式");
        NodeList statuses=d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","operationalStatus");
        Element availability=null;
        for(int i=0;i<statuses.getLength();i++){String v=statuses.item(i).getTextContent();if("CLOSED".equals(v)||"LIMITED".equals(v)||v.startsWith("OTHER:")){Node p=statuses.item(i).getParentNode();if(p instanceof Element e)availability=e;}}
        if(availability==null)throw new IllegalArgumentException("场景XML缺少事件可用性结构");
        replaceTimesheets(d,availability,days,start,end);
    }
    private static void applyRunwayClosure(Document d,Notam n){
        Set<String> selected=new HashSet<>(Arrays.asList(n.selectedRunways().split("/")));List<Element> targets=new ArrayList<>();
        NodeList directions=d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","RunwayDirection");
        for(int i=0;i<directions.getLength();i++){Element direction=(Element)directions.item(i);String designator=RUNWAY_DIRECTION_BY_ID.get(direction.getAttributeNS("http://www.opengis.net/gml/3.2","id"));if(!selected.contains(designator))continue;Element found=null;NodeList states=direction.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","operationalStatus");for(int j=0;j<states.getLength();j++)if("CLOSED".equals(states.item(j).getTextContent().trim()))found=(Element)states.item(j).getParentNode();if(found!=null)targets.add(found);}
        if(targets.size()!=selected.size())throw new IllegalArgumentException("所选跑道方向与RWY.CLS蓝图不匹配");
        for(Element target:targets){if("SCHEDULED".equals(n.scheduleMode()))replaceTimesheets(d,target,parseScheduleDays(n.scheduleDay()),n.scheduleStart(),n.scheduleEnd());else removeDirectTimeIntervals(target);replaceClosureNotes(target,n.reason(),n.remarks());}
    }
    private static void applyTaxiwayClosure(Document d,Notam n)throws Exception{
        Map<String,String> baseline=new BaselineTaxiwayCatalog().identifiers(n.airport());List<String> selected=Arrays.stream(n.selectedTaxiways().split(",")).map(String::trim).filter(s->!s.isBlank()).distinct().toList();if(selected.isEmpty()||!baseline.keySet().containsAll(selected))throw new IllegalArgumentException("所选滑行道与机场基线不匹配");
        Element root=d.getDocumentElement(),templateMember=null;List<Node> remove=new ArrayList<>();for(Node p=root.getFirstChild();p!=null;p=p.getNextSibling())if(p instanceof Element member&&"hasMember".equals(member.getLocalName())){Element feature=firstElementChild(member);if(feature!=null&&("Taxiway".equals(feature.getLocalName())||"TaxiwayElement".equals(feature.getLocalName()))){if("Taxiway".equals(feature.getLocalName())&&templateMember==null)templateMember=member;remove.add(member);}}
        if(templateMember==null)throw new IllegalArgumentException("TWY.CLS蓝图缺少Taxiway结构");Element template=(Element)templateMember.cloneNode(true);for(Node node:remove)root.removeChild(node);
        for(String designator:selected){Element member=(Element)template.cloneNode(true);Element taxiway=firstElementChild(member);regenerateGmlIds(taxiway);String uuid=baseline.get(designator);taxiway.setAttributeNS("http://www.opengis.net/gml/3.2","gml:id","uuid."+uuid);NodeList identifiers=taxiway.getElementsByTagNameNS("http://www.opengis.net/gml/3.2","identifier");if(identifiers.getLength()>0)identifiers.item(0).setTextContent(uuid);NodeList links=taxiway.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1/event","theEvent");for(int i=0;i<links.getLength();i++)((Element)links.item(i)).setAttributeNS("http://www.w3.org/1999/xlink","xlink:title",n.airport()+" "+designator+" DNOTAM TWY.CLS");Element closed=closedAvailability(taxiway);if(closed==null)throw new IllegalArgumentException("TWY.CLS蓝图缺少关闭状态");if("SCHEDULED".equals(n.scheduleMode()))replaceTimesheets(d,closed,parseScheduleDays(n.scheduleDay()),n.scheduleStart(),n.scheduleEnd());else removeDirectTimeIntervals(closed);replaceClosureNotes(closed,n.reason(),n.remarks());root.appendChild(member);}
    }
    private static Element firstElementChild(Element parent){for(Node p=parent.getFirstChild();p!=null;p=p.getNextSibling())if(p instanceof Element e)return e;return null;}
    private static void regenerateGmlIds(Element root){if(root.hasAttributeNS("http://www.opengis.net/gml/3.2","id"))root.setAttributeNS("http://www.opengis.net/gml/3.2","gml:id","id_"+UUID.randomUUID());NodeList all=root.getElementsByTagNameNS("*","*");for(int i=0;i<all.getLength();i++){Element e=(Element)all.item(i);if(e.hasAttributeNS("http://www.opengis.net/gml/3.2","id"))e.setAttributeNS("http://www.opengis.net/gml/3.2","gml:id","id_"+UUID.randomUUID());}}
    private static Element closedAvailability(Element feature){NodeList statuses=feature.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","operationalStatus");for(int i=0;i<statuses.getLength();i++)if("CLOSED".equals(statuses.item(i).getTextContent().trim()))return(Element)statuses.item(i).getParentNode();return null;}
    private static void replaceTimesheets(Document d,Element availability,List<String> days,String start,String end){
        removeDirectTimeIntervals(availability);Node reference=availability.getFirstChild();
        for(String day:days){Element interval=d.createElementNS("http://www.aixm.aero/schema/5.1.1","aixm:timeInterval");Element sheet=d.createElementNS("http://www.aixm.aero/schema/5.1.1","aixm:Timesheet");sheet.setAttributeNS("http://www.opengis.net/gml/3.2","gml:id","schedule-"+day.toLowerCase(Locale.ROOT)+"-"+UUID.randomUUID());interval.appendChild(sheet);for(String[] x:new String[][]{{"timeReference","UTC"},{"day",day},{"startTime",start},{"endTime",end},{"daylightSavingAdjust","NO"},{"excluded","NO"}}){Element e=d.createElementNS("http://www.aixm.aero/schema/5.1.1","aixm:"+x[0]);e.setTextContent(x[1]);sheet.appendChild(e);}availability.insertBefore(interval,reference);}
    }
    private static void removeGenericEventSchedule(Document d){Element availability=lastEventAvailability(d);if(availability==null)throw new IllegalArgumentException("场景XML缺少事件可用性结构");removeDirectTimeIntervals(availability);}
    private static void removeDirectTimeIntervals(Element availability){List<Node> remove=new ArrayList<>();for(Node p=availability.getFirstChild();p!=null;p=p.getNextSibling())if(p instanceof Element e&&"timeInterval".equals(e.getLocalName()))remove.add(p);for(Node p:remove)availability.removeChild(p);}
    private static Element lastEventAvailability(Document d){NodeList statuses=d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","operationalStatus");Element result=null;for(int i=0;i<statuses.getLength();i++){String v=statuses.item(i).getTextContent().trim();if("CLOSED".equals(v)||"LIMITED".equals(v)||v.startsWith("OTHER:"))result=(Element)statuses.item(i).getParentNode();}return result;}
    private static Element eventSchedule(Document d){Element availability=lastEventAvailability(d);if(availability==null)return null;for(Node p=availability.getFirstChild();p!=null;p=p.getNextSibling())if(p instanceof Element e&&"timeInterval".equals(e.getLocalName())){NodeList sheets=e.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","Timesheet");if(sheets.getLength()>0)return(Element)sheets.item(0);}return null;}
    private static List<String> parseScheduleDays(String value){List<String> days=Arrays.stream(value.split(",")).map(String::trim).filter(s->!s.isBlank()).toList();if(days.isEmpty()||days.size()!=new HashSet<>(days).size()||!Set.of("MON","TUE","WED","THU","FRI","SAT","SUN").containsAll(days))throw new IllegalArgumentException("D项必须逐日选择MON至SUN，不允许WORK_DAY或节假日代码");return days;}
    private static String scheduleDays(Element availability){if(availability==null)return "";List<String> days=new ArrayList<>();for(Node p=availability.getFirstChild();p!=null;p=p.getNextSibling())if(p instanceof Element e&&"timeInterval".equals(e.getLocalName())){NodeList sheets=e.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","Timesheet");for(int i=0;i<sheets.getLength();i++){String day=childText((Element)sheets.item(i),"day","");if(!day.isBlank()&&!days.contains(day))days.add(day);}}return String.join(",",days);}
    private static String scheduleItem(Document d){Element availability=lastEventAvailability(d);if(availability==null)return "-";Map<String,List<String>> grouped=new LinkedHashMap<>();for(Node p=availability.getFirstChild();p!=null;p=p.getNextSibling())if(p instanceof Element e&&"timeInterval".equals(e.getLocalName())){NodeList sheets=e.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","Timesheet");for(int i=0;i<sheets.getLength();i++){Element sheet=(Element)sheets.item(i);String day=childText(sheet,"day","");String time=childText(sheet,"startTime","").replace(":","")+"-"+childText(sheet,"endTime","").replace(":","");if(!day.isBlank())grouped.computeIfAbsent(time,k->new ArrayList<>()).add(day);}}if(grouped.isEmpty())return "-";return grouped.entrySet().stream().map(e->String.join(" ",e.getValue())+" "+e.getKey()).reduce((a,b)->a+System.lineSeparator()+"D) "+b).orElse("-");}
    private static void replaceClosureNotes(Element availability,String reason,String remarks){
        NodeList notes=availability.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","Note");List<Element> optionalAnnotationsToRemove=new ArrayList<>();
        for(int i=0;i<notes.getLength();i++){Element note=(Element)notes.item(i);String property=descendantText(note,"propertyName");NodeList values=note.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1","note");if(values.getLength()==0)continue;if("operationalStatus".equals(property)){values.item(0).setTextContent(reason);}else if(remarks==null||remarks.isBlank()){Node parent=note.getParentNode();if(parent instanceof Element annotation&&"annotation".equals(annotation.getLocalName()))optionalAnnotationsToRemove.add(annotation);}else values.item(0).setTextContent(remarks);}
        for(Element annotation:optionalAnnotationsToRemove)annotation.getParentNode().removeChild(annotation);
    }
    private static String descendantText(Element root,String local){NodeList n=root.getElementsByTagNameNS("*",local);return n.getLength()==0?"":n.item(0).getTextContent().trim();}
    private static String runwayFromTitle(Document d){String title=text(d,"name");var m=java.util.regex.Pattern.compile("(?:^|\\s)(\\d{2}[LRC]?/\\d{2}[LRC]?)(?:\\s|$)").matcher(title);return m.find()?m.group(1):"";}
    private static String taxiwaysFromText(String value){var m=java.util.regex.Pattern.compile("^TWY\\s+([A-Z0-9 ]+)\\s+(?:CLSD|LIMITED)").matcher(value.toUpperCase(Locale.ROOT));return m.find()?m.group(1).trim().replace(' ', ','):"";}
    private static void replaceHeaderComments(Document d,String scenario){for(Node n=d.getFirstChild();n!=null;){Node next=n.getNextSibling();if(n.getNodeType()==Node.COMMENT_NODE)d.removeChild(n);n=next;}d.insertBefore(d.createComment(" Generated by Digital NOTAM Demo; AIXM 5.1.1 Event 2.0.k; scenario "+scenario+"; no CNOTAM text is embedded in this header. "),d.getDocumentElement());}
    private static void validateXsd(Document d)throws Exception{SchemaFactory f=SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"file,http,https");Path core=Path.of("schemas","aixm-5.1.1","aixm-5.1.1","message","AIXM_BasicMessage.xsd");Path event=Path.of("schemas","event-5.1.1-k","Event_Features.xsd");f.newSchema(new Source[]{new StreamSource(core.toFile()),new StreamSource(event.toFile())}).newValidator().validate(new DOMSource(d));}
    private static void setFirst(Document d,String local,String value){NodeList n=d.getElementsByTagNameNS("*",local);if(n.getLength()>0)n.item(0).setTextContent(value);}
    private static void setNotamField(Document d,String local,String value){NodeList notams=d.getElementsByTagNameNS("*","NOTAM");if(notams.getLength()==0)throw new IllegalArgumentException("缺少NOTAM结构");NodeList fields=((Element)notams.item(0)).getElementsByTagNameNS("*",local);if(fields.getLength()==0)throw new IllegalArgumentException("NOTAM缺少字段: "+local);fields.item(0).setTextContent(value);}
    private static void replaceAll(Document d,String local,String value){NodeList n=d.getElementsByTagNameNS("*",local);for(int i=0;i<n.getLength();i++)n.item(i).setTextContent(value);}
    private static String text(Document d,String local){NodeList n=d.getElementsByTagNameNS("*",local);return n.getLength()==0?"":n.item(0).getTextContent().trim();}
    private static Element lastElement(Document d,String local){NodeList n=d.getElementsByTagNameNS("*",local);return n.getLength()==0?null:(Element)n.item(n.getLength()-1);}
    private static String childText(Element parent,String local,String fallback){if(parent==null)return fallback;NodeList n=parent.getElementsByTagNameNS("*",local);return n.getLength()==0||n.item(0).getTextContent().isBlank()?fallback:n.item(0).getTextContent().trim();}
    private static String defaultIfBlank(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    private static String firstIso(Document d,String local){String value=text(d,local);if(value.isBlank())throw new IllegalArgumentException("缺少 "+local);return Instant.parse(value).toString();}
    private static String timeOf(String iso){return Instant.parse(iso).atZone(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));}
    private static String validInstantOr(String value,String fallback){try{return Instant.parse(value).toString();}catch(Exception e){return fallback;}}
    private static void validatePublicationTime(Notam n){
        Instant issued,start,end;try{issued=Instant.parse(n.publishedAt());start=Instant.parse(n.effectiveStart());end=Instant.parse(n.effectiveEnd());}catch(Exception e){throw new IllegalArgumentException("发布时间及有效期必须为合法UTC时间");}
        String[] parts=n.number().split("/");int numberYear=2000+Integer.parseInt(parts[1]);int issuedYear=issued.atZone(ZoneOffset.UTC).getYear();if(numberYear!=issuedYear)throw new IllegalArgumentException("通告编号年份与发布时间年份不一致");
        if(!end.isAfter(start))throw new IllegalArgumentException("C项结束时间必须晚于B项开始时间");
        if(issued.isAfter(start))throw new IllegalArgumentException("通告发布时间不能晚于B项生效开始时间；当前版本不支持追溯发布");
        if("SCHEDULED".equals(n.scheduleMode())&&!scheduleIntersects(n,start,end))throw new IllegalArgumentException("D项时间计划在B-C有效期内没有任何生效区间");
    }
    private static boolean scheduleIntersects(Notam n,Instant start,Instant end){
        LocalTime from=LocalTime.parse(n.scheduleStart()),to=LocalTime.parse(n.scheduleEnd());LocalDate first=start.atZone(ZoneOffset.UTC).toLocalDate(),last=end.atZone(ZoneOffset.UTC).toLocalDate();
        Set<String> selected=new HashSet<>(parseScheduleDays(n.scheduleDay()));for(LocalDate date=first;!date.isAfter(last);date=date.plusDays(1)){if(!selected.contains(dayCode(date.getDayOfWeek())))continue;Instant occurrenceStart=date.atTime(from).toInstant(ZoneOffset.UTC);Instant occurrenceEnd=(to.isAfter(from)?date:date.plusDays(1)).atTime(to).toInstant(ZoneOffset.UTC);if(occurrenceStart.isBefore(end)&&occurrenceEnd.isAfter(start))return true;}return false;
    }
    private static String dayCode(DayOfWeek day){return switch(day){case MONDAY->"MON";case TUESDAY->"TUE";case WEDNESDAY->"WED";case THURSDAY->"THU";case FRIDAY->"FRI";case SATURDAY->"SAT";case SUNDAY->"SUN";};}
    private static String formatCoordinates(String latitude,String latHem,String longitude,String lonHem){return coordinate(latitude,90,2,latHem)+coordinate(longitude,180,3,lonHem);}
    private static String coordinate(String value,int max,int degreeWidth,String hemisphere){double decimal=Double.parseDouble(value);if(decimal<0||decimal>max)throw new IllegalArgumentException("坐标超出范围");int degrees=(int)Math.floor(decimal);int minutes=(int)Math.round((decimal-degrees)*60);if(minutes==60){degrees++;minutes=0;}if(degrees>max)throw new IllegalArgumentException("坐标超出范围");return ("%0"+degreeWidth+"d%02d%s").formatted(degrees,minutes,hemisphere);}
    private static String[] parseCoordinates(String value){var m=java.util.regex.Pattern.compile("^(\\d{2})(\\d{2})([NS])(\\d{3})(\\d{2})([EW])$").matcher(value);if(!m.matches())return new String[]{"","","N","E"};double lat=Integer.parseInt(m.group(1))+Integer.parseInt(m.group(2))/60.0,lon=Integer.parseInt(m.group(4))+Integer.parseInt(m.group(5))/60.0;return new String[]{trimDecimal(lat),trimDecimal(lon),m.group(3),m.group(6)};}
    private static String trimDecimal(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    private static String metersOfFl(String fl,boolean upper){if(fl==null||fl.isBlank()||(!upper&&"000".equals(fl))||(upper&&"999".equals(fl)))return "";try{return Long.toString(Math.round(Integer.parseInt(fl)*100/3.28));}catch(NumberFormatException e){return "";}}
    private static String serialize(Document d)throws Exception{TransformerFactory f=TransformerFactory.newDefaultInstance();Transformer t=f.newTransformer();t.setOutputProperty(OutputKeys.INDENT,"yes");t.setOutputProperty(OutputKeys.ENCODING,"UTF-8");StringWriter w=new StringWriter();t.transform(new DOMSource(d),new StreamResult(w));return w.toString();}
    private static String notamDate(String iso){return Instant.parse(iso).atZone(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmm"));}
    private static String iso(String compact){return LocalDateTime.parse(compact,java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmm")).toInstant(ZoneOffset.UTC).toString();}
    record Imported(String number,String scenario,String title,String airport,String condition,String start,String end,String issued,String latitude,String longitude,String radiusNm,String latitudeHemisphere,String longitudeHemisphere,String qCode,String traffic,String purpose,String scope,String lowerMeters,String upperMeters,String scheduleMode,String scheduleDay,String scheduleStart,String scheduleEnd){}

    private static final class DonlonResolver implements URIResolver{
        public Source resolve(String href,String base){if(!href.contains("Baseline-datasets"))return null;String name=href.substring(href.lastIndexOf('/')+1);Path root=Path.of("data","virtual data","Donlon_2025","Donlon","DONLON original files");Path p=switch(name){case "Dataset_AirportHeliport.xml"->root.resolve("DONLON International/Donlon_EADD_AirportHeliport.xml");case "Dataset_Runway.xml"->root.resolve("DONLON International/Donlon_EADD_Runway.xml");case "Dataset_RunwayDirection.xml"->root.resolve("DONLON International/Donlon_EADD_RunwayDirection.xml");case "Dataset_Taxiway.xml"->root.resolve("DONLON International/Donlon_EADD_Taxiway.xml");case "Dataset_Airspace_simplified_geometry.xml"->root.resolve("Common/Donlon_Airspace.xml");case "Dataset_OrganisationAuthority.xml"->root.resolve("Common/Donlon_OrganisationAuthority.xml");default->null;};return p==null?null:new StreamSource(p.toFile());}
    }
}
