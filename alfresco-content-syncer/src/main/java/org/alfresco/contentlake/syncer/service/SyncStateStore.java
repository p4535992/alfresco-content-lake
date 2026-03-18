package org.alfresco.contentlake.syncer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.model.SyncState;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class SyncStateStore {

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "syncer.data-dir", defaultValue = ".syncer-data")
    String dataDir;

    public SyncState load(String remoteRootNodeId) {
        Path statePath = statePath(remoteRootNodeId);
        if (!Files.exists(statePath)) {
            return new SyncState();
        }

        try {
            return objectMapper.readValue(statePath.toFile(), SyncState.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read sync state " + statePath, e);
        }
    }

    public void save(String remoteRootNodeId, SyncState state) {
        Path statePath = statePath(remoteRootNodeId);
        try {
            Files.createDirectories(statePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(statePath.toFile(), state);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write sync state " + statePath, e);
        }
    }

    private Path statePath(String remoteRootNodeId) {
        return Path.of(dataDir).toAbsolutePath().normalize()
                .resolve("states")
                .resolve(sanitize(remoteRootNodeId) + ".json");
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
