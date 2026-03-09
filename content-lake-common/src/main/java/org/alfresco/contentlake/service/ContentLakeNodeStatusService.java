package org.alfresco.contentlake.service;

import lombok.RequiredArgsConstructor;
import org.alfresco.contentlake.client.AlfrescoClient;
import org.alfresco.contentlake.client.HxprService;
import org.alfresco.contentlake.model.ContentLakeIngestProperties;
import org.alfresco.contentlake.model.ContentLakeNodeStatus;
import org.alfresco.contentlake.model.HxprDocument;
import org.alfresco.core.model.Node;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContentLakeNodeStatusService {

    private final AlfrescoClient alfrescoClient;
    private final HxprService hxprService;
    private final ContentLakeScopeResolver scopeResolver;

    public ContentLakeNodeStatus getNodeStatus(String nodeId) {
        return getNodeStatuses(List.of(nodeId)).get(nodeId);
    }

    public Map<String, ContentLakeNodeStatus> getNodeStatuses(Collection<String> nodeIds) {
        List<String> sanitizedIds = nodeIds == null
                ? List.of()
                : nodeIds.stream()
                .filter(nodeId -> nodeId != null && !nodeId.isBlank())
                .distinct()
                .toList();

        String repositoryId = alfrescoClient.getRepositoryId();
        Map<String, Node> nodesById = new LinkedHashMap<>();
        List<String> fileNodeIds = sanitizedIds.stream()
                .map(nodeId -> {
                    Node node = alfrescoClient.getNode(nodeId);
                    nodesById.put(nodeId, node);
                    return node;
                })
                .filter(node -> node != null && !Boolean.TRUE.equals(node.isIsFolder()))
                .map(Node::getId)
                .toList();

        Map<String, HxprDocument> documentsByNodeId = hxprService.findByNodeIds(fileNodeIds, repositoryId);
        Map<String, ContentLakeNodeStatus> statusesByNodeId = new LinkedHashMap<>();

        for (String nodeId : sanitizedIds) {
            Node node = nodesById.get(nodeId);
            if (node == null) {
                statusesByNodeId.put(nodeId, new ContentLakeNodeStatus(nodeId, null, false, false, false, false, null));
                continue;
            }

            if (Boolean.TRUE.equals(node.isIsFolder())) {
                boolean excluded = scopeResolver.isExcludedBySelfOrAncestor(node);
                boolean inScope = scopeResolver.isFolderInScope(node);
                statusesByNodeId.put(nodeId, new ContentLakeNodeStatus(node.getId(), null, true, true, inScope, excluded, null));
            } else {
                statusesByNodeId.put(nodeId, resolveFileStatus(node, documentsByNodeId.get(nodeId)));
            }
        }

        return statusesByNodeId;
    }

    private ContentLakeNodeStatus resolveFileStatus(Node node, HxprDocument document) {
        boolean excluded = scopeResolver.isExcludedBySelfOrAncestor(node);
        boolean inScope = scopeResolver.isInScope(node);
        if (!inScope) {
            return new ContentLakeNodeStatus(node.getId(), null, true, false, false, excluded, null);
        }

        ContentLakeNodeStatus.Status status = readStoredStatus(document);
        if (status == null) {
            status = ContentLakeNodeStatus.Status.PENDING;
        }

        return new ContentLakeNodeStatus(
                node.getId(),
                status,
                true,
                false,
                true,
                false,
                readStoredError(document)
        );
    }

    private ContentLakeNodeStatus.Status readStoredStatus(HxprDocument document) {
        if (document == null) {
            return null;
        }

        Map<String, Object> ingestProperties = document.getCinIngestProperties();
        if (ingestProperties == null) {
            return ContentLakeNodeStatus.Status.INDEXED;
        }

        Object rawStatus = ingestProperties.get(ContentLakeIngestProperties.CONTENT_LAKE_SYNC_STATUS);
        if (rawStatus == null) {
            return ContentLakeNodeStatus.Status.INDEXED;
        }

        try {
            return ContentLakeNodeStatus.Status.valueOf(rawStatus.toString());
        } catch (IllegalArgumentException ignored) {
            return ContentLakeNodeStatus.Status.INDEXED;
        }
    }

    private String readStoredError(HxprDocument document) {
        if (document == null || document.getCinIngestProperties() == null) {
            return null;
        }

        Object rawError = document.getCinIngestProperties().get(ContentLakeIngestProperties.CONTENT_LAKE_SYNC_ERROR);
        return rawError != null ? rawError.toString() : null;
    }
}
