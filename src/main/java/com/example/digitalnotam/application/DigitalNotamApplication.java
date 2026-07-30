package com.example.digitalnotam.application;

import com.example.aixm.geometry.api.DefaultAixmGeometryService;
import com.example.digitalnotam.baseline.BaselineAirportHeliportCatalog;
import com.example.digitalnotam.baseline.BaselineAirspaceCatalog;
import com.example.digitalnotam.baseline.BaselineNavaidCatalog;
import com.example.digitalnotam.baseline.BaselineRunwayCatalog;
import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.domain.AdLimRestriction;
import com.example.digitalnotam.domain.NavUnsData;
import com.example.digitalnotam.domain.ScheduleData;
import com.example.digitalnotam.domain.ScheduleEntry;
import com.example.digitalnotam.persistence.AixmXmlStore;
import com.example.digitalnotam.persistence.NotamRepository;
import com.example.digitalnotam.workflow.DigitalNotamPipeline;
import com.example.digitalnotam.xml.AixmXml;
import com.example.digitalnotam.scenario.atsaact.AtsaActActivationComposer;
import com.example.digitalnotam.scenario.navuns.NavUnsPreviewService;
import com.example.digitalnotam.scenario.common.schedule.EventScheduleSupport;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;

public final class DigitalNotamApplication {
    private static final DigitalNotamPipeline PIPELINE = new DigitalNotamPipeline();
    private static final NotamRepository REPO = new NotamRepository();
    private static final AixmXmlStore XML_STORE = new AixmXmlStore(Path.of("data", "notams"));
    private static final BaselineRunwayCatalog BASELINE_RUNWAYS = new BaselineRunwayCatalog();
    private static final BaselineAirportHeliportCatalog BASELINE_AIRPORTS = new BaselineAirportHeliportCatalog();
    private static final BaselineAirspaceCatalog BASELINE_AIRSPACES = new BaselineAirspaceCatalog();
    private static final BaselineNavaidCatalog BASELINE_NAVAIDS = new BaselineNavaidCatalog();
    private static final AtsaActActivationComposer ATSA_ACTIVATION = new AtsaActActivationComposer();
    private static final NavUnsPreviewService NAV_UNS_PREVIEW = new NavUnsPreviewService(BASELINE_NAVAIDS);
    private static final DefaultAixmGeometryService AIXM_GEOMETRY = new DefaultAixmGeometryService();
    private static final Path PUBLIC = Path.of("src", "main", "resources", "public").toAbsolutePath().normalize();

    static {
        var restored = PIPELINE.restorePublished();
        REPO.restore(restored);
        REPO.seedIfEmpty();
        System.out.println("Restored " + restored.size() + " published NOTAM(s) from data/notams");
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
        server.createContext("/api/notams", DigitalNotamApplication::api);
        server.createContext("/api/import", DigitalNotamApplication::importXml);
        server.createContext("/api/baseline/runways", DigitalNotamApplication::baselineRunways);
        server.createContext("/api/baseline/airports", DigitalNotamApplication::baselineAirports);
        server.createContext("/api/baseline/airspaces", DigitalNotamApplication::baselineAirspaces);
        server.createContext("/api/baseline/navaids", DigitalNotamApplication::baselineNavaids);
        server.createContext("/api/scenarios/atsa-act/activation-preview", DigitalNotamApplication::atsaActivationPreview);
        server.createContext("/api/scenarios/nav-uns/preview", DigitalNotamApplication::navUnsPreview);
        server.createContext("/api/aixm/geometry", DigitalNotamApplication::aixmGeometry);
        server.createContext("/", DigitalNotamApplication::staticFile);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Digital NOTAM Demo running at http://localhost:8080");
    }

