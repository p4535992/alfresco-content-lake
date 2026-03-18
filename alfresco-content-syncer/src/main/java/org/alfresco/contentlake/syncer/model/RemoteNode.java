package org.alfresco.contentlake.syncer.model;

import java.time.Instant;

public record RemoteNode(
        String id,
        String name,
        boolean folder,
        boolean file,
        long sizeInBytes,
        Instant modifiedAt
) {
}
