package org.alfresco.contentlake.syncer.api;

import org.alfresco.contentlake.syncer.model.RemoteNode;

import java.util.List;

public record AlfrescoSiteFolderBrowseResponse(
        AlfrescoSiteInfo site,
        RemoteNode documentLibrary,
        RemoteNode currentNode,
        List<RemoteNode> children
) {
}
