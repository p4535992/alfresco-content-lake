package org.alfresco.contentlake.syncer.service;

import org.alfresco.contentlake.syncer.model.RemoteNode;
import org.alfresco.contentlake.syncer.model.SyncStateEntry;
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

        RemoteNode remoteNode = new RemoteNode("1", "test.txt", false, true, 99L, Instant.now());

        assertTrue(service.needsUpdate(file, remoteNode));
    }

    @Test
    void doesNotNeedUpdateWhenSizeMatchesAndRemoteIsNewer() throws Exception {
        Path file = Files.createTempFile("alfresco-content-syncer", ".txt");
        Files.writeString(file, "abc");
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));

        RemoteNode remoteNode = new RemoteNode(
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

        RemoteNode remoteNode = new RemoteNode(
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
                Instant.parse("2026-01-02T00:00:00Z")
        );

        assertFalse(service.needsUpdate(file, remoteNode, stateEntry, new HashMap<>(), "test.txt"));
    }
}
