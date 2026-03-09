package org.alfresco.contentlake.model;

public record ContentLakeNodeStatus(
        String nodeId,
        Status status,
        boolean exists,
        boolean folder,
        boolean inScope,
        boolean excluded,
        String error
) {

    public enum Status {
        PENDING,
        INDEXED,
        FAILED
    }
}
