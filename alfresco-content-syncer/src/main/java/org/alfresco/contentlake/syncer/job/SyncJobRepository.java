package org.alfresco.contentlake.syncer.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.entity.SyncJob;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SyncJobRepository {

    private static final Logger LOG = Logger.getLogger(SyncJobRepository.class);

    @Inject
    DataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "syncer.data-dir")
    String dataDir;

    @PostConstruct
    void init() {
        createTableIfMissing();
        importLegacyJobs();
    }

    public void save(SyncJob job) {
        String sql = """
                MERGE INTO sync_job (
                    job_id,
                    jobrunr_id,
                    status,
                    created_at,
                    started_at,
                    completed_at,
                    local_root,
                    remote_root_node_id,
                    report_output,
                    dry_run,
                    delete_remote_missing,
                    error_message,
                    payload_json
                ) KEY(job_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, job.getJobId());
            statement.setString(2, job.getJobRunrId());
            statement.setString(3, job.getStatus() != null ? job.getStatus().name() : null);
            statement.setTimestamp(4, toTimestamp(job.getCreatedAt()));
            statement.setTimestamp(5, toTimestamp(job.getStartedAt()));
            statement.setTimestamp(6, toTimestamp(job.getCompletedAt()));
            statement.setString(7, job.getLocalRoot());
            statement.setString(8, job.getRemoteRootNodeId());
            statement.setString(9, job.getReportOutput());
            statement.setBoolean(10, job.isDryRun());
            statement.setBoolean(11, job.isDeleteRemoteMissing());
            statement.setString(12, job.getErrorMessage());
            statement.setString(13, serialize(job));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist sync job " + job.getJobId(), e);
        }
    }

    public Optional<SyncJob> findById(String jobId) {
        String sql = "SELECT payload_json FROM sync_job WHERE job_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(deserializeJob(resultSet.getString("payload_json")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load sync job " + jobId, e);
        }
    }

    public List<SyncJob> findAll() {
        String sql = "SELECT payload_json FROM sync_job ORDER BY created_at DESC, job_id DESC";
        List<SyncJob> jobs = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                jobs.add(deserializeJob(resultSet.getString("payload_json")));
            }
            return jobs;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load sync jobs", e);
        }
    }

    private void createTableIfMissing() {
        String sql = """
                CREATE TABLE IF NOT EXISTS sync_job (
                    job_id VARCHAR(128) PRIMARY KEY,
                    jobrunr_id VARCHAR(128),
                    status VARCHAR(32),
                    created_at TIMESTAMP,
                    started_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    local_root VARCHAR(4096),
                    remote_root_node_id VARCHAR(512),
                    report_output VARCHAR(4096),
                    dry_run BOOLEAN,
                    delete_remote_missing BOOLEAN,
                    error_message CLOB,
                    payload_json CLOB NOT NULL
                )
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize sync_job table", e);
        }
    }

    private void importLegacyJobs() {
        Path legacyJobsDir = Path.of(dataDir).toAbsolutePath().normalize().resolve("jobs");
        if (!Files.exists(legacyJobsDir)) {
            return;
        }

        try (var stream = Files.list(legacyJobsDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            SyncJob job = objectMapper.readValue(path.toFile(), SyncJob.class);
                            save(job);
                            LOG.infof("Imported legacy sync job %s from %s", job.getJobId(), path);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to read legacy sync job " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to import legacy sync jobs", e);
        }
    }

    private String serialize(SyncJob job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize sync job " + job.getJobId(), e);
        }
    }

    private SyncJob deserializeJob(String payload) {
        try {
            return objectMapper.readValue(payload, SyncJob.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize sync job payload", e);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}


