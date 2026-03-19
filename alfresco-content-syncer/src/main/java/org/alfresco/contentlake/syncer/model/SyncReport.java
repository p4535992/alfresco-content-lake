package org.alfresco.contentlake.syncer.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SyncReport {

    private String localRoot;
    private String remoteRootNodeId;
    private boolean dryRun;
    private boolean deleteRemoteMissing;
    private Instant startedAt = Instant.now();
    private Instant completedAt;

    private int directoriesScanned;
    private int filesScanned;
    private int foldersCreated;
    private int filesUploaded;
    private int filesUpdated;
    private int filesSkipped;
    private int remoteNodesDeleted;
    private long uploadedBytes;
    private long updatedBytes;

    private List<SyncFailure> failures = new ArrayList<>();
    private List<SyncItemResult> items = new ArrayList<>();

    public SyncReport() {
    }

    public SyncReport(String localRoot, String remoteRootNodeId, boolean dryRun, boolean deleteRemoteMissing) {
        this.localRoot = localRoot;
        this.remoteRootNodeId = remoteRootNodeId;
        this.dryRun = dryRun;
        this.deleteRemoteMissing = deleteRemoteMissing;
        this.startedAt = Instant.now();
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

    public int getDirectoriesScanned() {
        return directoriesScanned;
    }

    public void setDirectoriesScanned(int directoriesScanned) {
        this.directoriesScanned = directoriesScanned;
    }

    public int getFilesScanned() {
        return filesScanned;
    }

    public void setFilesScanned(int filesScanned) {
        this.filesScanned = filesScanned;
    }

    public int getFoldersCreated() {
        return foldersCreated;
    }

    public void setFoldersCreated(int foldersCreated) {
        this.foldersCreated = foldersCreated;
    }

    public int getFilesUploaded() {
        return filesUploaded;
    }

    public void setFilesUploaded(int filesUploaded) {
        this.filesUploaded = filesUploaded;
    }

    public int getFilesUpdated() {
        return filesUpdated;
    }

    public void setFilesUpdated(int filesUpdated) {
        this.filesUpdated = filesUpdated;
    }

    public int getFilesSkipped() {
        return filesSkipped;
    }

    public void setFilesSkipped(int filesSkipped) {
        this.filesSkipped = filesSkipped;
    }

    public int getRemoteNodesDeleted() {
        return remoteNodesDeleted;
    }

    public void setRemoteNodesDeleted(int remoteNodesDeleted) {
        this.remoteNodesDeleted = remoteNodesDeleted;
    }

    public long getUploadedBytes() {
        return uploadedBytes;
    }

    public void setUploadedBytes(long uploadedBytes) {
        this.uploadedBytes = uploadedBytes;
    }

    public long getUpdatedBytes() {
        return updatedBytes;
    }

    public void setUpdatedBytes(long updatedBytes) {
        this.updatedBytes = updatedBytes;
    }

    public List<SyncFailure> getFailures() {
        return failures;
    }

    public void setFailures(List<SyncFailure> failures) {
        this.failures = failures;
    }

    public List<SyncItemResult> getItems() {
        return items;
    }

    public void setItems(List<SyncItemResult> items) {
        this.items = items;
    }

    public int getFailedCount() {
        return failures == null ? 0 : failures.size();
    }

    public long getDurationMs() {
        Instant end = completedAt != null ? completedAt : Instant.now();
        Instant start = startedAt != null ? startedAt : end;
        return Duration.between(start, end).toMillis();
    }

    public void incrementDirectoriesScanned() {
        directoriesScanned++;
    }

    public void incrementFilesScanned() {
        filesScanned++;
    }

    public void recordFolderCreated() {
        foldersCreated++;
    }

    public void recordUpload(long size) {
        filesUploaded++;
        uploadedBytes += Math.max(size, 0L);
    }

    public void recordUpdate(long size) {
        filesUpdated++;
        updatedBytes += Math.max(size, 0L);
    }

    public void recordSkip() {
        filesSkipped++;
    }

    public void recordDeletion() {
        remoteNodesDeleted++;
    }

    public void recordFailure(String path, String operation, String message) {
        failures.add(new SyncFailure(path, operation, message));
        items.add(new SyncItemResult(path, operation, "FAILED", -1L, null, message));
    }

    public void recordInProgress(String path, String operation, long sizeInBytes, String remoteNodeId, String message) {
        items.add(new SyncItemResult(path, operation, "IN_PROGRESS", sizeInBytes, remoteNodeId, message));
    }

    public void recordItem(String path, String operation, String outcome, long sizeInBytes, String remoteNodeId, String message) {
        items.add(new SyncItemResult(path, operation, outcome, sizeInBytes, remoteNodeId, message));
    }

    public void complete() {
        completedAt = Instant.now();
    }
}
