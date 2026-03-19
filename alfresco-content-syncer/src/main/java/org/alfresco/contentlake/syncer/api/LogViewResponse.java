package org.alfresco.contentlake.syncer.api;

import java.util.List;

public record LogViewResponse(
        String logsDirectory,
        String selectedFile,
        List<LogFileInfo> files,
        List<LogEntryView> entries
) {
}
