package com.example.digitalnotam.scenario.common;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class QCodeDefaults {
    private final Properties values = new Properties();

    public QCodeDefaults() {
        Path path = Path.of("config", "notam", "q-code-defaults.properties");
        try (InputStream in = Files.newInputStream(path)) {
            values.load(in);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取 Q-code 默认值配置: " + path + ": " + e.getMessage(), e);
        }
    }

    public String[] get(String qCode) {
        String raw = values.getProperty(qCode, "").trim();
        String[] parts = raw.split(",", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank())
            throw new IllegalArgumentException("Q-code 缺少 Traffic/Purpose 映射: " + qCode);
        return new String[]{parts[0].trim(), parts[1].trim()};
    }
}
