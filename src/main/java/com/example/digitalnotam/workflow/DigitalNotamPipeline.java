package com.example.digitalnotam.workflow;

import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.scenario.adcls.AdClsNotamProducer;
import com.example.digitalnotam.scenario.adcls.AdClsScenarioBuilder;
import com.example.digitalnotam.scenario.adlim.AdLimNotamProducer;
import com.example.digitalnotam.scenario.adlim.AdLimScenarioBuilder;
import com.example.digitalnotam.scenario.rwycls.RwyClsNotamProducer;
import com.example.digitalnotam.scenario.rwycls.RwyClsScenarioBuilder;
import com.example.digitalnotam.scenario.rwylim.RwyLimNotamProducer;
import com.example.digitalnotam.scenario.rwylim.RwyLimScenarioBuilder;

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

public final class DigitalNotamPipeline {
    private static final Set<String> SCENARIOS = Set.of("AD.CLS", "AD.LIM", "RWY.CLS", "RWY.LIM");
    private static final Path SAMPLES = Path.of("data", "virtual data", "Donlon_2025", "Donlon", "Digital NOTAM");
    private static final Map<String, String> BLUEPRINT = Map.of(
            "AD.CLS", "DN_AD.CLS_1_ad_closed.xml", "AD.LIM", "DN_AD.LIM_1_closed_except_for.xml",
            "RWY.CLS", "DN_RWY.CLS_1_full_runway_closure.xml", "RWY.LIM", "DN_RWY.LIM_1_closed_except_for_takeoff.xml");
    private final Path store = Path.of("data", "notams").toAbsolutePath().normalize();
    private final Map<String, ScenarioBuilder> scenarioBuilders = Map.of("AD.CLS", new AdClsScenarioBuilder(), "AD.LIM", new AdLimScenarioBuilder(), "RWY.CLS", new RwyClsScenarioBuilder(), "RWY.LIM", new RwyLimScenarioBuilder());
    private final AdClsNotamProducer adClsNotamProducer = new AdClsNotamProducer();
    private final AdLimNotamProducer adLimNotamProducer = new AdLimNotamProducer();
    private final RwyClsNotamProducer rwyClsNotamProducer = new RwyClsNotamProducer();
    private final RwyLimNotamProducer rwyLimNotamProducer = new RwyLimNotamProducer();

