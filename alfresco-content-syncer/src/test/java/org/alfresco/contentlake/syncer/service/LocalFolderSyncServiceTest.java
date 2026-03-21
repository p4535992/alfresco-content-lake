package org.alfresco.contentlake.syncer.service;

import org.alfresco.contentlake.syncer.model.api.StartSyncRequestDTO;
import org.alfresco.contentlake.syncer.model.RemoteNodeDTO;
import org.alfresco.contentlake.syncer.model.SyncVersionType;
import org.alfresco.contentlake.syncer.entity.SyncStateEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFolderSyncServiceTest {

    LocalFolderSyncService service = new LocalFolderSyncService();

    @Test
    void needsUpdateWhenSizeDiffers() throws Exception {
        Path file = Files.createTempFile("alfresco-content-syncer", ".txt");
        Files.writeString(file, "abc");

        RemoteNodeDTO remoteNode = new RemoteNodeDTO("1", "test.txt", false, true, 99L, Instant.now());

        assertTrue(service.needsUpdate(file, remoteNode));
    }

    @Test
    void doesNotNeedUpdateWhenSizeMatchesAndRemoteIsNewer() throws Exception {
        Path file = Files.createTempFile("alfresco-content-syncer", ".txt");
        Files.writeString(file, "abc");
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));

        RemoteNodeDTO remoteNode = new RemoteNodeDTO(
                "1",
                "test.txt",
                false,
                true,
                3L,
                Instant.parse("2026-01-02T00:00:00Z")
        );

        assertFalse(service.needsUpdate(file, remoteNode));
    }

    @Test
    void doesNotNeedUpdateWhenOnlyLocalTimestampChangedAndChecksumMatchesPersistedState() throws Exception {
        Path file = Files.createTempFile("alfresco-content-syncer", ".txt");
        Files.writeString(file, "abc");
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-01-03T00:00:00Z")));

        RemoteNodeDTO remoteNode = new RemoteNodeDTO(
                "1",
                "test.txt",
                false,
                true,
                3L,
                Instant.parse("2026-01-02T00:00:00Z")
        );

        SyncStateEntry stateEntry = new SyncStateEntry(
                "test.txt",
                "1",
                3L,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-03T00:00:00Z")
        );

        assertFalse(service.needsUpdate(file, remoteNode, stateEntry, new HashMap<>(), "test.txt"));
    }

    @Test
    void doesNotNeedUpdateWhenSizeMatchesEvenIfLocalTimestampIsNewer() throws Exception {
        Path file = Files.createTempFile("alfresco-content-syncer", ".txt");
        Files.writeString(file, "abc");
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-01-05T00:00:00Z")));

        RemoteNodeDTO remoteNode = new RemoteNodeDTO(
                "1",
                "test.txt",
                false,
                true,
                3L,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        assertFalse(service.needsUpdate(file, remoteNode));
    }

    @Test
    void needsUpdateWhenChecksumChangedEvenIfSizeMatchesAndStateExists() throws Exception {
        Path file = Files.createTempFile("alfresco-content-syncer", ".txt");
        Files.writeString(file, "xyz");

        RemoteNodeDTO remoteNode = new RemoteNodeDTO(
                "1",
                "test.txt",
                false,
                true,
                3L,
                Instant.parse("2026-01-02T00:00:00Z")
        );

        SyncStateEntry stateEntry = new SyncStateEntry(
                "test.txt",
                "1",
                3L,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-03T00:00:00Z")
        );

        assertTrue(service.needsUpdate(file, remoteNode, stateEntry, new HashMap<>(), "test.txt"));
    }

    @Test
    void detectsAlreadyTransferredSuccessfullyFromPersistedState() throws Exception {
        Path file = Files.createTempFile("alfresco-content-syncer", ".txt");
        Files.writeString(file, "abc");

        RemoteNodeDTO remoteNode = new RemoteNodeDTO(
                "1",
                "test.txt",
                false,
                true,
                3L,
                Instant.parse("2026-01-02T00:00:00Z")
        );

        SyncStateEntry stateEntry = new SyncStateEntry(
                "test.txt",
                "1",
                3L,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-03T00:00:00Z")
        );

        assertTrue(service.alreadyTransferredSuccessfully(file, remoteNode, stateEntry, new HashMap<>(), "test.txt"));
    }

    @Test
    void canForceNewVersionForExistingRemoteFile() {
        StartSyncRequestDTO request = new StartSyncRequestDTO();
        request.forceNewVersion = true;
        request.forceVersionType = SyncVersionType.MAJOR;

        RemoteNodeDTO remoteNode = new RemoteNodeDTO(
                "1",
                "test.txt",
                false,
                true,
                3L,
                Instant.parse("2026-01-02T00:00:00Z")
        );

        assertTrue(service.shouldForceNewVersion(request, remoteNode));
        assertTrue(request.resolvedForceVersionType().isMajor());
    }
}


