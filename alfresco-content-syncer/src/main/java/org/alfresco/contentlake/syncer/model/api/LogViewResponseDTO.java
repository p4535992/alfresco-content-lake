package org.alfresco.contentlake.syncer.model.api;

import java.util.List;

public record LogViewResponseDTO(
        String logsDirectory,
        String selectedFile,
        List<LogFileInfoDTO> files,
        List<LogEntryViewDTO> entries
) {
}


