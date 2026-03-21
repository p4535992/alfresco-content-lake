package org.alfresco.contentlake.syncer.report;

import jakarta.enterprise.context.ApplicationScoped;
import org.alfresco.contentlake.syncer.model.SyncItemResultDTO;
import org.alfresco.contentlake.syncer.model.SyncReport;

@ApplicationScoped
public class CsvReportWriter {

    public String write(SyncReport report) {
        StringBuilder csv = new StringBuilder();

        csv.append("section,key,value\n");
        summary(csv, "localRoot", report.getLocalRoot());
        summary(csv, "remoteRootNodeId", report.getRemoteRootNodeId());
        summary(csv, "dryRun", Boolean.toString(report.isDryRun()));
        summary(csv, "deleteRemoteMissing", Boolean.toString(report.isDeleteRemoteMissing()));
        summary(csv, "startedAt", String.valueOf(report.getStartedAt()));
        summary(csv, "completedAt", String.valueOf(report.getCompletedAt()));
        summary(csv, "durationMs", Long.toString(report.getDurationMs()));
        summary(csv, "directoriesScanned", Integer.toString(report.getDirectoriesScanned()));
        summary(csv, "filesScanned", Integer.toString(report.getFilesScanned()));
        summary(csv, "foldersCreated", Integer.toString(report.getFoldersCreated()));
        summary(csv, "filesUploaded", Integer.toString(report.getFilesUploaded()));
        summary(csv, "filesUpdated", Integer.toString(report.getFilesUpdated()));
        summary(csv, "filesSkipped", Integer.toString(report.getFilesSkipped()));
        summary(csv, "remoteNodesDeleted", Integer.toString(report.getRemoteNodesDeleted()));
        summary(csv, "failedCount", Integer.toString(report.getFailedCount()));
        summary(csv, "uploadedBytes", Long.toString(report.getUploadedBytes()));
        summary(csv, "updatedBytes", Long.toString(report.getUpdatedBytes()));

        csv.append("\n");
        csv.append("details,path,operation,outcome,sizeInBytes,remoteNodeId,message\n");
        for (SyncItemResultDTO item : report.getItems()) {
            csv.append("details,")
                    .append(cell(item.getPath())).append(',')
                    .append(cell(item.getOperation())).append(',')
                    .append(cell(item.getOutcome())).append(',')
                    .append(item.getSizeInBytes()).append(',')
                    .append(cell(item.getRemoteNodeId())).append(',')
                    .append(cell(item.getMessage()))
                    .append('\n');
        }

        return csv.toString();
    }

    private void summary(StringBuilder csv, String key, String value) {
        csv.append("summary,")
                .append(cell(key)).append(',')
                .append(cell(value))
                .append('\n');
    }

    private String cell(String value) {
        if (value == null) {
            return "\"\"";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}

