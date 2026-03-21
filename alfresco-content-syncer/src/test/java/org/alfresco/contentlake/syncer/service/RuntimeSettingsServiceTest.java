package org.alfresco.contentlake.syncer.service;

import org.alfresco.contentlake.syncer.model.api.RuntimeSettingsResponseDTO;
import org.alfresco.contentlake.syncer.model.api.UpdateRuntimeSettingsRequestDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSettingsServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void savesExternalOverrideFile() throws Exception {
        RuntimeSettingsService service = new RuntimeSettingsService();
        service.runtimeRoot = tempDir.toString();
        service.httpPort = 9093;
        service.openBrowserOnStartup = true;
        service.dataStorageRoot = tempDir.toString();
        service.logsDir = tempDir.resolve("logs").toString();
        service.dataDir = tempDir.resolve(".syncer-data").toString();
        service.jobrunrDataDir = tempDir.resolve("data").toString();

        UpdateRuntimeSettingsRequestDTO request = new UpdateRuntimeSettingsRequestDTO();
        request.httpPort = 9191;
        request.openBrowserOnStartup = false;
        request.dataStorageRoot = tempDir.resolve("portable-data").toString();

        RuntimeSettingsResponseDTO response = service.save(request);

        Path configFile = tempDir.resolve("config").resolve("application.properties");
        assertTrue(Files.exists(configFile));
        String content = Files.readString(configFile);
        assertTrue(content.contains("quarkus.http.port=9191"));
        assertTrue(content.contains("syncer.ui.open-browser-on-startup=false"));
        assertTrue(content.contains("syncer.storage-root="));
        assertEquals(9191, response.httpPort());
        assertTrue(response.externalConfigPresent());
        assertTrue(response.restartRequired());
        assertTrue(response.dataDir().endsWith("/portable-data/.syncer-data"));
        assertTrue(response.jobrunrDataDir().endsWith("/portable-data/data"));
    }

    @Test
    void loadReflectsExternalConfigPresence() {
        RuntimeSettingsService service = new RuntimeSettingsService();
        service.runtimeRoot = tempDir.toString();
        service.httpPort = 9093;
        service.openBrowserOnStartup = true;
        service.dataStorageRoot = tempDir.toString();
        service.logsDir = tempDir.resolve("logs").toString();
        service.dataDir = tempDir.resolve(".syncer-data").toString();
        service.jobrunrDataDir = tempDir.resolve("data").toString();

        RuntimeSettingsResponseDTO before = service.load();
        assertFalse(before.externalConfigPresent());
    }

    @Test
    void rejectsInvalidPort() {
        RuntimeSettingsService service = new RuntimeSettingsService();
        service.runtimeRoot = tempDir.toString();

        UpdateRuntimeSettingsRequestDTO request = new UpdateRuntimeSettingsRequestDTO();
        request.httpPort = 70000;
        request.openBrowserOnStartup = true;
        request.dataStorageRoot = tempDir.toString();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(request));
        assertTrue(error.getMessage().contains("httpPort"));
    }
}


