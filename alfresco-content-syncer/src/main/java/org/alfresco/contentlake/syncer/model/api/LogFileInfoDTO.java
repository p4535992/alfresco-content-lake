package org.alfresco.contentlake.syncer.model.api;

import java.time.Instant;

public record LogFileInfoDTO(
        String fileName,
        long sizeInBytes,
        Instant modifiedAt
) {
}


