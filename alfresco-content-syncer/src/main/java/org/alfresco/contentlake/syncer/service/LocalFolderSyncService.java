package org.alfresco.contentlake.syncer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.api.StartSyncRequest;
import org.alfresco.contentlake.syncer.client.AlfrescoHttpClient;
import org.alfresco.contentlake.syncer.model.RemoteNode;
import org.alfresco.contentlake.syncer.model.SyncReport;
import org.alfresco.contentlake.syncer.model.SyncState;
import org.alfresco.contentlake.syncer.model.SyncStateEntry;
import org.alfresco.contentlake.syncer.report.CsvReportWriter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@ApplicationScoped
public class LocalFolderSyncService {

    @Inject
    AlfrescoHttpClient alfrescoHttpClient;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SyncStateStore syncStateStore;

    @Inject
    CsvReportWriter csvReportWriter;

    public SyncReport sync(StartSyncRequest request) throws IOException {
        return sync(request, ignored -> {
        });
    }

    public SyncReport sync(StartSyncRequest request, Consumer<SyncReport> progressListener) throws IOException {
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
        report.recordItem("/", "validate-root", "OK", 0L, remoteRoot.id(), remoteRoot.name());
        publishProgress(progressListener, report);

        SyncState syncState = syncStateStore.load(request.remoteRootNodeId);
        Map<String, String> checksumCache = new HashMap<>();
        Map<Path, String> remoteFolderIds = new HashMap<>();
        remoteFolderIds.put(Path.of(""), request.remoteRootNodeId);
        Set<Path> ignoredPaths = buildIgnoredPaths(request);

        Map<String, Map<String, RemoteNode>> childrenCache = new HashMap<>();

        Files.walkFileTree(localRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (shouldIgnore(dir, ignoredPaths)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path relativeDir = localRoot.relativize(dir);
                String remoteFolderId = ensureRemoteFolder(
                        request,
                        relativeDir,
                        remoteFolderIds,
                        childrenCache,
                        report,
                        progressListener
                );
                remoteFolderIds.put(relativeDir, remoteFolderId);
                report.incrementDirectoriesScanned();
                publishProgress(progressListener, report);

                if (request.deleteRemoteMissing) {
                    reconcileRemoteChildren(
                            request,
                            syncState,
                            localRoot,
                            dir,
                            remoteFolderId,
                            childrenCache,
                            report,
                            progressListener
                    );
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                if (shouldIgnore(file, ignoredPaths)) {
                    return FileVisitResult.CONTINUE;
                }

                report.incrementFilesScanned();
                publishProgress(progressListener, report);
                Path parentRelative = localRoot.relativize(file.getParent());
                String remoteParentId = remoteFolderIds.get(parentRelative);
                syncFile(
                        request,
                        syncState,
                        checksumCache,
                        localRoot,
                        file,
                        remoteParentId,
                        childrenCache,
                        report,
                        progressListener
                );
                return FileVisitResult.CONTINUE;
            }
        });

        report.complete();
        publishProgress(progressListener, report);
        if (!request.dryRun) {
            syncStateStore.save(request.remoteRootNodeId, syncState);
        }
        writeReportsIfRequested(request, report);
        return report;
    }

