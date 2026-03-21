package org.alfresco.contentlake.syncer.model;

public class SyncItemResultDTO {

    private String path;
    private String operation;
    private String outcome;
    private long sizeInBytes;
    private String remoteNodeId;
    private String message;

    public SyncItemResultDTO() {
    }

    public SyncItemResultDTO(String path, String operation, String outcome, long sizeInBytes, String remoteNodeId, String message) {
        this.path = path;
        this.operation = operation;
        this.outcome = outcome;
        this.sizeInBytes = sizeInBytes;
        this.remoteNodeId = remoteNodeId;
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public void setSizeInBytes(long sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }

    public String getRemoteNodeId() {
        return remoteNodeId;
    }

    public void setRemoteNodeId(String remoteNodeId) {
        this.remoteNodeId = remoteNodeId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}


