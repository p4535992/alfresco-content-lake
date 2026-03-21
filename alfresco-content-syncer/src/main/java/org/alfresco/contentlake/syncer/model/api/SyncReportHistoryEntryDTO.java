package org.alfresco.contentlake.syncer.model.api;

import java.time.Instant;

public record SyncReportHistoryEntryDTO(
        String jobId,
        String status,
        String localRoot,
        String remoteRootNodeId,
        String reportOutput,
        Instant createdAt,
        Instant completedAt
) {
}


