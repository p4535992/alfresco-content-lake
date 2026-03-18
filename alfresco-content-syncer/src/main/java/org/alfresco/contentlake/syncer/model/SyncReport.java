package org.alfresco.contentlake.syncer.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SyncReport {

    private final String localRoot;
    private final String remoteRootNodeId;
    private final boolean dryRun;
    private final boolean deleteRemoteMissing;
    private final Instant startedAt = Instant.now();
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

    private final List<SyncFailure> failures = new ArrayList<>();

    public SyncReport(String localRoot, String remoteRootNodeId, boolean dryRun, boolean deleteRemoteMissing) {
        this.localRoot = localRoot;
        this.remoteRootNodeId = remoteRootNodeId;
        this.dryRun = dryRun;
        this.deleteRemoteMissing = deleteRemoteMissing;
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

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public int getDirectoriesScanned() {
        return directoriesScanned;
    }

    public int getFilesScanned() {
        return filesScanned;
    }

    public int getFoldersCreated() {
        return foldersCreated;
    }

    public int getFilesUploaded() {
        return filesUploaded;
    }

    public int getFilesUpdated() {
        return filesUpdated;
    }

    public int getFilesSkipped() {
        return filesSkipped;
    }

    public int getRemoteNodesDeleted() {
        return remoteNodesDeleted;
    }

    public long getUploadedBytes() {
        return uploadedBytes;
    }

    public long getUpdatedBytes() {
        return updatedBytes;
    }

    public List<SyncFailure> getFailures() {
        return failures;
    }

    public int getFailedCount() {
        return failures.size();
    }

    public long getDurationMs() {
        Instant end = completedAt != null ? completedAt : Instant.now();
        return Duration.between(startedAt, end).toMillis();
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
    }

    public void complete() {
        completedAt = Instant.now();
    }
}
