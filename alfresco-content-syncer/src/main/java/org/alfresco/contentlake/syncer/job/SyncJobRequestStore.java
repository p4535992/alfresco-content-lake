package org.alfresco.contentlake.syncer.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.api.StartSyncRequest;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

@ApplicationScoped
public class SyncJobRequestStore {

    private static final Logger LOG = Logger.getLogger(SyncJobRequestStore.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "syncer.data-dir")
    String dataDir;

    @PostConstruct
    void init() {
        createTableIfMissing();
        importLegacyRequests();
    }

    public void save(String jobId, StartSyncRequest request) {
        String sql = """
                MERGE INTO sync_job_request (
                    job_id,
                    created_at,
                    payload_json
                ) KEY(job_id) VALUES (?, ?, ?)
                """;
        try {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, jobId);
                statement.setTimestamp(2, Timestamp.from(Instant.now()));
                statement.setString(3, objectMapper.writeValueAsString(request));
                statement.executeUpdate();
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Failed to persist sync job request " + jobId, e);
        }
    }

    public StartSyncRequest load(String jobId) {
        String sql = "SELECT payload_json FROM sync_job_request WHERE job_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Sync job request not found for " + jobId);
                }
                return objectMapper.readValue(resultSet.getString("payload_json"), StartSyncRequest.class);
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Failed to read sync job request " + jobId, e);
        }
    }

    private void createTableIfMissing() {
        String sql = """
                CREATE TABLE IF NOT EXISTS sync_job_request (
                    job_id VARCHAR(128) PRIMARY KEY,
                    created_at TIMESTAMP,
                    payload_json CLOB NOT NULL
                )
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize sync_job_request table", e);
        }
    }

    private void importLegacyRequests() {
        Path legacyRequestsDir = requestsDir();
        if (!Files.exists(legacyRequestsDir)) {
            return;
        }

        try (var stream = Files.list(legacyRequestsDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String jobId = fileName.substring(0, fileName.length() - ".json".length());
                        try {
                            StartSyncRequest request = objectMapper.readValue(path.toFile(), StartSyncRequest.class);
                            save(jobId, request);
                            LOG.infof("Imported legacy sync job request %s from %s", jobId, path);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to import legacy sync job request " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to import legacy sync job requests", e);
        }
    }

    private Path requestsDir() {
        return Path.of(dataDir).toAbsolutePath().normalize().resolve("requests");
    }
}
