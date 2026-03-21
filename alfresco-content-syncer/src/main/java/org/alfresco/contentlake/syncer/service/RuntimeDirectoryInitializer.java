package org.alfresco.contentlake.syncer.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class RuntimeDirectoryInitializer {

    private static final Logger LOG = Logger.getLogger(RuntimeDirectoryInitializer.class);

    @ConfigProperty(name = "syncer.logs.dir")
    String logsDir;

    @ConfigProperty(name = "syncer.data-dir")
    String dataDir;

    @ConfigProperty(name = "syncer.jobrunr.data-dir")
    String jobrunrDataDir;

    @Inject
    RuntimeSettingsService runtimeSettingsService;

    void onStart(@Observes StartupEvent ignored) {
        createDirectory(logsDir);
        createDirectory(dataDir);
        createDirectory(jobrunrDataDir);
        createDirectory(runtimeSettingsService.configDirectoryPath().toString());
    }

    private void createDirectory(String directory) {
        try {
            Files.createDirectories(Path.of(directory).toAbsolutePath().normalize());
        } catch (IOException e) {
            LOG.warnf(e, "Failed to create runtime directory %s", directory);
        }
    }
}

