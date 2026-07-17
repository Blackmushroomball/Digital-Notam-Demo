package com.example.digitalnotam;

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
    private static final BaselineTaxiwayCatalog BASELINE_TAXIWAYS = new BaselineTaxiwayCatalog();
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
        server.createContext("/api/baseline/taxiways", DigitalNotamApplication::baselineTaxiways);
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
                Map<String, String> v = Json.parseObject(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                for (String key : List.of("scenario", "fir", "title", "airport", "featureType", "effectiveStart", "effectiveEnd", "numberSeries", "qCode", "traffic", "purpose", "scope", "latitudeHemisphere", "longitudeHemisphere", "scheduleMode"))
                    if (v.getOrDefault(key, "").isBlank()) { send(ex, 400, "application/json", Json.message("缺少必填字段: " + key)); return; }
                if (!"EAAD".equals(v.get("fir"))) { send(ex,400,"application/json",Json.message("当前版本FIR只能选择EAAD")); return; }
                if(v.getOrDefault("eventDescription","").isBlank()||v.getOrDefault("reason","").isBlank()){send(ex,400,"application/json",Json.message("所有业务场景都必须填写E项的事件和原因"));return;}
                if("RWY.CLS".equals(v.get("scenario"))&&v.getOrDefault("selectedRunways","").isBlank()){send(ex,400,"application/json",Json.message("RWY.CLS必须选择跑道"));return;}
                if("TWY.CLS".equals(v.get("scenario"))&&v.getOrDefault("selectedTaxiways","").isBlank()){send(ex,400,"application/json",Json.message("TWY.CLS必须选择至少一条滑行道"));return;}
                if("SCHEDULED".equals(v.get("scheduleMode"))&&(v.getOrDefault("scheduleDay","").isBlank()||v.getOrDefault("scheduleStart","").isBlank()||v.getOrDefault("scheduleEnd","").isBlank())){send(ex,400,"application/json",Json.message("计划生效模式必须填写D项日期和时间"));return;}
                String number;
                try { number = REPO.assignNumber(v.get("numberSeries"), v.getOrDefault("numberDigits", "")); }
                catch (IllegalArgumentException e) { send(ex, 400, "application/json", Json.message(e.getMessage())); return; }
                catch (IllegalStateException e) { send(ex, 409, "application/json", Json.message(e.getMessage())); return; }
                Notam n = new Notam(UUID.randomUUID().toString(), number, v.get("scenario"), v.get("title"), v.get("airport").toUpperCase(), v.get("featureType"),
                        structuredCondition(v), v.getOrDefault("selectedRunways",""),v.getOrDefault("selectedTaxiways",""),v.getOrDefault("eventDescription",""),v.getOrDefault("reason",""),v.getOrDefault("remarks",""), v.get("effectiveStart"), v.get("effectiveEnd"), v.getOrDefault("latitude", ""),
                        v.getOrDefault("longitude", ""), v.getOrDefault("radiusNm", ""), v.get("latitudeHemisphere"), v.get("longitudeHemisphere"),
                        v.get("qCode").toUpperCase(), v.get("traffic"), v.get("purpose"), v.get("scope"), v.getOrDefault("lowerMeters", ""), v.getOrDefault("upperMeters", ""),
                        v.get("scheduleMode"),v.getOrDefault("scheduleDay","ANY"),v.getOrDefault("scheduleStart","00:00"),v.getOrDefault("scheduleEnd","00:00"), "DRAFT", Instant.now().toString(), "");
                try { validateQFields(n); }
                catch (IllegalArgumentException e) { send(ex,400,"application/json",Json.message(e.getMessage())); return; }
                send(ex, 201, "application/json", Json.notam(REPO.save(n))); return;
            }
            if (parts.length >= 4) {
                Optional<Notam> found = REPO.find(parts[3]);
                if (found.isEmpty()) { send(ex, 404, "application/json", Json.message("通告不存在")); return; }
                if ("GET".equals(ex.getRequestMethod()) && parts.length == 4) { send(ex, 200, "application/json", Json.notam(found.get())); return; }
                if ("DELETE".equals(ex.getRequestMethod()) && parts.length == 4) {
                    XML_STORE.delete(found.get());
                    REPO.delete(parts[3]); ex.sendResponseHeaders(204, -1); ex.close(); return;
                }
                if ("POST".equals(ex.getRequestMethod()) && parts.length == 5 && "publish".equals(parts[4])) {
                    if ("PUBLISHED".equals(found.get().status())) { send(ex, 409, "application/json", Json.message("通告已发布")); return; }
                    Notam published = found.get().publish();
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
        if("RWY.CLS".equals(n.scenario())&&!"09R/27L".equals(n.selectedRunways())) throw new IllegalArgumentException("当前RWY.CLS模板仅支持基线跑道09R/27L");
        if("TWY.CLS".equals(n.scenario()))try{Set<String> selected=new LinkedHashSet<>(Arrays.asList(n.selectedTaxiways().split(",")));if(selected.contains("")||!BASELINE_TAXIWAYS.identifiers(n.airport()).keySet().containsAll(selected))throw new IllegalArgumentException("所选滑行道不属于机场基线数据");}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("读取机场滑行道基线失败: "+e.getMessage());}
        String expectedEvent=n.scenario().endsWith(".CLS")?"CLSD":"LIMITED";
        if(!expectedEvent.equals(n.eventDescription())) throw new IllegalArgumentException(n.scenario()+" 的E项事件必须为 "+expectedEvent);
        if(!n.qCode().matches("Q[A-Z]{4}")) throw new IllegalArgumentException("Q-CODE必须为Q开头的5位英文字母");
        if(!Set.of("I","V","IV").contains(n.traffic())) throw new IllegalArgumentException("TRAFFIC只能为I、V或IV");
        if(!Set.of("N","B","O","NB","NO","BO","NBO").contains(n.purpose())) throw new IllegalArgumentException("PURPOSE组合不符合当前版本规则");
        if(!Set.of("A","E","AE").contains(n.scope())) throw new IllegalArgumentException("SCOPE只能为A、E或AE");
        if(!Set.of("CONTINUOUS","SCHEDULED").contains(n.scheduleMode()))throw new IllegalArgumentException("D项生效模式无效");
        if("SCHEDULED".equals(n.scheduleMode())){
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
        String subject=scenario.startsWith("AD.")?"AD":scenario.equals("RWY.CLS")?"RWY "+v.getOrDefault("selectedRunways","").trim():scenario.startsWith("RWY.")?"RWY":scenario.equals("TWY.CLS")?"TWY "+v.getOrDefault("selectedTaxiways","").replace(',',' '):"TWY";
        String event=v.getOrDefault("eventDescription","").trim().toUpperCase();
        String reason=v.getOrDefault("reason","").trim().toUpperCase(), remarks=v.getOrDefault("remarks","").trim().toUpperCase();
        String schedule="SCHEDULED".equals(v.get("scheduleMode"))?" "+v.getOrDefault("scheduleDay","").replace(',',' ')+" "+v.getOrDefault("scheduleStart","").replace(":","")+"-"+v.getOrDefault("scheduleEnd","").replace(":","")+" UTC":"";
        return (subject+" "+event+schedule+" DUE TO "+reason+(remarks.isBlank()?"":". "+remarks)).trim();
    }

    private static void baselineRunways(HttpExchange ex)throws IOException{
        if(!"GET".equals(ex.getRequestMethod())){send(ex,405,"application/json",Json.message("仅支持GET"));return;}
        String airport=query(ex.getRequestURI().getRawQuery()).getOrDefault("airport","");
        try{send(ex,200,"application/json",BASELINE_RUNWAYS.json(airport));}catch(Exception e){send(ex,400,"application/json",Json.message(e.getMessage()));}
    }

    private static void baselineTaxiways(HttpExchange ex)throws IOException{
        if(!"GET".equals(ex.getRequestMethod())){send(ex,405,"application/json",Json.message("仅支持GET"));return;}
        String airport=query(ex.getRequestURI().getRawQuery()).getOrDefault("airport","");
        try{send(ex,200,"application/json",BASELINE_TAXIWAYS.json(airport));}catch(Exception e){send(ex,400,"application/json",Json.message(e.getMessage()));}
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); ex.sendResponseHeaders(status, bytes.length); ex.getResponseBody().write(bytes); ex.close();
    }
}
