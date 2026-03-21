package org.alfresco.contentlake.syncer.model.api;

import java.util.List;

public record SyncStateViewDTO(
        String remoteRootNodeId,
        int entryCount,
        List<SyncStateEntryDTO> entries
) {
}
