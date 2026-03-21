package org.alfresco.contentlake.syncer.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.entity.SyncState;
import org.alfresco.contentlake.syncer.entity.SyncStateEntry;
import org.alfresco.contentlake.syncer.job.SyncJobRepository;
import org.alfresco.contentlake.syncer.job.SyncReportArchiveRepository;
import org.alfresco.contentlake.syncer.entity.SyncJob;
import org.alfresco.contentlake.syncer.entity.SyncReport;
import org.alfresco.contentlake.syncer.service.SyncStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class SyncResourceApiTest {

    @Inject
    SyncJobRepository syncJobRepository;

    @Inject
    SyncReportArchiveRepository syncReportArchiveRepository;

    @Inject
    SyncStateStore syncStateStore;

    @BeforeEach
    void setUp() {
        SyncReport report = new SyncReport("D:/data", "remote-root", false, false);
        report.recordItem("contracts/a.pdf", "upload-file", "UPLOADED", 128L, "node-1", "a.pdf");
        report.complete();

        SyncJob job = new SyncJob("job-report-json", "D:/data", "remote-root", "D:/reports/job-report-json.csv", false, false);
        job.markCompleted(report);
        syncJobRepository.save(job);
        syncReportArchiveRepository.save("job-report-json", """
                {
                  "localRoot": "D:/data",
                  "remoteRootNodeId": "remote-root",
                  "items": [
                    {
                      "path": "contracts/a.pdf",
                      "operation": "upload-file",
                      "outcome": "UPLOADED"
                    }
                  ]
                }
                """, "details,\"contracts/a.pdf\",\"upload-file\",\"UPLOADED\",128,\"node-1\",\"a.pdf\"");

        SyncReport failedReport = new SyncReport("D:/failed-data", "remote-failed", false, false);
        failedReport.recordItem("contracts/b.pdf", "upload-file", "FAILED", 64L, null, "upload failed");
        failedReport.complete();
        SyncJob failedJob = new SyncJob("job-report-failed", "D:/failed-data", "remote-failed", "D:/reports/job-report-failed.csv", false, false);
        failedJob.markFailed("upload failed", failedReport);
        syncJobRepository.save(failedJob);
        syncReportArchiveRepository.save("job-report-failed", """
                {
                  "localRoot": "D:/failed-data",
                  "remoteRootNodeId": "remote-failed",
                  "items": [
                    {
                      "path": "contracts/b.pdf",
                      "operation": "upload-file",
                      "outcome": "FAILED"
                    }
                  ]
                }
                """, "details,\"contracts/b.pdf\",\"upload-file\",\"FAILED\",64,\"\",\"upload failed\"");

        SyncState syncState = new SyncState();
        syncState.getEntries().put("contracts/a.pdf", new SyncStateEntry(
                "contracts/a.pdf",
                "node-1",
                128L,
                "abc123",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:05:00Z")
        ));
        syncStateStore.save("remote-root", syncState);
    }

    @Test
    void downloadsArchivedJsonReport() {
        given()
                .when()
                .get("/api/sync/jobs/job-report-json/report.json")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("localRoot", equalTo("D:/data"))
                .body("items[0].path", equalTo("contracts/a.pdf"))
                .body("items[0].outcome", equalTo("UPLOADED"));
    }

    @Test
    void downloadsArchivedCsvReport() {
        given()
                .when()
                .get("/api/sync/jobs/job-report-json/report.csv")
                .then()
                .statusCode(200)
                .contentType(containsString("text/csv"))
                .body(containsString("contracts/a.pdf"))
                .body(containsString("UPLOADED"));
    }

    @Test
    void listsArchivedReports() {
        given()
                .when()
                .get("/api/sync/reports")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("[0].jobId", equalTo("job-report-failed"))
                .body("[0].status", equalTo("FAILED"))
                .body("[1].jobId", equalTo("job-report-json"))
                .body("[1].status", equalTo("COMPLETED"));
    }

    @Test
    void returnsTrackedStateForRemoteRoot() {
        given()
                .when()
                .get("/api/sync/state/remote-root")
                .then()
                .statusCode(200)
                .body("remoteRootNodeId", equalTo("remote-root"))
                .body("entryCount", equalTo(1))
                .body("entries[0].relativePath", equalTo("contracts/a.pdf"))
                .body("entries[0].remoteNodeId", equalTo("node-1"));
    }

    @Test
    void clearsTrackedStateForRemoteRoot() {
        given()
                .when()
                .delete("/api/sync/state/remote-root")
                .then()
                .statusCode(204);

        given()
                .when()
                .get("/api/sync/state/remote-root")
                .then()
                .statusCode(200)
                .body("entryCount", equalTo(0))
                .body("entries", hasSize(0));
    }
}