    public List<Notam> restorePublished() {
        if (!Files.isDirectory(store)) return List.of();
        List<Notam> restored = new ArrayList<>();
        try (var files = Files.list(store)) {
            files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .sorted().forEach(path -> {
                        try {
                            restored.add(restorePublished(path));
                        } catch (Exception e) {
                            System.err.println("Skip invalid NOTAM XML " + path.getFileName() + ": " + e.getMessage());
                        }
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
        String scenario = text(d, "scenario");
        if (!SCENARIOS.contains(scenario)) throw new IllegalArgumentException("不支持场景 " + scenario);
        String series = text(d, "series").toUpperCase(Locale.ROOT);
        String digits = text(d, "number");
        String year = text(d, "year");
        if (!series.matches("[ACD]") || !digits.matches("\\d{1,4}") || !year.matches("\\d{2}|\\d{4}"))
            throw new IllegalArgumentException("通告编号字段无效");
        String number = series + String.format("%04d", Integer.parseInt(digits)) + "/" + year.substring(year.length() - 2);
        String start = firstIso(d, "beginPosition");
        String end = firstIso(d, "endPosition");
        Element schedule = eventSchedule(d);
        String scheduleMode = schedule == null ? "CONTINUOUS" : "SCHEDULED";
        String scheduleDay = scheduleDays(lastEventAvailability(d));
        String scheduleStart = childText(schedule, "startTime", timeOf(start));
        String scheduleEnd = childText(schedule, "endTime", timeOf(end));
        String coordinates = text(d, "coordinates");
        String[] coordinateParts = parseCoordinates(coordinates);
        String minimum = text(d, "minimumFL"), maximum = text(d, "maximumFL");
        String modified = validInstantOr(text(d, "issued"), Files.getLastModifiedTime(path).toInstant().toString());
        String featureType = scenario.startsWith("AD.") ? "AIRPORT_HELIPORT" : scenario.startsWith("RWY.") ? "RUNWAY" : "TAXIWAY";
        return new Notam(UUID.nameUUIDFromBytes(number.getBytes(StandardCharsets.UTF_8)).toString(), number, scenario,
                defaultIfBlank(text(d, "name"), number), defaultIfBlank(text(d, "location"), "EADD"), featureType,
                text(d, "text"), runwayFromTitle(d), "", "", "", "", start, end, coordinateParts[0], coordinateParts[1], defaultIfBlank(text(d, "radius"), "0"), coordinateParts[2], coordinateParts[3],
                defaultIfBlank(text(d, "selectionCode"), "QXXXX"), defaultIfBlank(text(d, "traffic"), "IV"), defaultIfBlank(text(d, "purpose"), "NBO"), defaultIfBlank(text(d, "scope"), "A"),
                metersOfFl(minimum, false), metersOfFl(maximum, true), scheduleMode, scheduleDay, scheduleStart, scheduleEnd,
                "PUBLISHED", modified, modified);
    }

    private Notam restoreLegacyPublished(Path path, Document d) throws Exception {
        String file = path.getFileName().toString();
        var matcher = java.util.regex.Pattern.compile("^([ACD])(\\d{4})_(\\d{2})\\.xml$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(file);
        if (!matcher.matches()) throw new IllegalArgumentException("旧格式XML文件名不符合 SNNNN_YY.xml");
        String number = matcher.group(1).toUpperCase(Locale.ROOT) + matcher.group(2) + "/" + matcher.group(3);
        String feature;
        String scenario;
        if (d.getElementsByTagNameNS("*", "AirportHeliport").getLength() > 0) {
            feature = "AIRPORT_HELIPORT";
            scenario = "AD.CLS";
        } else if (d.getElementsByTagNameNS("*", "Runway").getLength() > 0) {
            feature = "RUNWAY";
            scenario = "RWY.CLS";
        } else throw new IllegalArgumentException("无法识别旧格式航空要素");
        String start = firstIso(d, "beginPosition"), end = firstIso(d, "endPosition");
        String note = text(d, "note"), title = number, condition = note;
        int separator = note.indexOf(" - ");
        if (separator >= 0) {
            title = note.substring(0, separator).trim();
            condition = note.substring(separator + 3).trim();
        }
        String airport = defaultIfBlank(text(d, "designator"), "EADD");
        String modified = Files.getLastModifiedTime(path).toInstant().toString();
        return new Notam(UUID.nameUUIDFromBytes(number.getBytes(StandardCharsets.UTF_8)).toString(), number, scenario, title, airport, feature,
                condition, "", "", "", "", "", start, end, "", "", "", "N", "E", "QXXXX", "IV", "NBO", "A", "", "", "CONTINUOUS", "ANY", timeOf(start), timeOf(end), "PUBLISHED", modified, modified);
    }

    public String publish(Notam n) throws Exception {
        if (!SCENARIOS.contains(n.scenario())) throw new IllegalArgumentException("不支持的场景: " + n.scenario());
        validatePublicationTime(n);
        ScenarioBuilder dedicatedBuilder = scenarioBuilders.get(n.scenario());
        if (dedicatedBuilder != null) {
            Document d = dedicatedBuilder.build(n);
            if ("AD.CLS".equals(n.scenario())) adClsNotamProducer.produce(d, n);
            if ("AD.LIM".equals(n.scenario())) adLimNotamProducer.produce(d, n);
            if ("RWY.CLS".equals(n.scenario())) rwyClsNotamProducer.produce(d, n);
            if ("RWY.LIM".equals(n.scenario())) rwyLimNotamProducer.produce(d, n);
            replaceHeaderComments(d, n.scenario());
            validateRules(d);
            dedicatedBuilder.validate(d, n);
            validateXsd(d);
            String xml = serialize(d);
            save(n.number(), xml);
            return xml;
        }
        Document d = parse(Files.readString(SAMPLES.resolve(BLUEPRINT.get(n.scenario())), StandardCharsets.UTF_8));
        String[] number = n.number().split("[/]");
        setFirst(d, "series", number[0].substring(0, 1));
        setFirst(d, "number", number[0].substring(1));
        setFirst(d, "year", "20" + number[1]);
        setFirst(d, "scenario", n.scenario());
        setNotamField(d, "issued", n.publishedAt());
        setFirst(d, "name", n.title());
        setFirst(d, "text", n.condition());
        replaceAll(d, "beginPosition", n.effectiveStart());
        replaceAll(d, "endPosition", n.effectiveEnd());
        setFirst(d, "effectiveStart", notamDate(n.effectiveStart()));
        setFirst(d, "effectiveEnd", notamDate(n.effectiveEnd()));
        setNotamField(d, "affectedFIR", "EAAD");
        setNotamField(d, "selectionCode", n.qCode());
        setNotamField(d, "traffic", n.traffic());
        setNotamField(d, "purpose", n.purpose());
        setNotamField(d, "scope", n.scope());
        setNotamField(d, "minimumFL", n.minimumFl());
        setNotamField(d, "maximumFL", n.maximumFl());
        setNotamField(d, "coordinates", formatCoordinates(n.latitude(), n.latitudeHemisphere(), n.longitude(), n.longitudeHemisphere()));
        setNotamField(d, "radius", String.format("%03d", Integer.parseInt(n.radiusNm())));
        if ("SCHEDULED".equals(n.scheduleMode()))
            applySchedule(d, n.scheduleDay(), n.scheduleStart(), n.scheduleEnd());
        else removeGenericEventSchedule(d);
        replaceHeaderComments(d, n.scenario());
        annotateModules(d);
        validateRules(d);
        validateXsd(d);
        String xml = serialize(d);
        save(n.number(), xml);
        return xml;
    }

    public Imported importXml(String xml) throws Exception {
        Document d = parse(xml);
        validateRules(d);
        validateXsd(d);
        String scenario = text(d, "scenario"), series = text(d, "series"), num = text(d, "number"), year = text(d, "year");
        if (!SCENARIOS.contains(scenario)) throw new IllegalArgumentException("首期不支持场景: " + scenario);
        String number = series + String.format("%04d", Integer.parseInt(num)) + "/" + year.substring(year.length() - 2);
        replaceHeaderComments(d, scenario);
        annotateModules(d);
        save(number, serialize(d));
        String[] c = parseCoordinates(text(d, "coordinates"));
        Element schedule = eventSchedule(d);
        String scheduleMode = schedule == null ? "CONTINUOUS" : "SCHEDULED";
        return new Imported(number, scenario, text(d, "name"), text(d, "location"), text(d, "text"), iso(text(d, "effectiveStart")), iso(text(d, "effectiveEnd")), validInstantOr(text(d, "issued"), Instant.now().toString()),
                c[0], c[1], defaultIfBlank(text(d, "radius"), "0"), c[2], c[3], text(d, "selectionCode"), text(d, "traffic"), text(d, "purpose"), text(d, "scope"),
                metersOfFl(text(d, "minimumFL"), false), metersOfFl(text(d, "maximumFL"), true), scheduleMode, scheduleDays(lastEventAvailability(d)), childText(schedule, "startTime", "00:00"), childText(schedule, "endTime", "23:59"));
    }

    public String transform(String xml, String scenario) throws Exception {
        Document d = parse(xml);
        if ("AD.LIM".equals(scenario)) { normalizeLegacyAdLimAvailabilities(d); xml = serialize(d); }
        TransformerFactory f = TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl", getClass().getClassLoader());
        f.setURIResolver(new DonlonResolver());
        Path xsl = Path.of("utils", "NOTAM-Production-Templates", "xslt-scenarios", scenario + "_CNOTAM_text_generation.xslt").toAbsolutePath();
        Transformer t = f.newTransformer(new StreamSource(xsl.toFile()));
        StringWriter out = new StringWriter();
        t.transform(new StreamSource(new StringReader(xml)), new StreamResult(out));
        String qCode = text(d, "selectionCode"), minimum = text(d, "minimumFL"), maximum = text(d, "maximumFL");
        String q = "Q) " + String.join("/", text(d, "affectedFIR"), qCode, text(d, "traffic"), text(d, "purpose"), text(d, "scope"), minimum, maximum, text(d, "coordinates") + text(d, "radius"));
        String result = out.toString().replaceFirst("(?m)^Q\\) [^\\r\\n]*", java.util.regex.Matcher.quoteReplacement(q));
        String dItem = scheduleItem(d);
        if (result.matches("(?s).*(?m)^D\\).*"))
            result = result.replaceFirst("(?m)^D\\) [^\\r\\n]*", java.util.regex.Matcher.quoteReplacement("D) " + dItem));
        else if (!"-".equals(dItem))
            result = result.replaceFirst("(?m)^E\\)", java.util.regex.Matcher.quoteReplacement("D) " + dItem + System.lineSeparator() + "E)"));
        String eItem = text(d, "text");
        if (result.matches("(?s).*(?m)^E\\).*"))
            result = result.replaceFirst("(?m)^E\\) [^\\r\\n]*", java.util.regex.Matcher.quoteReplacement("E) " + eItem));
        result = result.replaceAll("(?m)^[FG]\\) [^\\r\\n]*(?:\\R|$)", "").stripTrailing();
        boolean warningOrRestriction = qCode.startsWith("QW") || qCode.startsWith("QR");
        String fItem = warningOrRestriction ? "FL" + minimum : "-", gItem = warningOrRestriction ? "FL" + maximum : "-";
        return result + System.lineSeparator() + "F) " + fItem + System.lineSeparator() + "G) " + gItem;
    }

    private static void normalizeLegacyAdLimAvailabilities(Document d) {
        Element slice = lastElement(d, "AirportHeliportTimeSlice");
        List<Element> wrappers = new ArrayList<>();
        for (Node node = slice.getFirstChild(); node != null; node = node.getNextSibling()) if (node instanceof Element wrapper && "availability".equals(wrapper.getLocalName())) {
            String status = descendantText(wrapper, "operationalStatus");
            if ("LIMITED".equals(status) || "OTHER:EXTENDED".equals(status)) wrappers.add(wrapper);
        }
        if (wrappers.size() < 2) return;
        Element target = (Element) wrappers.get(0).getElementsByTagNameNS("*", "AirportHeliportAvailability").item(0);
        for (int i = 1; i < wrappers.size(); i++) {
            Element source = (Element) wrappers.get(i).getElementsByTagNameNS("*", "AirportHeliportAvailability").item(0);
            List<Element> usages = new ArrayList<>();
            for (Node node = source.getFirstChild(); node != null; node = node.getNextSibling()) if (node instanceof Element e && "usage".equals(e.getLocalName())) usages.add(e);
            for (Element usage : usages) target.appendChild(usage);
            slice.removeChild(wrappers.get(i));
        }
        for (Node node = target.getFirstChild(); node != null;) { Node next = node.getNextSibling(); if (node.getNodeType() == Node.COMMENT_NODE && node.getNodeValue().trim().toLowerCase(Locale.ROOT).startsWith("limitation")) target.removeChild(node); node = next; }
    }

    private void save(String number, String xml) throws IOException {
        Files.createDirectories(store);
        Path p = store.resolve(number.replace('/', '_') + ".xml");
        Files.writeString(p, xml, StandardCharsets.UTF_8);
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static void validateRules(Document d) {
        if (!"AIXMBasicMessage".equals(d.getDocumentElement().getLocalName()))
            throw new IllegalArgumentException("根元素必须为 AIXMBasicMessage");
        for (String x : List.of("Event", "EventTimeSlice", "scenario", "NOTAM", "series", "number", "year"))
            if (d.getElementsByTagNameNS("*", x).getLength() == 0)
                throw new IllegalArgumentException("缺少必需元素: " + x);
        Element availability = lastEventAvailability(d);
        if (availability != null) for (Node p = availability.getFirstChild(); p != null; p = p.getNextSibling())
            if (p instanceof Element e && "timeInterval".equals(e.getLocalName())) {
                NodeList sheets = e.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "Timesheet");
                for (int i = 0; i < sheets.getLength(); i++) {
                    String day = childText((Element) sheets.item(i), "day", "");
                    if (Set.of("WORK_DAY", "BEF_WORK_DAY", "AFT_WORK_DAY", "HOL", "BEF_HOL", "AFT_HOL").contains(day))
                        throw new IllegalArgumentException("Digital NOTAM事件时间表不允许使用含糊日期代码: " + day);
                }
            }
    }

    private static void applySchedule(Document d, String day, String start, String end) {
        List<String> days = parseScheduleDays(day);
        if (!start.matches("(?:[01]\\d|2[0-3]):[0-5]\\d") || !end.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))
            throw new IllegalArgumentException("D项时间必须为HH:mm格式");
        NodeList statuses = d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "operationalStatus");
        Element availability = null;
        for (int i = 0; i < statuses.getLength(); i++) {
            String v = statuses.item(i).getTextContent();
            if ("CLOSED".equals(v) || "LIMITED".equals(v) || v.startsWith("OTHER:")) {
                Node p = statuses.item(i).getParentNode();
                if (p instanceof Element e) availability = e;
            }
        }
        if (availability == null) throw new IllegalArgumentException("场景XML缺少事件可用性结构");
        replaceTimesheets(d, availability, days, start, end);
    }

    private static Element firstElementChild(Element parent) {
        for (Node p = parent.getFirstChild(); p != null; p = p.getNextSibling()) if (p instanceof Element e) return e;
        return null;
    }

    private static void regenerateGmlIds(Element root) {
        if (root.hasAttributeNS("http://www.opengis.net/gml/3.2", "id"))
            root.setAttributeNS("http://www.opengis.net/gml/3.2", "gml:id", "id_" + UUID.randomUUID());
        NodeList all = root.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (e.hasAttributeNS("http://www.opengis.net/gml/3.2", "id"))
                e.setAttributeNS("http://www.opengis.net/gml/3.2", "gml:id", "id_" + UUID.randomUUID());
        }
    }

    private static Element closedAvailability(Element feature) {
        NodeList statuses = feature.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "operationalStatus");
        for (int i = 0; i < statuses.getLength(); i++)
            if ("CLOSED".equals(statuses.item(i).getTextContent().trim()))
                return (Element) statuses.item(i).getParentNode();
        return null;
    }

