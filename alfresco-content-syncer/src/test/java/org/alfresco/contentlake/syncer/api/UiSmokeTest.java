package org.alfresco.contentlake.syncer.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class UiSmokeTest {

    @Test
    void servesMainUiWithSyncControls() {
        given()
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .body(containsString("forceVersionButton"))
                .body(containsString("exportReportsCsvButton"))
                .body(containsString("reportStatusFilter"));
    }

    @Test
    void servesSettingsPage() {
        given()
                .when()
                .get("/settings.html")
                .then()
                .statusCode(200)
                .body(containsString("saveSettingsButton"))
                .body(containsString("backToMainButton"));
    }
}


