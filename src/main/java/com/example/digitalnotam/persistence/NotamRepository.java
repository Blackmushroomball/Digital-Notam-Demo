package com.example.digitalnotam.persistence;

import com.example.digitalnotam.domain.Notam;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Year;
import java.time.ZoneOffset;

public final class NotamRepository {
    public NotamRepository() {}
    private final Map<String, Notam> data = new ConcurrentHashMap<>();
    private final Set<String> batchReservations = ConcurrentHashMap.newKeySet();
    private static final Set<String> SERIES = Set.of("A", "C", "D");

    public void seedIfEmpty() {
        if (!data.isEmpty()) return;
        Notam draft = new Notam(UUID.randomUUID().toString(), assignNumber("A", "1001"), "RWY.CLS", "DONLON 09R/27L 跑道关闭", "EADD", "RUNWAY",
                "RWY 09R/27L CLSD DUE TO MAINT", "09R/27L", "", "跑道关闭", "MAINT", "", "2026-07-14T01:00:00Z", "2026-07-14T05:00:00Z",
                "40.0801", "116.5846", "2", "N", "E", "QMRLC", "IV", "NBO", "A", "", "", "CONTINUOUS", "ANY", "01:00", "05:00", "DRAFT", Instant.now().toString(), "");
        data.put(draft.id(), draft);
    }

    public void restore(Collection<Notam> notams) {
        for (Notam notam : notams) data.put(notam.id(), notam);
    }

    public Collection<Notam> all() { return data.values().stream().sorted(Comparator.comparing(Notam::createdAt).reversed()).toList(); }
    public Optional<Notam> find(String id) { return Optional.ofNullable(data.get(id)); }
    public Notam save(Notam n) { data.put(n.id(), n); return n; }
    public boolean delete(String id) { return data.remove(id) != null; }

    public synchronized String assignNumber(String series, String requestedDigits) {
        return assignNumber(series, requestedDigits, null);
    }

    /** Assigns a number while allowing an existing draft to retain its own number. */
    public synchronized String assignNumberForUpdate(String draftId, String series, String requestedDigits) {
        if (draftId == null || draftId.isBlank()) throw new IllegalArgumentException("草稿ID不能为空");
        return assignNumber(series, requestedDigits, draftId);
    }

    private String assignNumber(String series, String requestedDigits, String excludedDraftId) {
        String normalizedSeries = series == null ? "" : series.trim().toUpperCase(Locale.ROOT);
        if (!SERIES.contains(normalizedSeries)) throw new IllegalArgumentException("编号系列必须为 A、C 或 D");
        int year = Year.now(ZoneOffset.UTC).getValue() % 100;
        if (requestedDigits != null && !requestedDigits.isBlank()) {
            if (!requestedDigits.matches("\\d{1,4}")) throw new IllegalArgumentException("编号数字必须为 1 至 4 位数字");
            int value = Integer.parseInt(requestedDigits);
            if (value < 1) throw new IllegalArgumentException("编号数字必须大于 0");
            String number = formatNumber(normalizedSeries, value, year);
            if (numberExists(number, excludedDraftId)) throw new IllegalStateException("通告编号 " + number + " 已存在，请重新指定");
            return number;
        }
        for (int value = 1; value <= 9999; value++) {
            String number = formatNumber(normalizedSeries, value, year);
            if (!numberExists(number, excludedDraftId)) return number;
        }
        throw new IllegalStateException(normalizedSeries + " 系列本年度编号已用完");
    }
    public synchronized void reserveConsecutive(String base,int count){
        if(count<1||batchReservations.contains(base))return;String[] p=base.split("/");String series=p[0].substring(0,1);int start=Integer.parseInt(p[0].substring(1)),year=Integer.parseInt(p[1]);
        List<String> requested=new ArrayList<>();for(int i=0;i<count;i++){if(start+i>9999)throw new IllegalStateException("连续编号段超过9999");requested.add(formatNumber(series,start+i,year));}
        for(int i=1;i<requested.size();i++)if(numberExists(requested.get(i))||batchReservations.contains(requested.get(i)))throw new IllegalStateException("连续编号 "+requested.get(i)+" 已被占用");
        batchReservations.addAll(requested);
    }

    private boolean numberExists(String number) { return numberExists(number, null); }
    private boolean numberExists(String number,String excludedDraftId) { return batchReservations.contains(number)||data.values().stream().anyMatch(n -> !n.id().equals(excludedDraftId)&&number.equals(n.number())); }
    private static String formatNumber(String series, int value, int year) { return "%s%04d/%02d".formatted(series, value, year); }
}