    private String ensureRemoteFolder(
            StartSyncRequest request,
            Path relativeDir,
            Map<Path, String> remoteFolderIds,
            Map<String, Map<String, RemoteNode>> childrenCache,
            SyncReport report,
            Consumer<SyncReport> progressListener
    ) {
        if (relativeDir.getNameCount() == 0) {
            return request.remoteRootNodeId;
        }

        Path parentRelative = relativeDir.getParent() == null ? Path.of("") : relativeDir.getParent();
        String parentId = remoteFolderIds.get(parentRelative);
        String folderName = relativeDir.getFileName().toString();
        String relativePath = relativeDir.toString();

        RemoteNode existing = loadChildren(request, parentId, childrenCache).get(folderName);
        if (existing != null) {
            if (!existing.folder()) {
                report.recordFailure(relativePath, "ensure-folder", "Remote node exists but is not a folder");
                publishProgress(progressListener, report);
                throw new IllegalStateException("Remote node exists but is not a folder: " + relativeDir);
            }
            report.recordItem(relativePath, "ensure-folder", "EXISTS", 0L, existing.id(), existing.name());
            publishProgress(progressListener, report);
            return existing.id();
        }

        report.recordFolderCreated();
        if (request.dryRun) {
            String syntheticId = "__dry_run__/" + relativeDir;
            childrenCache.computeIfAbsent(parentId, ignored -> new LinkedHashMap<>())
                    .put(folderName, new RemoteNode(syntheticId, folderName, true, false, -1L, Instant.now()));
            report.recordItem(relativePath, "create-folder", "DRY_RUN", 0L, syntheticId, folderName);
            publishProgress(progressListener, report);
            return syntheticId;
        }

        RemoteNode created = alfrescoHttpClient.createFolder(request, parentId, folderName);
        childrenCache.computeIfAbsent(parentId, ignored -> new LinkedHashMap<>()).put(folderName, created);
        report.recordItem(relativePath, "create-folder", "CREATED", 0L, created.id(), created.name());
        publishProgress(progressListener, report);
        return created.id();
    }

    private void syncFile(
            StartSyncRequest request,
            SyncState syncState,
            Map<String, String> checksumCache,
            Path localRoot,
            Path file,
            String remoteParentId,
            Map<String, Map<String, RemoteNode>> childrenCache,
            SyncReport report,
            Consumer<SyncReport> progressListener
    ) {
        String relativePath = localRoot.relativize(file.toAbsolutePath().normalize()).toString();
        String fileName = file.getFileName().toString();

        try {
            RemoteNode remoteNode = loadChildren(request, remoteParentId, childrenCache).get(fileName);
            long fileSize = Files.size(file);

            if (remoteNode == null) {
                report.recordUpload(fileSize);
                if (!request.dryRun) {
                    RemoteNode uploaded = alfrescoHttpClient.uploadFile(request, remoteParentId, file);
                    childrenCache.computeIfAbsent(remoteParentId, ignored -> new LinkedHashMap<>()).put(fileName, uploaded);
                    storeState(syncState, checksumCache, relativePath, file, uploaded);
                    report.recordItem(relativePath, "upload-file", "UPLOADED", fileSize, uploaded.id(), uploaded.name());
                } else {
                    report.recordItem(relativePath, "upload-file", "DRY_RUN", fileSize, null, null);
                }
                publishProgress(progressListener, report);
                return;
            }

            if (!remoteNode.file()) {
                report.recordFailure(relativePath, "sync-file", "Remote node exists but is not a file");
                publishProgress(progressListener, report);
                return;
            }

            if (needsUpdate(file, remoteNode, syncState.getEntries().get(relativePath), checksumCache, relativePath)) {
                report.recordUpdate(fileSize);
                if (!request.dryRun) {
                    RemoteNode updated = alfrescoHttpClient.updateFileContent(request, remoteNode.id(), file);
                    childrenCache.computeIfAbsent(remoteParentId, ignored -> new LinkedHashMap<>()).put(fileName, updated);
                    storeState(syncState, checksumCache, relativePath, file, updated);
                    report.recordItem(relativePath, "update-file", "UPDATED", fileSize, updated.id(), updated.name());
                } else {
                    report.recordItem(relativePath, "update-file", "DRY_RUN", fileSize, remoteNode.id(), remoteNode.name());
                }
                publishProgress(progressListener, report);
            } else {
                report.recordSkip();
                report.recordItem(relativePath, "skip-file", "SKIPPED", fileSize, remoteNode.id(), remoteNode.name());
                if (!request.dryRun) {
                    String checksum = checksumCache.computeIfAbsent(relativePath, ignored -> sha256(file));
                    syncState.getEntries().put(relativePath, new SyncStateEntry(
                            relativePath,
                            remoteNode.id(),
                            fileSize,
                            checksum,
                            remoteNode.modifiedAt()
                    ));
                }
                publishProgress(progressListener, report);
            }
        } catch (Exception e) {
            report.recordFailure(relativePath, "sync-file", e.getMessage());
            publishProgress(progressListener, report);
        }
    }

