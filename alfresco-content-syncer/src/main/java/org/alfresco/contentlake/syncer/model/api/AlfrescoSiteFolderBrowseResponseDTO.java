package org.alfresco.contentlake.syncer.model.api;

import org.alfresco.contentlake.syncer.model.RemoteNodeDTO;

import java.util.List;

public record AlfrescoSiteFolderBrowseResponseDTO(
        AlfrescoSiteInfoDTO site,
        RemoteNodeDTO documentLibrary,
        RemoteNodeDTO currentNode,
        List<RemoteNodeDTO> children
) {
}


