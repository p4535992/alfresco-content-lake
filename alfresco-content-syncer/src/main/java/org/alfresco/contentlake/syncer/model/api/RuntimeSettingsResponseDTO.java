package org.alfresco.contentlake.syncer.model.api;

public record RuntimeSettingsResponseDTO(
        int httpPort,
        boolean openBrowserOnStartup,
        String dataStorageRoot,
        String logsDir,
        String dataDir,
        String jobrunrDataDir,
        String configFilePath,
        boolean externalConfigPresent,
        boolean restartRequired
) {
}