    private static void replaceTimesheets(Document d, Element availability, List<String> days, String start, String end) {
        removeDirectTimeIntervals(availability);
        Node reference = availability.getFirstChild();
        for (String day : days) {
            Element interval = d.createElementNS("http://www.aixm.aero/schema/5.1.1", "aixm:timeInterval");
            Element sheet = d.createElementNS("http://www.aixm.aero/schema/5.1.1", "aixm:Timesheet");
            sheet.setAttributeNS("http://www.opengis.net/gml/3.2", "gml:id", "schedule-" + day.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID());
            interval.appendChild(sheet);
            for (String[] x : new String[][]{{"timeReference", "UTC"}, {"day", day}, {"startTime", start}, {"endTime", end}, {"daylightSavingAdjust", "NO"}, {"excluded", "NO"}}) {
                Element e = d.createElementNS("http://www.aixm.aero/schema/5.1.1", "aixm:" + x[0]);
                e.setTextContent(x[1]);
                sheet.appendChild(e);
            }
            availability.insertBefore(interval, reference);
        }
    }

    private static void removeGenericEventSchedule(Document d) {
        Element availability = lastEventAvailability(d);
        if (availability == null) throw new IllegalArgumentException("场景XML缺少事件可用性结构");
        removeDirectTimeIntervals(availability);
    }

