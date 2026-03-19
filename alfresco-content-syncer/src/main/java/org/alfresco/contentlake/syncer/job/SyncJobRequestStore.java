package org.alfresco.contentlake.syncer.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.api.StartSyncRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class SyncJobRequestStore {

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "syncer.data-dir")
    String dataDir;

    public void save(String jobId, StartSyncRequest request) {
        try {
            Files.createDirectories(requestsDir());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(requestPath(jobId).toFile(), request);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist sync job request " + jobId, e);
        }
    }

    public StartSyncRequest load(String jobId) {
        Path requestPath = requestPath(jobId);
        if (!Files.exists(requestPath)) {
            throw new IllegalStateException("Sync job request not found for " + jobId);
        }
        try {
            return objectMapper.readValue(requestPath.toFile(), StartSyncRequest.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read sync job request " + jobId, e);
        }
    }

    private Path requestsDir() {
        return Path.of(dataDir).toAbsolutePath().normalize().resolve("requests");
    }

    private Path requestPath(String jobId) {
        return requestsDir().resolve(jobId + ".json");
    }
}