    private void reconcileRemoteChildren(
            StartSyncRequest request,
            SyncState syncState,
            Path localRoot,
            Path localDirectory,
            String remoteFolderId,
            Map<String, Map<String, RemoteNode>> childrenCache,
            SyncReport report,
            Consumer<SyncReport> progressListener
    ) {
        try {
            Set<String> localNames = new HashSet<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(localDirectory)) {
                for (Path entry : stream) {
                    if (shouldIgnore(entry, buildIgnoredPaths(request))) {
                        continue;
                    }
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
                    removeStatePrefix(syncState, relativePath);
                    report.recordItem(relativePath, "delete-remote", "DELETED", 0L, remoteChild.id(), remoteChild.name());
                } else {
                    report.recordItem(relativePath, "delete-remote", "DRY_RUN", 0L, remoteChild.id(), remoteChild.name());
                }
                publishProgress(progressListener, report);
                remoteChildren.remove(remoteChild.name());
            }
        } catch (Exception e) {
            String relativePath = localRoot.relativize(localDirectory.toAbsolutePath().normalize()).toString();
            report.recordFailure(relativePath, "delete-remote-missing", e.getMessage());
            publishProgress(progressListener, report);
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

    void writeReportsIfRequested(StartSyncRequest request, SyncReport report) throws IOException {
        Path reportOutput = request.reportOutputPath();
        if (reportOutput == null) {
            return;
        }

        String fileName = reportOutput.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".csv")) {
            writeTextReport(reportOutput, csvReportWriter.write(report));
            return;
        }

        writeTextReport(reportOutput, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        String csvName = reportOutput.getFileName().toString().replaceFirst("(\\.[^.]+)?$", ".csv");
        writeTextReport(reportOutput.resolveSibling(csvName), csvReportWriter.write(report));
    }

    private void writeTextReport(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content);
    }

    private void publishProgress(Consumer<SyncReport> progressListener, SyncReport report) {
        progressListener.accept(report);
    }

    boolean needsUpdate(
            Path localFile,
            RemoteNode remoteNode,
            SyncStateEntry stateEntry,
            Map<String, String> checksumCache,
            String relativePath
    ) throws IOException {
        long localSize = Files.size(localFile);
        return localSize != remoteNode.sizeInBytes();
    }

    boolean needsUpdate(Path localFile, RemoteNode remoteNode) throws IOException {
        return needsUpdate(localFile, remoteNode, null, new HashMap<>(), localFile.getFileName().toString());
    }

    private void storeState(
            SyncState syncState,
            Map<String, String> checksumCache,
            String relativePath,
            Path localFile,
            RemoteNode remoteNode
    ) {
        String checksum = checksumCache.computeIfAbsent(relativePath, ignored -> sha256(localFile));
        syncState.getEntries().put(relativePath, new SyncStateEntry(
                relativePath,
                remoteNode.id(),
                remoteNode.sizeInBytes(),
                checksum,
                remoteNode.modifiedAt()
        ));
    }

    private void removeStatePrefix(SyncState syncState, String relativePath) {
        syncState.getEntries().keySet().removeIf(path -> path.equals(relativePath) || path.startsWith(relativePath + java.io.File.separator));
    }

    private String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute SHA-256 for " + file, e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private Set<Path> buildIgnoredPaths(StartSyncRequest request) {
        Set<Path> ignoredPaths = new HashSet<>();
        Path reportOutput = request.reportOutputPath();
        if (reportOutput == null) {
            return ignoredPaths;
        }

        ignoredPaths.add(reportOutput.toAbsolutePath().normalize());
        String fileName = reportOutput.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".csv")) {
            String csvName = reportOutput.getFileName().toString().replaceFirst("(\\.[^.]+)?$", ".csv");
            ignoredPaths.add(reportOutput.resolveSibling(csvName).toAbsolutePath().normalize());
        }
        return ignoredPaths;
    }

    private boolean shouldIgnore(Path candidate, Set<Path> ignoredPaths) {
        Path normalized = candidate.toAbsolutePath().normalize();
        return ignoredPaths.contains(normalized);
    }
}