    private static void removeDirectTimeIntervals(Element availability) {
        List<Node> remove = new ArrayList<>();
        for (Node p = availability.getFirstChild(); p != null; p = p.getNextSibling())
            if (p instanceof Element e && "timeInterval".equals(e.getLocalName())) remove.add(p);
        for (Node p : remove) availability.removeChild(p);
    }

    private static Element lastEventAvailability(Document d) {
        NodeList statuses = d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "operationalStatus");
        Element result = null;
        for (int i = 0; i < statuses.getLength(); i++) {
            String v = statuses.item(i).getTextContent().trim();
            if ("CLOSED".equals(v) || "LIMITED".equals(v) || v.startsWith("OTHER:"))
                result = (Element) statuses.item(i).getParentNode();
        }
        return result;
    }

    private static Element eventSchedule(Document d) {
        Element availability = lastEventAvailability(d);
        if (availability == null) return null;
        for (Node p = availability.getFirstChild(); p != null; p = p.getNextSibling())
            if (p instanceof Element e && "timeInterval".equals(e.getLocalName())) {
                NodeList sheets = e.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "Timesheet");
                if (sheets.getLength() > 0) return (Element) sheets.item(0);
            }
        return null;
    }

    private static List<String> parseScheduleDays(String value) {
        List<String> days = Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        if (days.isEmpty() || days.size() != new HashSet<>(days).size() || !Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN").containsAll(days))
            throw new IllegalArgumentException("D项必须逐日选择MON至SUN，不允许WORK_DAY或节假日代码");
        return days;
    }

    private static String scheduleDays(Element availability) {
        if (availability == null) return "";
        List<String> days = new ArrayList<>();
        for (Node p = availability.getFirstChild(); p != null; p = p.getNextSibling())
            if (p instanceof Element e && "timeInterval".equals(e.getLocalName())) {
                NodeList sheets = e.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "Timesheet");
                for (int i = 0; i < sheets.getLength(); i++) {
                    String day = childText((Element) sheets.item(i), "day", "");
                    if (!day.isBlank() && !days.contains(day)) days.add(day);
                }
            }
        return String.join(",", days);
    }

    private static String scheduleItem(Document d) {
        Element availability = lastEventAvailability(d);
        if (availability == null) return "-";
        List<String> values = new ArrayList<>();
        for (Node p = availability.getFirstChild(); p != null; p = p.getNextSibling())
            if (p instanceof Element e && "timeInterval".equals(e.getLocalName())) {
                NodeList sheets = e.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "Timesheet");
                for (int i = 0; i < sheets.getLength(); i++) {
                    Element sheet = (Element) sheets.item(i);
                    String day = childText(sheet, "day", ""), startDate=childText(sheet,"startDate",""), endDate=childText(sheet,"endDate","");
                    String start=childText(sheet,"startTime","").replace(":",""), rawEnd=childText(sheet,"endTime","");
                    String time=start+"-"+("00:00".equals(rawEnd)?"2359":rawEnd.replace(":",""));
                    if(!startDate.isBlank()){String range=notamScheduleDate(startDate);if(!endDate.isBlank()&&!endDate.equals(startDate))range+="-"+notamScheduleDate(endDate);values.add(range+" "+time);}
                    else if("ANY".equals(day))values.add("DAILY "+time);else if(!day.isBlank())values.add(day+" "+time);
                }
            }
        return values.isEmpty()?"-":String.join(" ",values);
    }

    private static String notamScheduleDate(String value){return MonthDay.parse(value,java.time.format.DateTimeFormatter.ofPattern("dd-MM")).format(java.time.format.DateTimeFormatter.ofPattern("MMM dd",Locale.ENGLISH)).toUpperCase(Locale.ROOT);}

    private static void replaceClosureNotes(Element availability, String reason, String remarks) {
        NodeList notes = availability.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "Note");
        List<Element> optionalAnnotationsToRemove = new ArrayList<>();
        for (int i = 0; i < notes.getLength(); i++) {
            Element note = (Element) notes.item(i);
            String property = descendantText(note, "propertyName");
            NodeList values = note.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "note");
            if (values.getLength() == 0) continue;
            if ("operationalStatus".equals(property)) {
                values.item(0).setTextContent(reason);
            } else if (remarks == null || remarks.isBlank()) {
                Node parent = note.getParentNode();
                if (parent instanceof Element annotation && "annotation".equals(annotation.getLocalName()))
                    optionalAnnotationsToRemove.add(annotation);
            } else values.item(0).setTextContent(remarks);
        }
        for (Element annotation : optionalAnnotationsToRemove) annotation.getParentNode().removeChild(annotation);
    }

    private static String descendantText(Element root, String local) {
        NodeList n = root.getElementsByTagNameNS("*", local);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent().trim();
    }

    private static String runwayFromTitle(Document d) {
        String title = text(d, "name");
        var m = java.util.regex.Pattern.compile("(?:^|\\s)(\\d{2}[LRC]?/\\d{2}[LRC]?)(?:\\s|$)").matcher(title);
        return m.find() ? m.group(1) : "";
    }

    private static void replaceHeaderComments(Document d, String scenario) {
        for (Node n = d.getFirstChild(); n != null; ) {
            Node next = n.getNextSibling();
            if (n.getNodeType() == Node.COMMENT_NODE) d.removeChild(n);
            n = next;
        }
        d.insertBefore(d.createComment(" Generated by Digital NOTAM Demo; AIXM 5.1.1 Event 2.0.k; scenario " + scenario + "; no CNOTAM text is embedded in this header. "), d.getDocumentElement());
    }

    private static void annotateModules(Document d) {
        removeGeneratedModuleComments(d.getDocumentElement());
        Element root = d.getDocumentElement();
        for (Node p = root.getFirstChild(); p != null; p = p.getNextSibling()) {
            if (!(p instanceof Element member) || !"hasMember".equals(member.getLocalName())) continue;
            Element feature = firstElementChild(member);
            if (feature == null) continue;
            if ("Event".equals(feature.getLocalName())) insertModuleCommentBefore(member, "Event and NOTAM metadata");
            else insertModuleCommentBefore(member, "Affected feature TEMPDELTA");
        }
        NodeList statuses = d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "operationalStatus");
        Set<Element> annotatedAvailability = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < statuses.getLength(); i++) {
            String status = statuses.item(i).getTextContent().trim();
            if (!(statuses.item(i).getParentNode() instanceof Element availability) || !annotatedAvailability.add(availability))
                continue;
            if ("NORMAL".equals(status)) insertModuleCommentBefore(availability, "Baseline status block");
            else if ("CLOSED".equals(status) || "LIMITED".equals(status) || status.startsWith("OTHER:")) {
                insertModuleCommentBefore(availability, "Event status block");
                annotateEventAvailability(availability);
            }
        }
        NodeList links = d.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1/event", "theEvent");
        Set<Element> annotatedExtensions = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < links.getLength(); i++) {
            Node p = links.item(i).getParentNode();
            while (p instanceof Element e && !"extension".equals(e.getLocalName())) p = p.getParentNode();
            if (p instanceof Element extension && annotatedExtensions.add(extension))
                insertModuleCommentBefore(extension, "Link to the event");
        }
    }

    private static void annotateEventAvailability(Element availability) {
        boolean scheduleAnnotated = false, reasonAnnotated = false, noteAnnotated = false;
        for (Node p = availability.getFirstChild(); p != null; p = p.getNextSibling()) {
            if (!(p instanceof Element child)) continue;
            if ("timeInterval".equals(child.getLocalName()) && child.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "Timesheet").getLength() > 0 && !scheduleAnnotated) {
                insertModuleCommentBefore(child, "Event schedule");
                scheduleAnnotated = true;
            }
            if (!"annotation".equals(child.getLocalName())) continue;
            NodeList notes = child.getElementsByTagNameNS("http://www.aixm.aero/schema/5.1.1", "Note");
            if (notes.getLength() == 0) continue;
            String property = descendantText((Element) notes.item(0), "propertyName");
            if ("operationalStatus".equals(property) && !reasonAnnotated) {
                insertModuleCommentBefore(child, "Reason");
                reasonAnnotated = true;
            } else if (!"operationalStatus".equals(property) && !noteAnnotated) {
                insertModuleCommentBefore(child, "Additional note");
                noteAnnotated = true;
            }
        }
    }

    private static void insertModuleCommentBefore(Element target, String label) {
        Node parent = target.getParentNode();
        if (parent != null)
            parent.insertBefore(target.getOwnerDocument().createComment(" Digital NOTAM module: " + label + " "), target);
    }

    private static void removeGeneratedModuleComments(Node root) {
        for (Node p = root.getFirstChild(); p != null; ) {
            Node next = p.getNextSibling();
            if (p.getNodeType() == Node.COMMENT_NODE && p.getNodeValue() != null && p.getNodeValue().trim().startsWith("Digital NOTAM module:"))
                root.removeChild(p);
            else removeGeneratedModuleComments(p);
            p = next;
        }
    }

    private static void validateXsd(Document d) throws Exception {
        SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,http,https");
        Path core = Path.of("schemas", "aixm-5.1.1", "aixm-5.1.1", "message", "AIXM_BasicMessage.xsd");
        Path event = Path.of("schemas", "event-5.1.1-k", "Event_Features.xsd");
        f.newSchema(new Source[]{new StreamSource(core.toFile()), new StreamSource(event.toFile())}).newValidator().validate(new DOMSource(d));
    }

    private static void setFirst(Document d, String local, String value) {
        NodeList n = d.getElementsByTagNameNS("*", local);
        if (n.getLength() > 0) n.item(0).setTextContent(value);
    }

    private static void setNotamField(Document d, String local, String value) {
        NodeList notams = d.getElementsByTagNameNS("*", "NOTAM");
        if (notams.getLength() == 0) throw new IllegalArgumentException("缺少NOTAM结构");
        NodeList fields = ((Element) notams.item(0)).getElementsByTagNameNS("*", local);
        if (fields.getLength() == 0) throw new IllegalArgumentException("NOTAM缺少字段: " + local);
        fields.item(0).setTextContent(value);
    }

    private static void replaceAll(Document d, String local, String value) {
        NodeList n = d.getElementsByTagNameNS("*", local);
        for (int i = 0; i < n.getLength(); i++) n.item(i).setTextContent(value);
    }

    private static String text(Document d, String local) {
        NodeList n = d.getElementsByTagNameNS("*", local);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent().trim();
    }

    private static Element lastElement(Document d, String local) {
        NodeList n = d.getElementsByTagNameNS("*", local);
        return n.getLength() == 0 ? null : (Element) n.item(n.getLength() - 1);
    }

    private static String childText(Element parent, String local, String fallback) {
        if (parent == null) return fallback;
        NodeList n = parent.getElementsByTagNameNS("*", local);
        return n.getLength() == 0 || n.item(0).getTextContent().isBlank() ? fallback : n.item(0).getTextContent().trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String firstIso(Document d, String local) {
        String value = text(d, local);
        if (value.isBlank()) throw new IllegalArgumentException("缺少 " + local);
        return Instant.parse(value).toString();
    }

    private static String timeOf(String iso) {
        return Instant.parse(iso).atZone(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static String validInstantOr(String value, String fallback) {
        try {
            return Instant.parse(value).toString();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void validatePublicationTime(Notam n) {
        Instant issued, start, end;
        try {
            issued = Instant.parse(n.publishedAt());
            start = Instant.parse(n.effectiveStart());
            end = Instant.parse(n.effectiveEnd());
        } catch (Exception e) {
            throw new IllegalArgumentException("发布时间及有效期必须为合法UTC时间");
        }
        String[] parts = n.number().split("/");
        int numberYear = 2000 + Integer.parseInt(parts[1]);
        int issuedYear = issued.atZone(ZoneOffset.UTC).getYear();
        if (numberYear != issuedYear) throw new IllegalArgumentException("通告编号年份与发布时间年份不一致");
        if (!end.isAfter(start)) throw new IllegalArgumentException("C项结束时间必须晚于B项开始时间");
        if (issued.isAfter(start))
            throw new IllegalArgumentException("通告发布时间不能晚于B项生效开始时间；当前版本不支持追溯发布");
        if (!"CONTINUOUS".equals(n.scheduleMode()) && !scheduleIntersects(n, start, end))
            throw new IllegalArgumentException("D项时间计划在B-C有效期内没有任何生效区间");
    }

    private static boolean scheduleIntersects(Notam n, Instant start, Instant end) {
        LocalTime from = LocalTime.parse(n.scheduleStart()), to = LocalTime.parse(n.scheduleEnd());
        LocalDate first = start.atZone(ZoneOffset.UTC).toLocalDate(), last = end.atZone(ZoneOffset.UTC).toLocalDate();
        Set<String> selected = "WEEKDAYS".equals(n.scheduleMode())?new HashSet<>(parseScheduleDays(n.scheduleDay())):Set.of("MON","TUE","WED","THU","FRI","SAT","SUN");
        if("DATES".equals(n.scheduleMode())){LocalDate configuredStart=LocalDate.parse(n.scheduleStartDate()),configuredEnd=LocalDate.parse(n.scheduleEndDate());if(configuredEnd.isBefore(configuredStart))throw new IllegalArgumentException("Dates schedule 的 endDate 不能早于 startDate");first=first.isAfter(configuredStart)?first:configuredStart;last=last.isBefore(configuredEnd)?last:configuredEnd;}
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (!selected.contains(dayCode(date.getDayOfWeek()))) continue;
            Instant occurrenceStart = date.atTime(from).toInstant(ZoneOffset.UTC);
            Instant occurrenceEnd = (to.isAfter(from) ? date : date.plusDays(1)).atTime(to).toInstant(ZoneOffset.UTC);
            if (occurrenceStart.isBefore(end) && occurrenceEnd.isAfter(start)) return true;
        }
        return false;
    }

    private static String dayCode(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }

    private static String formatCoordinates(String latitude, String latHem, String longitude, String lonHem) {
        return coordinate(latitude, 90, 2, latHem) + coordinate(longitude, 180, 3, lonHem);
    }

    private static String coordinate(String value, int max, int degreeWidth, String hemisphere) {
        double decimal = Double.parseDouble(value);
        if (decimal < 0 || decimal > max) throw new IllegalArgumentException("坐标超出范围");
        int degrees = (int) Math.floor(decimal);
        int minutes = (int) Math.round((decimal - degrees) * 60);
        if (minutes == 60) {
            degrees++;
            minutes = 0;
        }
        if (degrees > max) throw new IllegalArgumentException("坐标超出范围");
        return ("%0" + degreeWidth + "d%02d%s").formatted(degrees, minutes, hemisphere);
    }

    private static String[] parseCoordinates(String value) {
        var m = java.util.regex.Pattern.compile("^(\\d{2})(\\d{2})([NS])(\\d{3})(\\d{2})([EW])$").matcher(value);
        if (!m.matches()) return new String[]{"", "", "N", "E"};
        double lat = Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2)) / 60.0, lon = Integer.parseInt(m.group(4)) + Integer.parseInt(m.group(5)) / 60.0;
        return new String[]{trimDecimal(lat), trimDecimal(lon), m.group(3), m.group(6)};
    }

    private static String trimDecimal(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String metersOfFl(String fl, boolean upper) {
        if (fl == null || fl.isBlank() || (!upper && "000".equals(fl)) || (upper && "999".equals(fl))) return "";
        try {
            return Long.toString(Math.round(Integer.parseInt(fl) * 100 / 3.28));
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static String serialize(Document d) throws Exception {
        removeWhitespaceOnlyText(d);
        TransformerFactory f = TransformerFactory.newDefaultInstance();
        Transformer t = f.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(d), new StreamResult(w));
        return formatRootStartTag(w.toString());
    }

    private static String formatRootStartTag(String xml) {
        String marker = "<message:AIXMBasicMessage";
        int start = xml.indexOf(marker);
        if (start < 0) return xml;
        int end = start + marker.length();
        boolean quoted = false;
        for (; end < xml.length(); end++) {
            char c = xml.charAt(end);
            if (c == '"') quoted = !quoted;
            else if (c == '>' && !quoted) break;
        }
        if (end >= xml.length()) return xml;
        String opening = xml.substring(start, end + 1);
        java.util.regex.Matcher attributes = java.util.regex.Pattern.compile("\\s+([^\\s=]+)=\"([^\"]*)\"").matcher(opening.substring(marker.length(), opening.length() - 1));
        StringBuilder formatted = new StringBuilder(marker);
        while (attributes.find()) formatted.append(System.lineSeparator()).append("  ").append(attributes.group(1)).append("=\"").append(attributes.group(2)).append('"');
        formatted.append('>');
        String prefix = xml.substring(0, start).stripTrailing();
        if (!prefix.isEmpty()) prefix += System.lineSeparator();
        return prefix + formatted + xml.substring(end + 1);
    }

    private static void removeWhitespaceOnlyText(Node parent) {
        for (Node node = parent.getFirstChild(); node != null; ) {
            Node next = node.getNextSibling();
            if (node.getNodeType() == Node.TEXT_NODE && node.getNodeValue().isBlank()) parent.removeChild(node);
            else removeWhitespaceOnlyText(node);
            node = next;
        }
    }

    private static String notamDate(String iso) {
        return Instant.parse(iso).atZone(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmm"));
    }

    private static String iso(String compact) {
        return LocalDateTime.parse(compact, java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmm")).toInstant(ZoneOffset.UTC).toString();
    }

    public record Imported(String number, String scenario, String title, String airport, String condition, String start,
                    String end, String issued, String latitude, String longitude, String radiusNm,
                    String latitudeHemisphere, String longitudeHemisphere, String qCode, String traffic, String purpose,
                    String scope, String lowerMeters, String upperMeters, String scheduleMode, String scheduleDay,
                    String scheduleStart, String scheduleEnd) {
    }

    private static final class DonlonResolver implements URIResolver {
        public Source resolve(String href, String base) {
            if (!href.contains("Baseline-datasets")) return null;
            String name = href.substring(href.lastIndexOf('/') + 1);
            Path root = Path.of("data", "virtual data", "Donlon_2025", "Donlon", "DONLON original files");
            Path p = switch (name) {
                case "Dataset_AirportHeliport.xml" ->
                        root.resolve("DONLON International/Donlon_EADD_AirportHeliport.xml");
                case "Dataset_Runway.xml" -> root.resolve("DONLON International/Donlon_EADD_Runway.xml");
                case "Dataset_RunwayDirection.xml" ->
                        root.resolve("DONLON International/Donlon_EADD_RunwayDirection.xml");
                case "Dataset_Taxiway.xml" -> root.resolve("DONLON International/Donlon_EADD_Taxiway.xml");
                case "Dataset_Airspace_simplified_geometry.xml" -> root.resolve("Common/Donlon_Airspace.xml");
                case "Dataset_OrganisationAuthority.xml" -> root.resolve("Common/Donlon_OrganisationAuthority.xml");
                default -> null;
            };
            return p == null ? null : new StreamSource(p.toFile());
        }
    }
}