    private static void importXml(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send(ex,405,"application/json",Json.message("仅支持POST")); return; }
        try {
            String xml=new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            var i=PIPELINE.importXml(xml);
            Notam n=new Notam(UUID.randomUUID().toString(),i.number(),i.scenario(),i.title(),i.airport(),i.scenario().split("\\.")[0],i.condition(),"","","","","",i.start(),i.end(),i.latitude(),i.longitude(),i.radiusNm(),i.latitudeHemisphere(),i.longitudeHemisphere(),i.qCode(),i.traffic(),i.purpose(),i.scope(),i.lowerMeters(),i.upperMeters(),i.scheduleMode(),i.scheduleDay(),i.scheduleStart(),i.scheduleEnd(),"PUBLISHED",i.issued(),i.issued());
            REPO.save(n); send(ex,201,"application/json",Json.notam(n));
        } catch(Exception e){send(ex,400,"application/json",Json.message(e.getMessage()));}
    }

    /**
     * Reusable geometry encoder exposed by the demo.
     *
     * <p>A successful request returns the standalone AIXM 5.1.1 fragment. A
     * rejected request returns structured JSON issues so a future form can bind
     * each error to its JSON path.</p>
     */
    private static void aixmGeometry(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, "application/json", Json.message("Only POST is supported"));
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var result = AIXM_GEOMETRY.encode(body);
        if (result.valid()) {
            send(ex, 200, "application/xml", result.aixmXml());
            return;
        }
        StringBuilder json = new StringBuilder("{\"valid\":false,\"issues\":[");
        for (int i = 0; i < result.issues().size(); i++) {
            if (i > 0) json.append(',');
            var issue = result.issues().get(i);
            json.append("{\"severity\":\"").append(issue.severity())
                    .append("\",\"rule\":\"").append(jsonEsc(issue.rule()))
                    .append("\",\"path\":\"").append(jsonEsc(issue.path()))
                    .append("\",\"message\":\"").append(jsonEsc(issue.message()))
                    .append("\"}");
        }
        send(ex, 400, "application/json", json.append("]}").toString());
    }

    private static void api(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String[] parts = path.split("/");
            if ("GET".equals(ex.getRequestMethod()) && parts.length == 3) {
                Map<String, String> query = query(ex.getRequestURI().getRawQuery());
                String status = query.getOrDefault("status", "");
                String q = query.getOrDefault("q", "").toLowerCase();
                var result = REPO.all().stream().filter(n -> status.isBlank() || n.status().equals(status))
                        .filter(n -> q.isBlank() || (n.title() + n.airport() + n.number()).toLowerCase().contains(q)).toList();
                send(ex, 200, "application/json", Json.list(result)); return;
            }
            if ("POST".equals(ex.getRequestMethod()) && parts.length == 3) {
                String body=new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);Map<String, String> v = Json.parseObject(body);List<AdLimRestriction> restrictions=parseAdLimRestrictions(body);
                if(Set.of("AD.LIM","RWY.LIM").contains(v.get("scenario"))&&!restrictions.isEmpty()){
                    String commonType=v.getOrDefault("limitationType",restrictions.get(0).limitationType());
                    if(commonType.isBlank()||restrictions.stream().anyMatch(r->!commonType.equals(r.limitationType()))){send(ex,400,"application/json",Json.message("一次 AD.LIM 通告中的所有限制条件必须使用相同的限制类型"));return;}
                }
                for (String key : List.of("scenario", "fir", "title", "featureType", "effectiveStart", "effectiveEnd", "numberSeries", "qCode", "traffic", "purpose", "scope", "latitudeHemisphere", "longitudeHemisphere", "scheduleMode"))
                    if (v.getOrDefault(key, "").isBlank()) { send(ex, 400, "application/json", Json.message("缺少必填字段: " + key)); return; }
                if(v.getOrDefault("eventDescription","").isBlank()&&!Set.of("ATSA.ACT","NAV.UNS").contains(v.get("scenario"))){send(ex,400,"application/json",Json.message("必须填写E项事件"));return;}
                if(!Set.of("AD.CLS","AD.LIM","RWY.CLS","RWY.LIM","ATSA.ACT","NAV.UNS").contains(v.get("scenario"))&&v.getOrDefault("reason","").isBlank()){send(ex,400,"application/json",Json.message("当前场景必须填写E项原因"));return;}
                if(Set.of("AD.LIM","RWY.LIM").contains(v.get("scenario"))&&restrictions.isEmpty()&&(v.getOrDefault("limitationType","").isBlank()||v.getOrDefault("operation","").isBlank())){send(ex,400,"application/json",Json.message(v.get("scenario")+" 必须至少包含一个限制条件"));return;}
                if("RWY.CLS".equals(v.get("scenario"))&&v.getOrDefault("selectedRunways","").isBlank()){send(ex,400,"application/json",Json.message("RWY.CLS必须选择跑道"));return;}
                if("RWY.LIM".equals(v.get("scenario"))&&v.getOrDefault("selectedRunways","").isBlank()){send(ex,400,"application/json",Json.message("RWY.LIM必须选择跑道"));return;}
                if("SCHEDULED".equals(v.get("scheduleMode"))&&(v.getOrDefault("scheduleDay","").isBlank()||v.getOrDefault("scheduleStart","").isBlank()||v.getOrDefault("scheduleEnd","").isBlank())){send(ex,400,"application/json",Json.message("计划生效模式必须填写D项日期和时间"));return;}
                String number;
                try { number = REPO.assignNumber(v.get("numberSeries"), v.getOrDefault("numberDigits", "")); }
                catch (IllegalArgumentException e) { send(ex, 400, "application/json", Json.message(e.getMessage())); return; }
                catch (IllegalStateException e) { send(ex, 409, "application/json", Json.message(e.getMessage())); return; }
                Notam n = draftFrom(v,restrictions,UUID.randomUUID().toString(),number,Instant.now().toString()).withScheduleData(parseScheduleData(body,v));
                try { EventScheduleSupport.validate(n);validateQFields(n); }
                catch (IllegalArgumentException e) { send(ex,400,"application/json",Json.message(e.getMessage())); return; }
                send(ex, 201, "application/json", Json.notam(REPO.save(n))); return;
            }
            if (parts.length >= 4) {
                Optional<Notam> found = REPO.find(parts[3]);
                if (found.isPresent() && "PUT".equals(ex.getRequestMethod()) && parts.length == 4) {
                    Notam current=found.get();
                    if(!"DRAFT".equals(current.status())){send(ex,409,"application/json",Json.message("已发布通告不能编辑"));return;}
                    String body=new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);Map<String,String> v=Json.parseObject(body);List<AdLimRestriction> restrictions=parseAdLimRestrictions(body);
                    try{Notam updated=draftFrom(v,restrictions,current.id(),current.number(),current.createdAt()).withScheduleData(parseScheduleData(body,v));validateDraft(updated);EventScheduleSupport.validate(updated);validateQFields(updated);send(ex,200,"application/json",Json.notam(REPO.save(updated)));}
                    catch(IllegalArgumentException e){send(ex,400,"application/json",Json.message(e.getMessage()));}
                    return;
                }
                if (found.isEmpty()) { send(ex, 404, "application/json", Json.message("通告不存在")); return; }
                if ("GET".equals(ex.getRequestMethod()) && parts.length == 4) { send(ex, 200, "application/json", Json.notam(found.get())); return; }
                if ("DELETE".equals(ex.getRequestMethod()) && parts.length == 4) {
                    XML_STORE.delete(found.get());
                    REPO.delete(parts[3]); ex.sendResponseHeaders(204, -1); ex.close(); return;
                }
                if ("POST".equals(ex.getRequestMethod()) && parts.length == 5 && "publish".equals(parts[4])) {
                    if ("PUBLISHED".equals(found.get().status())) { send(ex, 409, "application/json", Json.message("通告已发布")); return; }
                    Notam published = found.get().publish();
                    REPO.reserveConsecutive(published.number(),PIPELINE.notificationCount(published));
                    PIPELINE.publish(published);
                    send(ex, 200, "application/json", Json.notam(REPO.save(published))); return;
                }
                if ("GET".equals(ex.getRequestMethod()) && parts.length == 5 && "aixm".equals(parts[4])) {
                    Path xml=Path.of("data","notams",found.get().number().replace('/','_')+".xml");
                    send(ex, 200, "application/xml", Files.exists(xml)?Files.readString(xml):AixmXml.render(found.get())); return;
                }
                if ("GET".equals(ex.getRequestMethod()) && parts.length == 5 && "cnotam".equals(parts[4])) {
                    Path xml=Path.of("data","notams",found.get().number().replace('/','_')+".xml");
                    send(ex,200,"text/plain",PIPELINE.transform(Files.readString(xml),found.get().scenario())); return;
                }
            }
            send(ex, 404, "application/json", Json.message("接口不存在"));
        } catch (Exception e) { send(ex, 500, "application/json", Json.message(e.getMessage())); }
    }

    private static void staticFile(HttpExchange ex) throws IOException {
        String requested = ex.getRequestURI().getPath().equals("/") ? "index.html" : ex.getRequestURI().getPath().substring(1);
        Path file = PUBLIC.resolve(requested).normalize();
        if (!file.startsWith(PUBLIC) || !Files.isRegularFile(file)) file = PUBLIC.resolve("index.html");
        if (!Files.isRegularFile(file)) { send(ex, 404, "text/plain", "Frontend not built. Run npm install and npm run build in frontend."); return; }
        String name = file.getFileName().toString();
        String type = name.endsWith(".js") ? "text/javascript" : name.endsWith(".css") ? "text/css" : "text/html";
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length); ex.getResponseBody().write(bytes); ex.close();
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> map = new HashMap<>(); if (raw == null) return map;
        for (String part : raw.split("&")) { String[] p = part.split("=", 2); map.put(URLDecoder.decode(p[0], StandardCharsets.UTF_8), p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : ""); }
        return map;
    }

    private static void validateQFields(Notam n) {
        if(Set.of("RWY.CLS","RWY.LIM").contains(n.scenario()))try{if(!"EADD".equals(n.airport()))throw new IllegalArgumentException(n.scenario()+" 第一版仅支持 EADD");BASELINE_RUNWAYS.resolve(n.airport(),n.runwayUuid(),n.rwyTargetType(),n.runwayDirectionUuid(),Instant.parse(n.effectiveStart()),Instant.parse(n.effectiveEnd()));}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("读取跑道基线失败: "+e.getMessage(),e);}
        if("ATSA.ACT".equals(n.scenario())){
            var selectedAirspaces=BASELINE_AIRSPACES.resolve(n.airspaceGroupId(),n.selectedAirspaces());
            ATSA_ACTIVATION.composeForPublication(n,selectedAirspaces);
            if(!Set.of("ACTIVE","INACTIVE").contains(n.activationStatus()))throw new IllegalArgumentException("ATSA.ACT状态必须为ACTIVE或INACTIVE");
            return;
        }
        if("NAV.UNS".equals(n.scenario())){
            var nav=BASELINE_NAVAIDS.require(n.navUnsData().navaidUuid());
            var affected=BASELINE_NAVAIDS.affected(nav,n.navUnsData().impactMode(),n.navUnsData().equipmentUuid());
            new com.example.digitalnotam.scenario.navuns.NavUnsStatusResolver().resolve(nav,affected,n.navUnsData());
            if(!Instant.parse(n.effectiveEnd()).isAfter(Instant.parse(n.effectiveStart())))throw new IllegalArgumentException("NAV.UNS requires a definite end time");
            return;
        }
        String expectedEvent=n.scenario().endsWith(".CLS")?"CLSD":"LIMITED";
        if(!expectedEvent.equals(n.eventDescription())) throw new IllegalArgumentException(n.scenario()+" 的E项事件必须为 "+expectedEvent);
        if(!n.qCode().matches("Q[A-Z]{4}")) throw new IllegalArgumentException("Q-CODE必须为Q开头的5位英文字母");
        if(!Set.of("I","V","IV").contains(n.traffic())) throw new IllegalArgumentException("TRAFFIC只能为I、V或IV");
        if(!Set.of("N","B","O","NB","NO","BO","NBO").contains(n.purpose())) throw new IllegalArgumentException("PURPOSE组合不符合当前版本规则");
        if(!Set.of("A","E","AE").contains(n.scope())) throw new IllegalArgumentException("SCOPE只能为A、E或AE");
        if(!Set.of("CONTINUOUS","DAILY","WEEKDAYS","DATES").contains(n.scheduleMode()))throw new IllegalArgumentException("D项生效模式无效");
        if("WEEKDAYS".equals(n.scheduleMode())){
            List<String> days=Arrays.stream(n.scheduleDay().split(",")).filter(s->!s.isBlank()).toList();
            if(days.isEmpty()||days.size()!=new HashSet<>(days).size()||!Set.of("MON","TUE","WED","THU","FRI","SAT","SUN").containsAll(days))
                throw new IllegalArgumentException("D项必须选择一个或多个明确星期，不允许WORK_DAY或节假日代码");
        }
        if(!Set.of("N","S").contains(n.latitudeHemisphere())||!Set.of("E","W").contains(n.longitudeHemisphere())) throw new IllegalArgumentException("经纬度方向无效");
        double lat=parseCoordinate(n.latitude(),"纬度"), lon=parseCoordinate(n.longitude(),"经度");
        if(lat>90||lon>180) throw new IllegalArgumentException("纬度不能超过90，经度不能超过180");
        try { int radius=Integer.parseInt(n.radiusNm()); if(radius<0||radius>999)throw new Exception(); }
        catch(Exception e){throw new IllegalArgumentException("影响半径必须为0至999海里的整数");}
        int low=Integer.parseInt(n.minimumFl()), high=Integer.parseInt(n.maximumFl());
        if(low>high) throw new IllegalArgumentException("下限飞行高度不能高于上限");
    }

    private static double parseCoordinate(String value,String name){try{double v=Double.parseDouble(value);if(!Double.isFinite(v)||v<0)throw new Exception();return v;}catch(Exception e){throw new IllegalArgumentException(name+"必须为非负十进制度数值");}}

    private static String structuredCondition(Map<String,String> v){
        String scenario=v.getOrDefault("scenario","");
        if("ATSA.ACT".equals(scenario))return v.getOrDefault("activationStatus","")+" "+v.getOrDefault("remarks","");
        if("NAV.UNS".equals(scenario))return v.getOrDefault("operationalStatus","")+" "+v.getOrDefault("remarks","");
        String subject=scenario.startsWith("AD.")?"AD":"RWY "+v.getOrDefault("selectedRunways","").trim();
        String event=v.getOrDefault("eventDescription","").trim().toUpperCase();
        String reason=v.getOrDefault("reason","").trim().toUpperCase(), remarks=v.getOrDefault("remarks","").trim().toUpperCase();
        String schedule="SCHEDULED".equals(v.get("scheduleMode"))?" "+v.getOrDefault("scheduleDay","").replace(',',' ')+" "+v.getOrDefault("scheduleStart","").replace(":","")+"-"+v.getOrDefault("scheduleEnd","").replace(":","")+" UTC":"";
        return (subject+" "+event+schedule+" DUE TO "+reason+(remarks.isBlank()?"":". "+remarks)).trim();
    }

    private static Notam draftFrom(Map<String,String> v,List<AdLimRestriction> restrictions,String id,String number,String createdAt){return new Notam(id,number,v.getOrDefault("scenario",""),v.getOrDefault("title",""),v.getOrDefault("airport","").toUpperCase(),v.getOrDefault("featureType",""),structuredCondition(v),v.getOrDefault("selectedRunways",""),v.getOrDefault("selectedTaxiways",""),v.getOrDefault("eventDescription",""),v.getOrDefault("reason",""),v.getOrDefault("remarks",""),v.getOrDefault("effectiveStart",""),v.getOrDefault("effectiveEnd",""),v.getOrDefault("latitude",""),v.getOrDefault("longitude",""),v.getOrDefault("radiusNm",""),v.getOrDefault("latitudeHemisphere",""),v.getOrDefault("longitudeHemisphere",""),v.getOrDefault("qCode","").toUpperCase(),v.getOrDefault("traffic",""),v.getOrDefault("purpose",""),v.getOrDefault("scope",""),v.getOrDefault("lowerMeters",""),v.getOrDefault("upperMeters",""),v.getOrDefault("scheduleMode",""),v.getOrDefault("scheduleDay","ANY"),v.getOrDefault("scheduleStart","00:00"),v.getOrDefault("scheduleEnd","00:00"),"DRAFT",createdAt,"",v.getOrDefault("fir",""),v.getOrDefault("scheduleStartDate",""),v.getOrDefault("scheduleEndDate",""),v.getOrDefault("qOverrideReason",""),v.getOrDefault("qOverrideOperator",""),v.getOrDefault("qOverrideAt",""),v.getOrDefault("limitationType",""),v.getOrDefault("operation",""),v.getOrDefault("flightType",""),v.getOrDefault("flightRule",""),v.getOrDefault("flightStatus",""),v.getOrDefault("flightMilitary",""),v.getOrDefault("flightOrigin",""),v.getOrDefault("flightPurpose",""),v.getOrDefault("aircraftType",""),v.getOrDefault("aircraftEngine",""),v.getOrDefault("aircraftWingSpan",""),v.getOrDefault("aircraftWingSpanUom",""),v.getOrDefault("aircraftWingSpanInterpretation",""),v.getOrDefault("aircraftWeight",""),v.getOrDefault("aircraftWeightUom",""),v.getOrDefault("aircraftWeightInterpretation",""),v.getOrDefault("pprValue",""),v.getOrDefault("pprUnit",""),v.getOrDefault("pprDetails",""),restrictions,v.getOrDefault("rwyTargetType",""),v.getOrDefault("runwayUuid",""),v.getOrDefault("runwayDirectionUuid",""),v.getOrDefault("airspaceGroupId",""),v.getOrDefault("selectedAirspaces",""),v.getOrDefault("activationStatus",""),v.getOrDefault("affectedAirports",""),v.getOrDefault("additionalFirs",""),new NavUnsData(v.getOrDefault("navaidUuid",""),v.getOrDefault("impactMode",""),v.getOrDefault("equipmentUuid",""),v.getOrDefault("signalType",""),v.getOrDefault("operationalStatus",""),Boolean.parseBoolean(v.getOrDefault("signalStillEmitted","false"))),ScheduleData.empty());}
    private static void validateDraft(Notam n){List<String> required=new ArrayList<>(List.of(n.scenario(),n.fir(),n.title(),n.featureType(),n.effectiveStart(),n.effectiveEnd(),n.qCode(),n.traffic(),n.purpose(),n.scope(),n.latitudeHemisphere(),n.longitudeHemisphere(),n.scheduleMode()));if(!Set.of("ATSA.ACT","NAV.UNS").contains(n.scenario()))required.add(n.airport());for(String value:required)if(value==null||value.isBlank())throw new IllegalArgumentException("草稿缺少必填字段");if(Set.of("AD.LIM","RWY.LIM").contains(n.scenario())){if(n.adLimRestrictions().isEmpty())throw new IllegalArgumentException(n.scenario()+" 必须至少包含一个限制条件");String type=n.limitationType();if(type.isBlank()||n.adLimRestrictions().stream().anyMatch(r->!type.equals(r.limitationType())))throw new IllegalArgumentException("一次 "+n.scenario()+" 通告中的所有限制条件必须使用相同的限制类型");}}
    private static List<AdLimRestriction> parseAdLimRestrictions(String body){return Json.parseObjectArray(body,"restrictions").stream().map(v->new AdLimRestriction(v.get("limitationType"),v.get("operation"),v.get("flightType"),v.get("flightRule"),v.get("flightStatus"),v.get("flightMilitary"),v.get("flightOrigin"),v.get("flightPurpose"),v.get("aircraftType"),v.get("aircraftEngine"),v.get("aircraftWingSpan"),v.get("aircraftWingSpanUom"),v.get("aircraftWingSpanInterpretation"),v.get("aircraftWeight"),v.get("aircraftWeightUom"),v.get("aircraftWeightInterpretation"),v.get("pprValue"),v.get("pprUnit"),v.get("pprDetails"))).toList();}
    private static ScheduleData parseScheduleData(String body,Map<String,String> values){
        List<ScheduleEntry> entries=Json.parseObjectArray(body,"entries").stream().map(v->new ScheduleEntry(v.get("startDate"),v.get("endDate"),v.get("day"),v.get("dayTil"),v.get("startTime"),v.get("endTime"),Boolean.parseBoolean(v.getOrDefault("endOfDay","false")))).toList();
        if(entries.isEmpty()&&!"CONTINUOUS".equals(values.get("scheduleMode")))return ScheduleData.empty();
        return new ScheduleData(values.getOrDefault("scheduleMode",""),entries,Json.parseStringArray(body,"excludedDates"),values.getOrDefault("scheduleNote",values.getOrDefault("note","")));
    }

    private static void baselineRunways(HttpExchange ex)throws IOException{
        if(!"GET".equals(ex.getRequestMethod())){send(ex,405,"application/json",Json.message("仅支持GET"));return;}
        String airport=query(ex.getRequestURI().getRawQuery()).getOrDefault("airport","");
        try{send(ex,200,"application/json",BASELINE_RUNWAYS.json(airport));}catch(Exception e){send(ex,400,"application/json",Json.message(e.getMessage()));}
    }

    private static void baselineAirports(HttpExchange ex)throws IOException{
        if(!"GET".equals(ex.getRequestMethod())){send(ex,405,"application/json",Json.message("仅支持GET"));return;}
        try{send(ex,200,"application/json",BASELINE_AIRPORTS.json());}catch(Exception e){send(ex,400,"application/json",Json.message(e.getMessage()));}
    }

    private static void baselineAirspaces(HttpExchange ex)throws IOException{
        if(!"GET".equals(ex.getRequestMethod())){send(ex,405,"application/json",Json.message("仅支持GET"));return;}
        try{send(ex,200,"application/json",BASELINE_AIRSPACES.json());}catch(Exception e){send(ex,400,"application/json",Json.message(e.getMessage()));}
    }

    private static void baselineNavaids(HttpExchange ex)throws IOException{
        if(!"GET".equals(ex.getRequestMethod())){send(ex,405,"application/json",Json.message("Only GET is supported"));return;}
        try{send(ex,200,"application/json",BASELINE_NAVAIDS.json());}catch(Exception e){send(ex,400,"application/json",Json.message(e.getMessage()));}
    }

    private static void atsaActivationPreview(HttpExchange ex)throws IOException{
        if(!"POST".equals(ex.getRequestMethod())){send(ex,405,"application/json",Json.message("Only POST is supported"));return;}
        try{
            String body=new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            Map<String,String> values=Json.parseObject(body);
            values.put("scenario","ATSA.ACT");values.putIfAbsent("fir","EAAD");values.putIfAbsent("title","ATSA.ACT preview");
            values.putIfAbsent("featureType","AIRSPACE");values.putIfAbsent("numberSeries","A");
            values.putIfAbsent("qCode","QATCA");values.putIfAbsent("traffic","IV");values.putIfAbsent("purpose","BO");
            values.putIfAbsent("scope","E");values.putIfAbsent("latitudeHemisphere","N");values.putIfAbsent("longitudeHemisphere","E");
            Notam draft=draftFrom(values,List.of(),"preview","A0001/26",Instant.now().toString()).withScheduleData(parseScheduleData(body,values));
            var targets=BASELINE_AIRSPACES.resolve(draft.airspaceGroupId(),draft.selectedAirspaces());
            Instant viewStart=instant(values.get("viewStart"),Instant.parse(draft.effectiveStart()));
            Instant viewEnd=instant(values.get("viewEnd"),Instant.parse(draft.effectiveEnd()));
            send(ex,200,"application/json",activationJson(ATSA_ACTIVATION.compose(draft,targets,viewStart,viewEnd)));
        }catch(Exception e){send(ex,400,"application/json",Json.message(e.getMessage()));}
    }

    private static void navUnsPreview(HttpExchange ex)throws IOException{
        if(!"POST".equals(ex.getRequestMethod())){send(ex,405,"application/json",Json.message("Only POST is supported"));return;}
        try{
            String body=new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);Map<String,String> values=Json.parseObject(body);
            values.put("scenario","NAV.UNS");values.putIfAbsent("fir","EAAD");values.putIfAbsent("title","NAV.UNS preview");values.putIfAbsent("featureType","NAVAID");values.putIfAbsent("numberSeries","A");values.putIfAbsent("qCode","QXXXX");values.putIfAbsent("traffic","IV");values.putIfAbsent("purpose","BO");values.putIfAbsent("scope","E");values.putIfAbsent("latitudeHemisphere","N");values.putIfAbsent("longitudeHemisphere","E");
            Notam draft=draftFrom(values,List.of(),"preview","A0001/26",Instant.now().toString()).withScheduleData(parseScheduleData(body,values));
            Instant start=instant(values.get("viewStart"),Instant.parse(draft.effectiveStart())),end=instant(values.get("viewEnd"),Instant.parse(draft.effectiveEnd()));
            send(ex,200,"application/json",NAV_UNS_PREVIEW.json(NAV_UNS_PREVIEW.compose(draft,start,end)));
        }catch(Exception e){send(ex,400,"application/json",Json.message(e.getMessage()));}
    }

    private static Instant instant(String value,Instant fallback){return value==null||value.isBlank()?fallback:Instant.parse(value);}
    private static String activationJson(AtsaActActivationComposer.Composition c){
        StringBuilder out=new StringBuilder("{\"viewStart\":\"").append(c.viewStart()).append("\",\"viewEnd\":\"").append(c.viewEnd())
                .append("\",\"compositionHash\":\"").append(c.compositionHash()).append("\",\"publishable\":").append(c.publishable()).append(",\"airspaces\":[");
        for(int i=0;i<c.airspaces().size();i++){if(i>0)out.append(',');var a=c.airspaces().get(i);
            out.append("{\"uuid\":\"").append(jsonEsc(a.uuid())).append("\",\"label\":\"").append(jsonEsc(a.label())).append("\",\"lowerFl\":").append(a.lowerFl()).append(",\"upperFl\":").append(a.upperFl()).append(",\"intervals\":[");
            for(int j=0;j<a.intervals().size();j++){if(j>0)out.append(',');var x=a.intervals().get(j);
                out.append("{\"start\":\"").append(x.start()).append("\",\"end\":\"").append(x.end()).append("\",\"status\":\"").append(jsonEsc(x.status())).append("\",\"source\":\"").append(x.source()).append("\",\"lowerFl\":").append(x.lowerFl()).append(",\"upperFl\":").append(x.upperFl()).append(",\"conflict\":").append(x.conflict()).append('}');
            }out.append("]}");
        }out.append("],\"issues\":[");
        for(int i=0;i<c.issues().size();i++){if(i>0)out.append(',');var x=c.issues().get(i);
            out.append("{\"code\":\"").append(jsonEsc(x.code())).append("\",\"airspaceUuid\":\"").append(jsonEsc(x.airspaceUuid())).append("\",\"start\":\"").append(x.start()).append("\",\"end\":\"").append(x.end()).append("\",\"message\":\"").append(jsonEsc(x.message())).append("\",\"blocking\":").append(x.blocking()).append('}');
        }
        return out.append("]}").toString();
    }
    private static String jsonEsc(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n");}

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); ex.sendResponseHeaders(status, bytes.length); ex.getResponseBody().write(bytes); ex.close();
    }
}
