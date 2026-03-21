package org.alfresco.contentlake.syncer.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.http.ContentType.JSON;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
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

    @Test
    void savesRuntimeSettings() {
        given()
                .contentType(JSON)
                .body("""
                        {
                          "httpPort": 9191,
                          "openBrowserOnStartup": false,
                          "dataStorageRoot": "target/test-storage-post"
                        }
                        """)
                .when()
                .post("/api/system/settings")
                .then()
                .statusCode(200)
                .body("httpPort", equalTo(9191))
                .body("openBrowserOnStartup", equalTo(false))
                .body("dataStorageRoot", endsWith("/target/test-storage-post"))
                .body("externalConfigPresent", equalTo(true))
                .body("configFilePath", endsWith("/config/application.properties"));
    }
}


