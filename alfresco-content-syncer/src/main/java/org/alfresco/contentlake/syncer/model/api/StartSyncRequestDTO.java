package org.alfresco.contentlake.syncer.model.api;

import org.alfresco.contentlake.syncer.model.SyncVersionType;

import java.nio.file.Files;
import java.nio.file.Path;

public class StartSyncRequestDTO extends AlfrescoConnectionPayloadDTO {

    public String localRoot;
    public String remoteRootNodeId;
    public boolean dryRun;
    public boolean deleteRemoteMissing;
    public boolean forceNewVersion;
    public SyncVersionType forceVersionType = SyncVersionType.defaultValue();
    public String reportOutput;

    public void validate() {
        if (isBlank(localRoot)) {
            throw new IllegalArgumentException("localRoot is required");
        }
        Path localRootPath = Path.of(localRoot).toAbsolutePath().normalize();
        if (!Files.exists(localRootPath)) {
            throw new IllegalArgumentException("localRoot does not exist: " + localRootPath);
        }
        if (!Files.isDirectory(localRootPath)) {
            throw new IllegalArgumentException("localRoot is not a directory: " + localRootPath);
        }
        if (isBlank(remoteRootNodeId)) {
            throw new IllegalArgumentException("remoteRootNodeId is required");
        }
        if (forceVersionType == null) {
            forceVersionType = SyncVersionType.defaultValue();
        }
        validateConnection();
    }

    public Path localRootPath() {
        return Path.of(localRoot).toAbsolutePath().normalize();
    }

    public Path reportOutputPath() {
        return isBlank(reportOutput) ? null : Path.of(reportOutput).toAbsolutePath().normalize();
    }

    public void applyDefaultReportOutput(String jobId) {
        if (!isBlank(reportOutput)) {
            reportOutput = reportOutputPath().toString();
            return;
        }
        reportOutput = Path.of("")
                .toAbsolutePath()
                .normalize()
                .resolve("alfresco-content-sync-report-" + jobId + ".csv")
                .toString();
    }

    public SyncVersionType resolvedForceVersionType() {
        return forceVersionType != null ? forceVersionType : SyncVersionType.defaultValue();
    }
}



