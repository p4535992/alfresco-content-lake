package org.alfresco.contentlake.syncer.model;

import java.time.Instant;

public class SyncJob {

    private final String jobId;
    private final String localRoot;
    private final String remoteRootNodeId;
    private final boolean dryRun;
    private final boolean deleteRemoteMissing;
    private final Instant createdAt;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile SyncJobStatus status;
    private volatile String errorMessage;
    private volatile SyncReport report;

    public SyncJob(String jobId, String localRoot, String remoteRootNodeId, boolean dryRun, boolean deleteRemoteMissing) {
        this.jobId = jobId;
        this.localRoot = localRoot;
        this.remoteRootNodeId = remoteRootNodeId;
        this.dryRun = dryRun;
        this.deleteRemoteMissing = deleteRemoteMissing;
        this.createdAt = Instant.now();
        this.status = SyncJobStatus.QUEUED;
    }

    public String getJobId() {
        return jobId;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public String getRemoteRootNodeId() {
        return remoteRootNodeId;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isDeleteRemoteMissing() {
        return deleteRemoteMissing;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public SyncJobStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public SyncReport getReport() {
        return report;
    }

    public void markRunning() {
        status = SyncJobStatus.RUNNING;
        startedAt = Instant.now();
    }

    public void markCompleted(SyncReport syncReport) {
        report = syncReport;
        status = SyncJobStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void markFailed(String message, SyncReport syncReport) {
        errorMessage = message;
        report = syncReport;
        status = SyncJobStatus.FAILED;
        completedAt = Instant.now();
    }
}
