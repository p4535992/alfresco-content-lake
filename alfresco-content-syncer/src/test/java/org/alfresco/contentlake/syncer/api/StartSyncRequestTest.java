package org.alfresco.contentlake.syncer.api;

import org.alfresco.contentlake.syncer.model.SyncVersionType;
import org.alfresco.contentlake.syncer.model.api.StartSyncRequestDTO;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StartSyncRequestTest {

    @Test
    void rejectsBaseUrlWithoutAlfrescoSuffix() {
        StartSyncRequestDTO request = new StartSyncRequestDTO();
        request.alfrescoBaseUrl = "http://localhost:8080";
        request.username = "admin";
        request.password = "admin";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, request::validateConnection);

        assertTrue(error.getMessage().contains("/alfresco"));
    }

    @Test
    void appliesDefaultCsvReportOutputWhenBlank() {
        StartSyncRequestDTO request = new StartSyncRequestDTO();

        request.applyDefaultReportOutput("job-123");

        assertTrue(request.reportOutput.endsWith("alfresco-content-sync-report-job-123.csv"));
        assertTrue(Path.of(request.reportOutput).isAbsolute());
    }

    @Test
    void defaultsForceVersionTypeToMinor() {
        StartSyncRequestDTO request = new StartSyncRequestDTO();

        assertEquals(SyncVersionType.MINOR, request.resolvedForceVersionType());
    }
}


