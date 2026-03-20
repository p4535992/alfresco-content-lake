package org.alfresco.contentlake.syncer.api;

public record RuntimeSettingsResponse(
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
