package com.example.digitalnotam.scenario.adlim;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class AdLimConfiguration {
    final Set<String> operations;
    final Set<String> wingSpanUnits;
    final Set<String> weightUnits;

    AdLimConfiguration() {
        Properties p = new Properties();
        Path path = Path.of("config", "scenarios", "ad-lim.properties");
        try (InputStream in = Files.newInputStream(path)) { p.load(in); }
        catch (Exception e) { throw new IllegalStateException("无法读取 AD.LIM 配置: " + path + ": " + e.getMessage(), e); }
        operations = list(p, "operations");
        wingSpanUnits = list(p, "wingSpanUnits");
        weightUnits = list(p, "weightUnits");
    }

    private static Set<String> list(Properties p, String key) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : p.getProperty(key, "").split(",")) if (!value.isBlank()) result.add(value.trim());
        if (result.isEmpty()) throw new IllegalStateException("AD.LIM 配置缺少: " + key);
        return Collections.unmodifiableSet(result);
    }
}
