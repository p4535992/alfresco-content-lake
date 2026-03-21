package org.alfresco.contentlake.syncer.entity;

import java.time.Instant;

public class SyncStateEntry {

    private String relativePath;
    private String remoteNodeId;
    private long sizeInBytes;
    private String sha256;
    private Instant remoteModifiedAt;
    private Instant lastTransferredAt;

    public SyncStateEntry() {
    }

    public SyncStateEntry(String relativePath, String remoteNodeId, long sizeInBytes, String sha256, Instant remoteModifiedAt, Instant lastTransferredAt) {
        this.relativePath = relativePath;
        this.remoteNodeId = remoteNodeId;
        this.sizeInBytes = sizeInBytes;
        this.sha256 = sha256;
        this.remoteModifiedAt = remoteModifiedAt;
        this.lastTransferredAt = lastTransferredAt;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getRemoteNodeId() {
        return remoteNodeId;
    }

    public void setRemoteNodeId(String remoteNodeId) {
        this.remoteNodeId = remoteNodeId;
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public void setSizeInBytes(long sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Instant getRemoteModifiedAt() {
        return remoteModifiedAt;
    }

    public void setRemoteModifiedAt(Instant remoteModifiedAt) {
        this.remoteModifiedAt = remoteModifiedAt;
    }

    public Instant getLastTransferredAt() {
        return lastTransferredAt;
    }

    public void setLastTransferredAt(Instant lastTransferredAt) {
        this.lastTransferredAt = lastTransferredAt;
    }
}


