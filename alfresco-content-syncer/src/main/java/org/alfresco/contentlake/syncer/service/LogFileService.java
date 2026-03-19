package org.alfresco.contentlake.syncer.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.alfresco.contentlake.syncer.api.LogEntryView;
import org.alfresco.contentlake.syncer.api.LogFileInfo;
import org.alfresco.contentlake.syncer.api.LogViewResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class LogFileService {

    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3})\\s+"
                    + "(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
                    + "\\[(?<category>[^\\]]+)]\\s+\\((?<thread>[^)]*)\\)\\s*(?<message>.*)$"
    );

    @ConfigProperty(name = "syncer.logs.dir")
    String logsDir;

    public LogViewResponse readLogs(String fileName, int limit) {
        int sanitizedLimit = Math.max(20, Math.min(limit, 500));
        Path directory = logsDirectory();
        List<LogFileInfo> files = listFiles(directory);
        String selectedFile = resolveSelectedFileName(fileName, files);
        List<LogEntryView> entries = selectedFile == null
                ? List.of()
                : tailEntries(directory.resolve(selectedFile), selectedFile, sanitizedLimit);
        return new LogViewResponse(directory.toString(), selectedFile, files, entries);
    }

    private List<LogFileInfo> listFiles(Path directory) {
        if (!Files.exists(directory)) {
            return List.of();
        }

        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("alfresco-content-syncer"))
                    .map(this::toLogFileInfo)
                    .sorted(Comparator.comparing(LogFileInfo::modifiedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(LogFileInfo::fileName))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list log files under " + directory, e);
        }
    }

    private LogFileInfo toLogFileInfo(Path path) {
        try {
            FileTime modifiedAt = Files.getLastModifiedTime(path);
            return new LogFileInfo(path.getFileName().toString(), Files.size(path), modifiedAt.toInstant());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read log metadata for " + path, e);
        }
    }

    private String resolveSelectedFileName(String requestedFileName, List<LogFileInfo> files) {
        if (files.isEmpty()) {
            return null;
        }
        if (requestedFileName == null || requestedFileName.isBlank()) {
            return files.getFirst().fileName();
        }
        if (requestedFileName.contains("/") || requestedFileName.contains("\\") || requestedFileName.contains("..")) {
            throw new IllegalArgumentException("Invalid log file name");
        }
        return files.stream()
                .map(LogFileInfo::fileName)
                .filter(requestedFileName::equals)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Log file not found: " + requestedFileName));
    }

    private List<LogEntryView> tailEntries(Path path, String fileName, int limit) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(path);
            List<LogEntryView> entries = new ArrayList<>();
            LogAccumulator current = null;
            for (String line : lines) {
                Matcher matcher = LOG_LINE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    if (current != null) {
                        entries.add(current.toEntry(fileName));
                    }
                    current = new LogAccumulator(
                            matcher.group("timestamp"),
                            matcher.group("level"),
                            matcher.group("category"),
                            matcher.group("thread"),
                            matcher.group("message"),
                            line
                    );
                } else if (current != null) {
                    current.append(line);
                } else {
                    current = new LogAccumulator(null, null, null, null, line, line);
                }
            }
            if (current != null) {
                entries.add(current.toEntry(fileName));
            }
            if (entries.size() <= limit) {
                return entries;
            }
            return entries.subList(entries.size() - limit, entries.size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read log file " + path, e);
        }
    }

    private Path logsDirectory() {
        return Path.of(logsDir).toAbsolutePath().normalize();
    }

    private static final class LogAccumulator {
        private final String timestamp;
        private final String level;
        private final String category;
        private final String thread;
        private final StringBuilder message;
        private final StringBuilder raw;

        private LogAccumulator(
                String timestamp,
                String level,
                String category,
                String thread,
                String message,
                String raw
        ) {
            this.timestamp = timestamp;
            this.level = level;
            this.category = category;
            this.thread = thread;
            this.message = new StringBuilder(message == null ? "" : message);
            this.raw = new StringBuilder(raw == null ? "" : raw);
        }

        private void append(String line) {
            this.message.append('\n').append(line);
            this.raw.append('\n').append(line);
        }

        private LogEntryView toEntry(String fileName) {
            return new LogEntryView(
                    fileName,
                    timestamp,
                    level,
                    category,
                    thread,
                    message.toString(),
                    raw.toString()
            );
        }
    }
}
