package org.alfresco.contentlake.syncer.model.api;

import org.alfresco.contentlake.syncer.model.RemoteNodeDTO;

import java.util.List;

public record AlfrescoBrowseResponseDTO(RemoteNodeDTO currentNode, List<RemoteNodeDTO> children) {
}


