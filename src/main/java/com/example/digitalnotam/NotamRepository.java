package com.example.digitalnotam;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Year;
import java.time.ZoneOffset;

final class NotamRepository {
    private final Map<String, Notam> data = new ConcurrentHashMap<>();
    private static final Set<String> SERIES = Set.of("A", "C", "D");

    void seedIfEmpty() {
        if (!data.isEmpty()) return;
        Notam draft = new Notam(UUID.randomUUID().toString(), assignNumber("A", "1001"), "RWY.CLS", "DONLON 09R/27L 跑道关闭", "EADD", "RUNWAY",
                "RWY 09R/27L CLSD DUE TO MAINT", "09R/27L", "", "跑道关闭", "MAINT", "", "2026-07-14T01:00:00Z", "2026-07-14T05:00:00Z",
                "40.0801", "116.5846", "2", "N", "E", "QMRLC", "IV", "NBO", "A", "", "", "CONTINUOUS", "ANY", "01:00", "05:00", "DRAFT", Instant.now().toString(), "");
        data.put(draft.id(), draft);
        Notam published = new Notam(UUID.randomUUID().toString(), assignNumber("C", "1001"), "TWY.CLS", "DONLON 滑行道 B 关闭", "EADD", "TAXIWAY",
                "TWY B BTN B3 AND B5 CLSD", "", "B", "", "", "", "2026-07-13T16:00:00Z", "2026-07-20T16:00:00Z",
                "31.1443", "121.8083", "1", "N", "E", "QMXLC", "IV", "NBO", "A", "", "", "SCHEDULED", "ANY", "16:00", "20:00", "PUBLISHED", Instant.now().toString(), Instant.now().toString());
        data.put(published.id(), published);
    }

    void restore(Collection<Notam> notams) {
        for (Notam notam : notams) data.put(notam.id(), notam);
    }

    Collection<Notam> all() { return data.values().stream().sorted(Comparator.comparing(Notam::createdAt).reversed()).toList(); }
    Optional<Notam> find(String id) { return Optional.ofNullable(data.get(id)); }
    Notam save(Notam n) { data.put(n.id(), n); return n; }
    boolean delete(String id) { return data.remove(id) != null; }

    synchronized String assignNumber(String series, String requestedDigits) {
        String normalizedSeries = series == null ? "" : series.trim().toUpperCase(Locale.ROOT);
        if (!SERIES.contains(normalizedSeries)) throw new IllegalArgumentException("编号系列必须为 A、C 或 D");
        int year = Year.now(ZoneOffset.UTC).getValue() % 100;
        if (requestedDigits != null && !requestedDigits.isBlank()) {
            if (!requestedDigits.matches("\\d{1,4}")) throw new IllegalArgumentException("编号数字必须为 1 至 4 位数字");
            int value = Integer.parseInt(requestedDigits);
            if (value < 1) throw new IllegalArgumentException("编号数字必须大于 0");
            String number = formatNumber(normalizedSeries, value, year);
            if (numberExists(number)) throw new IllegalStateException("通告编号 " + number + " 已存在，请重新指定");
            return number;
        }
        for (int value = 1; value <= 9999; value++) {
            String number = formatNumber(normalizedSeries, value, year);
            if (!numberExists(number)) return number;
        }
        throw new IllegalStateException(normalizedSeries + " 系列本年度编号已用完");
    }

    private boolean numberExists(String number) { return data.values().stream().anyMatch(n -> number.equals(n.number())); }
    private static String formatNumber(String series, int value, int year) { return "%s%04d/%02d".formatted(series, value, year); }
}
