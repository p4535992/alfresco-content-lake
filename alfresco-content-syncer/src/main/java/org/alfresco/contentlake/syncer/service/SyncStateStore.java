package org.alfresco.contentlake.syncer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.entity.SyncState;
import org.alfresco.contentlake.syncer.entity.SyncStateEntry;
import org.alfresco.contentlake.syncer.model.api.SyncStateEntryDTO;
import org.alfresco.contentlake.syncer.model.api.SyncStateViewDTO;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

import javax.sql.DataSource;

@ApplicationScoped
public class SyncStateStore {

    private static final Logger LOG = Logger.getLogger(SyncStateStore.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "syncer.data-dir", defaultValue = ".syncer-data")
    String dataDir;

    @PostConstruct
    void init() {
        createTableIfMissing();
        importLegacyStates();
    }

    public SyncState load(String remoteRootNodeId) {
        String sql = "SELECT payload_json FROM sync_state WHERE remote_root_node_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, remoteRootNodeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new SyncState();
                }
                return objectMapper.readValue(resultSet.getString("payload_json"), SyncState.class);
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Failed to read sync state for " + remoteRootNodeId, e);
        }
    }

    public void save(String remoteRootNodeId, SyncState state) {
        String sql = """
                MERGE INTO sync_state (
                    remote_root_node_id,
                    payload_json
                ) KEY(remote_root_node_id) VALUES (?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, remoteRootNodeId);
            statement.setString(2, objectMapper.writeValueAsString(state));
            statement.executeUpdate();
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Failed to write sync state for " + remoteRootNodeId, e);
        }
    }

    public SyncStateViewDTO view(String remoteRootNodeId) {
        SyncState state = load(remoteRootNodeId);
        List<SyncStateEntryDTO> entries = state.getEntries().values().stream()
                .sorted(Comparator.comparing(SyncStateEntry::getRelativePath, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(entry -> new SyncStateEntryDTO(
                        entry.getRelativePath(),
                        entry.getRemoteNodeId(),
                        entry.getSizeInBytes(),
                        entry.getSha256(),
                        entry.getRemoteModifiedAt(),
                        entry.getLastTransferredAt()
                ))
                .toList();
        return new SyncStateViewDTO(remoteRootNodeId, entries.size(), entries);
    }

    public void clear(String remoteRootNodeId) {
        String sql = "DELETE FROM sync_state WHERE remote_root_node_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, remoteRootNodeId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear sync state for " + remoteRootNodeId, e);
        }
    }

    private void createTableIfMissing() {
        String sql = """
                CREATE TABLE IF NOT EXISTS sync_state (
                    remote_root_node_id VARCHAR(512) PRIMARY KEY,
                    payload_json CLOB NOT NULL
                )
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize sync_state table", e);
        }
    }

    private void importLegacyStates() {
        Path statesDir = Path.of(dataDir).toAbsolutePath().normalize().resolve("states");
        if (!Files.exists(statesDir)) {
            return;
        }

        try (var stream = Files.list(statesDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String remoteRootNodeId = fileName.substring(0, fileName.length() - ".json".length());
                        try {
                            SyncState state = objectMapper.readValue(path.toFile(), SyncState.class);
                            save(remoteRootNodeId, state);
                            LOG.infof("Imported legacy sync state %s from %s", remoteRootNodeId, path);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to import legacy sync state " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to import legacy sync states", e);
        }
    }
}


