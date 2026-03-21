package org.alfresco.contentlake.syncer.model.api;

public record LogEntryViewDTO(
        String fileName,
        String timestamp,
        String level,
        String category,
        String thread,
        String message,
        String raw
) {
}


