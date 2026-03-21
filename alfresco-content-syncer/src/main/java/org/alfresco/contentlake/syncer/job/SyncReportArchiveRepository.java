package org.alfresco.contentlake.syncer.job;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class SyncReportArchiveRepository {

    @Inject
    DataSource dataSource;

    @PostConstruct
    void init() {
        createTableIfMissing();
    }

    public void save(String jobId, String jsonReport, String csvReport) {
        String sql = """
                MERGE INTO sync_job_report (
                    job_id,
                    updated_at,
                    json_report,
                    csv_report
                ) KEY(job_id) VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.setString(3, jsonReport);
            statement.setString(4, csvReport);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to archive report for sync job " + jobId, e);
        }
    }

    public Optional<String> findJson(String jobId) {
        return findColumn(jobId, "json_report");
    }

    public Optional<String> findCsv(String jobId) {
        return findColumn(jobId, "csv_report");
    }

    private Optional<String> findColumn(String jobId, String columnName) {
        String sql = "SELECT " + columnName + " FROM sync_job_report WHERE job_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(resultSet.getString(columnName));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load archived report for sync job " + jobId, e);
        }
    }

    private void createTableIfMissing() {
        String sql = """
                CREATE TABLE IF NOT EXISTS sync_job_report (
                    job_id VARCHAR(128) PRIMARY KEY,
                    updated_at TIMESTAMP,
                    json_report CLOB,
                    csv_report CLOB
                )
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize sync_job_report table", e);
        }
    }
}


