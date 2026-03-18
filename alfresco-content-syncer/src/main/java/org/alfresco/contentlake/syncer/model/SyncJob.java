package org.alfresco.contentlake.syncer.model;

import java.time.Instant;

public class SyncJob {

    private String jobId;
    private String localRoot;
    private String remoteRootNodeId;
    private boolean dryRun;
    private boolean deleteRemoteMissing;
    private Instant createdAt;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile SyncJobStatus status;
    private volatile String errorMessage;
    private volatile SyncReport report;

    public SyncJob() {
    }

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

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public String getRemoteRootNodeId() {
        return remoteRootNodeId;
    }

    public void setRemoteRootNodeId(String remoteRootNodeId) {
        this.remoteRootNodeId = remoteRootNodeId;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isDeleteRemoteMissing() {
        return deleteRemoteMissing;
    }

    public void setDeleteRemoteMissing(boolean deleteRemoteMissing) {
        this.deleteRemoteMissing = deleteRemoteMissing;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public SyncJobStatus getStatus() {
        return status;
    }

    public void setStatus(SyncJobStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public SyncReport getReport() {
        return report;
    }

    public void setReport(SyncReport report) {
        this.report = report;
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
