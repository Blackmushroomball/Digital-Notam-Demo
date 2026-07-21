package com.example.digitalnotam.persistence;

import com.example.digitalnotam.domain.Notam;
import com.example.digitalnotam.xml.AixmXml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class AixmXmlStore {
    private final Path directory;

    public AixmXmlStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Path save(Notam notam) throws IOException {
        Files.createDirectories(directory);
        Path target = fileOf(notam);
        Path temporary = Files.createTempFile(directory, ".notam-", ".xml.tmp");
        try {
            Files.writeString(temporary, AixmXml.render(notam), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public boolean delete(Notam notam) throws IOException {
        return Files.deleteIfExists(fileOf(notam));
    }

    private Path fileOf(Notam notam) {
        String safeNumber = notam.number().replace('/', '_').replaceAll("[^A-Z0-9_-]", "_");
        Path file = directory.resolve(safeNumber + ".xml").normalize();
        if (!file.startsWith(directory)) throw new IllegalArgumentException("非法通告编号");
        return file;
    }
}
