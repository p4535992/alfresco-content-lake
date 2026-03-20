package org.alfresco.contentlake.syncer.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class DesktopResourceApiTest {

    @Test
    void returnsRuntimeSettings() {
        given()
                .when()
                .get("/api/system/settings")
                .then()
                .statusCode(200)
                .body("httpPort", notNullValue())
                .body("dataStorageRoot", notNullValue())
                .body("configFilePath", endsWith("/config/application.properties"))
                .body("restartRequired", org.hamcrest.Matchers.equalTo(true));
    }
}
