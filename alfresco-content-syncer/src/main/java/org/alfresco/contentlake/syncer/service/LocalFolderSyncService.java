package org.alfresco.contentlake.syncer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.api.StartSyncRequest;
import org.alfresco.contentlake.syncer.client.AlfrescoHttpClient;
import org.alfresco.contentlake.syncer.model.RemoteNode;
import org.alfresco.contentlake.syncer.model.SyncReport;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class LocalFolderSyncService {

    @Inject
    AlfrescoHttpClient alfrescoHttpClient;

    @Inject
    ObjectMapper objectMapper;

    public SyncReport sync(StartSyncRequest request) throws IOException {
        request.validate();

        Path localRoot = request.localRootPath();
        RemoteNode remoteRoot = alfrescoHttpClient.getNode(request, request.remoteRootNodeId);
        if (remoteRoot == null || !remoteRoot.folder()) {
            throw new IllegalArgumentException("Remote root node is not a folder: " + request.remoteRootNodeId);
        }

        SyncReport report = new SyncReport(
                localRoot.toString(),
                request.remoteRootNodeId,
                request.dryRun,
                request.deleteRemoteMissing
        );

        Map<Path, String> remoteFolderIds = new HashMap<>();
        remoteFolderIds.put(Path.of(""), request.remoteRootNodeId);

        Map<String, Map<String, RemoteNode>> childrenCache = new HashMap<>();

        Files.walkFileTree(localRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path relativeDir = localRoot.relativize(dir);
                String remoteFolderId = ensureRemoteFolder(request, relativeDir, remoteFolderIds, childrenCache, report);
                remoteFolderIds.put(relativeDir, remoteFolderId);
                report.incrementDirectoriesScanned();

                if (request.deleteRemoteMissing) {
                    reconcileRemoteChildren(request, localRoot, dir, remoteFolderId, childrenCache, report);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }

                report.incrementFilesScanned();
                Path parentRelative = localRoot.relativize(file.getParent());
                String remoteParentId = remoteFolderIds.get(parentRelative);
                syncFile(request, localRoot, file, remoteParentId, childrenCache, report);
                return FileVisitResult.CONTINUE;
            }
        });

        report.complete();
        writeReportIfRequested(request, report);
        return report;
    }

    private String ensureRemoteFolder(
            StartSyncRequest request,
            Path relativeDir,
            Map<Path, String> remoteFolderIds,
            Map<String, Map<String, RemoteNode>> childrenCache,
            SyncReport report
    ) {
        if (relativeDir.getNameCount() == 0) {
            return request.remoteRootNodeId;
        }

        Path parentRelative = relativeDir.getParent() == null ? Path.of("") : relativeDir.getParent();
        String parentId = remoteFolderIds.get(parentRelative);
        String folderName = relativeDir.getFileName().toString();

        RemoteNode existing = loadChildren(request, parentId, childrenCache).get(folderName);
        if (existing != null) {
            if (!existing.folder()) {
                report.recordFailure(relativeDir.toString(), "ensure-folder", "Remote node exists but is not a folder");
                throw new IllegalStateException("Remote node exists but is not a folder: " + relativeDir);
            }
            return existing.id();
        }

        report.recordFolderCreated();
        if (request.dryRun) {
            String syntheticId = "__dry_run__/" + relativeDir;
            childrenCache.computeIfAbsent(parentId, ignored -> new LinkedHashMap<>())
                    .put(folderName, new RemoteNode(syntheticId, folderName, true, false, -1L, Instant.now()));
            return syntheticId;
        }

        RemoteNode created = alfrescoHttpClient.createFolder(request, parentId, folderName);
        childrenCache.computeIfAbsent(parentId, ignored -> new LinkedHashMap<>()).put(folderName, created);
        return created.id();
    }

    private void syncFile(
            StartSyncRequest request,
            Path localRoot,
            Path file,
            String remoteParentId,
            Map<String, Map<String, RemoteNode>> childrenCache,
            SyncReport report
    ) {
        String fileName = file.getFileName().toString();
        String relativePath = localRoot.relativize(file.toAbsolutePath().normalize()).toString();

        try {
            RemoteNode remoteNode = loadChildren(request, remoteParentId, childrenCache).get(fileName);
            long fileSize = Files.size(file);

            if (remoteNode == null) {
                report.recordUpload(fileSize);
                if (!request.dryRun) {
                    RemoteNode uploaded = alfrescoHttpClient.uploadFile(request, remoteParentId, file);
                    childrenCache.computeIfAbsent(remoteParentId, ignored -> new LinkedHashMap<>()).put(fileName, uploaded);
                }
                return;
            }

            if (!remoteNode.file()) {
                report.recordFailure(relativePath, "sync-file", "Remote node exists but is not a file");
                return;
            }

            if (needsUpdate(file, remoteNode)) {
                report.recordUpdate(fileSize);
                if (!request.dryRun) {
                    RemoteNode updated = alfrescoHttpClient.updateFileContent(request, remoteNode.id(), file);
                    childrenCache.computeIfAbsent(remoteParentId, ignored -> new LinkedHashMap<>()).put(fileName, updated);
                }
            } else {
                report.recordSkip();
            }
        } catch (Exception e) {
            report.recordFailure(relativePath, "sync-file", e.getMessage());
        }
    }

    private void reconcileRemoteChildren(
            StartSyncRequest request,
            Path localRoot,
            Path localDirectory,
            String remoteFolderId,
            Map<String, Map<String, RemoteNode>> childrenCache,
            SyncReport report
    ) {
        try {
            Set<String> localNames = new HashSet<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(localDirectory)) {
                for (Path entry : stream) {
                    localNames.add(entry.getFileName().toString());
                }
            }

            Map<String, RemoteNode> remoteChildren = loadChildren(request, remoteFolderId, childrenCache);
            for (RemoteNode remoteChild : remoteChildren.values().toArray(RemoteNode[]::new)) {
                if (localNames.contains(remoteChild.name())) {
                    continue;
                }

                String relativePath = localRoot.relativize(localDirectory.toAbsolutePath().normalize())
                        .resolve(remoteChild.name())
                        .toString();
                report.recordDeletion();
                if (!request.dryRun) {
                    alfrescoHttpClient.deleteNode(request, remoteChild.id());
                }
                remoteChildren.remove(remoteChild.name());
            }
        } catch (Exception e) {
            String relativePath = localRoot.relativize(localDirectory.toAbsolutePath().normalize()).toString();
            report.recordFailure(relativePath, "delete-remote-missing", e.getMessage());
        }
    }

    private Map<String, RemoteNode> loadChildren(
            StartSyncRequest request,
            String parentId,
            Map<String, Map<String, RemoteNode>> childrenCache
    ) {
        if (parentId.startsWith("__dry_run__/")) {
            return childrenCache.computeIfAbsent(parentId, ignored -> new LinkedHashMap<>());
        }

        return childrenCache.computeIfAbsent(parentId, id -> {
            Map<String, RemoteNode> indexed = new LinkedHashMap<>();
            for (RemoteNode child : alfrescoHttpClient.listChildren(request, id)) {
                indexed.put(child.name(), child);
            }
            return indexed;
        });
    }

    void writeReportIfRequested(StartSyncRequest request, SyncReport report) throws IOException {
        Path reportOutput = request.reportOutputPath();
        if (reportOutput == null) {
            return;
        }
        Path parent = reportOutput.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(reportOutput, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }

    boolean needsUpdate(Path localFile, RemoteNode remoteNode) throws IOException {
        long localSize = Files.size(localFile);
        if (localSize != remoteNode.sizeInBytes()) {
            return true;
        }

        Instant remoteModifiedAt = remoteNode.modifiedAt();
        if (remoteModifiedAt == null) {
            return true;
        }

        Instant localModifiedAt = Files.getLastModifiedTime(localFile).toInstant();
        return localModifiedAt.isAfter(remoteModifiedAt);
    }
}
