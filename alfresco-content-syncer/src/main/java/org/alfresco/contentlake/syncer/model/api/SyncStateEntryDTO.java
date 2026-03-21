package org.alfresco.contentlake.syncer.model.api;

import java.time.Instant;

public record SyncStateEntryDTO(
        String relativePath,
        String remoteNodeId,
        long sizeInBytes,
        String sha256,
        Instant remoteModifiedAt,
        Instant lastTransferredAt
) {
}
