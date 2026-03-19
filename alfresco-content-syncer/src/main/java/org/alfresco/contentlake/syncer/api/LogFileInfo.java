package org.alfresco.contentlake.syncer.api;

import java.time.Instant;

public record LogFileInfo(
        String fileName,
        long sizeInBytes,
        Instant modifiedAt
) {
}
