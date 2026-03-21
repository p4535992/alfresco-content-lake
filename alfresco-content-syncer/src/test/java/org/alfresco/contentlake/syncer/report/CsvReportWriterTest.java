package org.alfresco.contentlake.syncer.report;

import org.alfresco.contentlake.syncer.model.SyncReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvReportWriterTest {

    private final CsvReportWriter csvReportWriter = new CsvReportWriter();

    @Test
    void writesSummaryAndDetailedRows() {
        SyncReport report = new SyncReport("D:\\data", "remote-folder", false, false);
        report.recordUpload(128);
        report.recordItem("contracts\\a.pdf", "upload-file", "UPLOADED", 128L, "node-1", "a.pdf");
        report.recordFailure("contracts\\broken.pdf", "sync-file", "Access denied");
        report.complete();

        String csv = csvReportWriter.write(report);

        assertTrue(csv.contains("summary,\"localRoot\",\"D:\\data\""));
        assertTrue(csv.contains("summary,\"filesUploaded\",\"1\""));
        assertTrue(csv.contains("details,\"contracts\\a.pdf\",\"upload-file\",\"UPLOADED\",128,\"node-1\",\"a.pdf\""));
        assertTrue(csv.contains("details,\"contracts\\broken.pdf\",\"sync-file\",\"FAILED\",-1,\"\",\"Access denied\""));
    }
}

