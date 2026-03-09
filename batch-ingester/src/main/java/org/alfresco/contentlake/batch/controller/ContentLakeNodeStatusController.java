package org.alfresco.contentlake.batch.controller;

import lombok.RequiredArgsConstructor;
import org.alfresco.contentlake.batch.model.NodeStatusBulkRequest;
import org.alfresco.contentlake.model.ContentLakeNodeStatus;
import org.alfresco.contentlake.service.ContentLakeNodeStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/content-lake/nodes")
@RequiredArgsConstructor
public class ContentLakeNodeStatusController {

    private final ContentLakeNodeStatusService contentLakeNodeStatusService;

    @GetMapping("/{nodeId}/status")
    public ContentLakeNodeStatus getNodeStatus(@PathVariable String nodeId) {
        return contentLakeNodeStatusService.getNodeStatus(nodeId);
    }

    @PostMapping("/status")
    public Map<String, ContentLakeNodeStatus> getNodeStatuses(@RequestBody(required = false) NodeStatusBulkRequest request) {
        List<String> nodeIds = request != null && request.nodeIds() != null
                ? request.nodeIds()
                : List.of();
        return contentLakeNodeStatusService.getNodeStatuses(nodeIds);
    }
}
