package org.alfresco.contentlake.syncer.api;

public record LogEntryView(
        String fileName,
        String timestamp,
        String level,
        String category,
        String thread,
        String message,
        String raw
) {
}
