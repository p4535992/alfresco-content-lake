package org.alfresco.contentlake.syncer.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.job.SyncJobRepository;
import org.alfresco.contentlake.syncer.job.SyncReportArchiveRepository;
import org.alfresco.contentlake.syncer.model.SyncJob;
import org.alfresco.contentlake.syncer.model.SyncReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class SyncResourceApiTest {

    @Inject
    SyncJobRepository syncJobRepository;

    @Inject
    SyncReportArchiveRepository syncReportArchiveRepository;

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
}
