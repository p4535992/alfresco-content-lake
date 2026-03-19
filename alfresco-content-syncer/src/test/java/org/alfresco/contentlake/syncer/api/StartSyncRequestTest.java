package org.alfresco.contentlake.syncer.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartSyncRequestTest {

    @Test
    void rejectsBaseUrlWithoutAlfrescoSuffix() {
        StartSyncRequest request = new StartSyncRequest();
        request.alfrescoBaseUrl = "http://localhost:8080";
        request.username = "admin";
        request.password = "admin";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, request::validateConnection);

        assertTrue(error.getMessage().contains("/alfresco"));
    }

    @Test
    void appliesDefaultCsvReportOutputWhenBlank() {
        StartSyncRequest request = new StartSyncRequest();

        request.applyDefaultReportOutput("job-123");

        assertTrue(request.reportOutput.endsWith("alfresco-content-sync-report-job-123.csv"));
        assertTrue(Path.of(request.reportOutput).isAbsolute());
    }
}
