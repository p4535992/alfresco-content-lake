package org.alfresco.contentlake.syncer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.model.api.StartSyncRequestDTO;
import org.alfresco.contentlake.syncer.client.AlfrescoHttpClient;
import org.alfresco.contentlake.syncer.model.SyncVersionType;
import org.alfresco.contentlake.syncer.model.RemoteNodeDTO;
import org.alfresco.contentlake.syncer.entity.SyncReport;
import org.alfresco.contentlake.syncer.entity.SyncState;
import org.alfresco.contentlake.syncer.entity.SyncStateEntry;
import org.alfresco.contentlake.syncer.report.CsvReportWriter;
import org.jboss.logging.Logger;

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

    private static final Logger LOG = Logger.getLogger(LocalFolderSyncService.class);
    private static final String FORCED_VERSION_COMMENT = "Forced new version by Alfresco Content Syncer";

    @Inject
    AlfrescoHttpClient alfrescoHttpClient;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SyncStateStore syncStateStore;

    @Inject
    CsvReportWriter csvReportWriter;

    public SyncReport sync(StartSyncRequestDTO request) throws IOException {
        return sync(request, ignored -> {
        });
    }

    public SyncReport sync(StartSyncRequestDTO request, Consumer<SyncReport> progressListener) throws IOException {
        request.validate();
        LOG.infof(
                "Starting folder sync localRoot=%s remoteRoot=%s dryRun=%s deleteRemoteMissing=%s forceNewVersion=%s forceVersionType=%s",
                request.localRootPath(),
                request.remoteRootNodeId,
                request.dryRun,
                request.deleteRemoteMissing,
                request.forceNewVersion,
                request.resolvedForceVersionType()
        );

        Path localRoot = request.localRootPath();
        RemoteNodeDTO remoteRoot = alfrescoHttpClient.getNode(request, request.remoteRootNodeId);
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

        Map<String, Map<String, RemoteNodeDTO>> childrenCache = new HashMap<>();

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
        LOG.infof(
                "Folder sync completed localRoot=%s remoteRoot=%s uploaded=%d updated=%d skipped=%d deleted=%d failures=%d",
                request.localRootPath(),
                request.remoteRootNodeId,
                report.getFilesUploaded(),
                report.getFilesUpdated(),
                report.getFilesSkipped(),
                report.getRemoteNodesDeleted(),
                report.getFailedCount()
        );
        return report;
    }

    private String ensureRemoteFolder(
            StartSyncRequestDTO request,
            Path relativeDir,
            Map<Path, String> remoteFolderIds,
            Map<String, Map<String, RemoteNodeDTO>> childrenCache,
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

        RemoteNodeDTO existing = loadChildren(request, parentId, childrenCache).get(folderName);
        if (existing != null) {
            if (!existing.folder()) {
                report.recordFailure(relativePath, "ensure-folder", "Remote node exists but is not a folder");
                LOG.warnf("Remote node exists but is not a folder for path=%s nodeId=%s", relativePath, existing.id());
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
                    .put(folderName, new RemoteNodeDTO(syntheticId, folderName, true, false, -1L, Instant.now()));
            report.recordItem(relativePath, "create-folder", "DRY_RUN", 0L, syntheticId, folderName);
            LOG.infof("Dry run folder creation relativePath=%s parentNodeId=%s", relativePath, parentId);
            publishProgress(progressListener, report);
            return syntheticId;
        }

        RemoteNodeDTO created = alfrescoHttpClient.createFolder(request, parentId, folderName);
        childrenCache.computeIfAbsent(parentId, ignored -> new LinkedHashMap<>()).put(folderName, created);
        report.recordItem(relativePath, "create-folder", "CREATED", 0L, created.id(), created.name());
        LOG.infof("Created remote folder relativePath=%s nodeId=%s", relativePath, created.id());
        publishProgress(progressListener, report);
        return created.id();
    }

    private void syncFile(
            StartSyncRequestDTO request,
            SyncState syncState,
            Map<String, String> checksumCache,
            Path localRoot,
            Path file,
            String remoteParentId,
            Map<String, Map<String, RemoteNodeDTO>> childrenCache,
            SyncReport report,
            Consumer<SyncReport> progressListener
    ) {
        String relativePath = localRoot.relativize(file.toAbsolutePath().normalize()).toString();
        String fileName = file.getFileName().toString();

        try {
            RemoteNodeDTO remoteNode = loadChildren(request, remoteParentId, childrenCache).get(fileName);
            long fileSize = Files.size(file);
            SyncStateEntry stateEntry = syncState.getEntries().get(relativePath);

            if (remoteNode == null) {
                report.recordInProgress(relativePath, "upload-file", fileSize, null, "Uploading file to Alfresco");
                publishProgress(progressListener, report);
                report.recordUpload(fileSize);
                if (!request.dryRun) {
                    RemoteNodeDTO uploaded = alfrescoHttpClient.uploadFile(request, remoteParentId, file);
                    childrenCache.computeIfAbsent(remoteParentId, ignored -> new LinkedHashMap<>()).put(fileName, uploaded);
                    storeState(syncState, checksumCache, relativePath, file, uploaded);
                    report.recordItem(relativePath, "upload-file", "UPLOADED", fileSize, uploaded.id(), uploaded.name());
                    LOG.infof("Uploaded file relativePath=%s size=%d nodeId=%s", relativePath, fileSize, uploaded.id());
                } else {
                    report.recordItem(relativePath, "upload-file", "DRY_RUN", fileSize, null, null);
                    LOG.infof("Dry run upload relativePath=%s size=%d", relativePath, fileSize);
                }
                publishProgress(progressListener, report);
                return;
            }

            if (!remoteNode.file()) {
                report.recordFailure(relativePath, "sync-file", "Remote node exists but is not a file");
                LOG.warnf("Remote node exists but is not a file for path=%s nodeId=%s", relativePath, remoteNode.id());
                publishProgress(progressListener, report);
                return;
            }

            if (shouldForceNewVersion(request, remoteNode)) {
                report.recordInProgress(relativePath, "update-file", fileSize, remoteNode.id(), "Forcing a new Alfresco version for the existing remote file");
                publishProgress(progressListener, report);
                report.recordUpdate(fileSize);
                if (!request.dryRun) {
                    SyncVersionType forceVersionType = request.resolvedForceVersionType();
                    RemoteNodeDTO updated = alfrescoHttpClient.updateFileContent(
                            request,
                            remoteNode.id(),
                            file,
                            forceVersionType.isMajor(),
                            forcedVersionComment(forceVersionType)
                    );
                    childrenCache.computeIfAbsent(remoteParentId, ignored -> new LinkedHashMap<>()).put(fileName, updated);
                    storeState(syncState, checksumCache, relativePath, file, updated);
                    report.recordItem(relativePath, "update-file", "UPDATED", fileSize, updated.id(), "Forced " + forceVersionType + " version");
                    LOG.infof("Forced %s version for file relativePath=%s size=%d nodeId=%s", forceVersionType, relativePath, fileSize, updated.id());
                } else {
                    report.recordItem(relativePath, "update-file", "DRY_RUN", fileSize, remoteNode.id(), "Would force " + request.resolvedForceVersionType() + " version");
                    LOG.infof("Dry run forced %s version relativePath=%s size=%d nodeId=%s", request.resolvedForceVersionType(), relativePath, fileSize, remoteNode.id());
                }
                publishProgress(progressListener, report);
            } else if (alreadyTransferredSuccessfully(file, remoteNode, stateEntry, checksumCache, relativePath)) {
                report.recordSkip();
                report.recordItem(relativePath, "skip-file", "SKIPPED", fileSize, remoteNode.id(), "Already transferred successfully; local checksum unchanged");
                LOG.infof("Skipped previously transferred file relativePath=%s size=%d nodeId=%s", relativePath, fileSize, remoteNode.id());
                if (!request.dryRun) {
                    storeState(syncState, checksumCache, relativePath, file, remoteNode);
                }
                publishProgress(progressListener, report);
            } else if (needsUpdate(file, remoteNode, stateEntry, checksumCache, relativePath)) {
                report.recordInProgress(relativePath, "update-file", fileSize, remoteNode.id(), "Updating file content in Alfresco");
                publishProgress(progressListener, report);
                report.recordUpdate(fileSize);
                if (!request.dryRun) {
                    RemoteNodeDTO updated = alfrescoHttpClient.updateFileContent(request, remoteNode.id(), file);
                    childrenCache.computeIfAbsent(remoteParentId, ignored -> new LinkedHashMap<>()).put(fileName, updated);
                    storeState(syncState, checksumCache, relativePath, file, updated);
                    report.recordItem(relativePath, "update-file", "UPDATED", fileSize, updated.id(), updated.name());
                    LOG.infof("Updated file relativePath=%s size=%d nodeId=%s", relativePath, fileSize, updated.id());
                } else {
                    report.recordItem(relativePath, "update-file", "DRY_RUN", fileSize, remoteNode.id(), remoteNode.name());
                    LOG.infof("Dry run update relativePath=%s size=%d nodeId=%s", relativePath, fileSize, remoteNode.id());
                }
                publishProgress(progressListener, report);
            } else {
                report.recordSkip();
                report.recordItem(relativePath, "skip-file", "SKIPPED", fileSize, remoteNode.id(), remoteNode.name());
                LOG.infof("Skipped file relativePath=%s size=%d nodeId=%s", relativePath, fileSize, remoteNode.id());
                if (!request.dryRun) {
                    String checksum = checksumCache.computeIfAbsent(relativePath, ignored -> sha256(file));
                    syncState.getEntries().put(relativePath, new SyncStateEntry(
                            relativePath,
                            remoteNode.id(),
                            fileSize,
                            checksum,
                            remoteNode.modifiedAt(),
                            Instant.now()
                    ));
                }
                publishProgress(progressListener, report);
            }
        } catch (Exception e) {
            report.recordFailure(relativePath, "sync-file", e.getMessage());
            LOG.warnf(e, "Failed to sync file relativePath=%s", relativePath);
            publishProgress(progressListener, report);
        }
    }

    private void reconcileRemoteChildren(
            StartSyncRequestDTO request,
            SyncState syncState,
            Path localRoot,
            Path localDirectory,
            String remoteFolderId,
            Map<String, Map<String, RemoteNodeDTO>> childrenCache,
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

            Map<String, RemoteNodeDTO> remoteChildren = loadChildren(request, remoteFolderId, childrenCache);
            for (RemoteNodeDTO remoteChild : remoteChildren.values().toArray(RemoteNodeDTO[]::new)) {
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
                    LOG.infof("Deleted remote node relativePath=%s nodeId=%s", relativePath, remoteChild.id());
                } else {
                    report.recordItem(relativePath, "delete-remote", "DRY_RUN", 0L, remoteChild.id(), remoteChild.name());
                    LOG.infof("Dry run delete relativePath=%s nodeId=%s", relativePath, remoteChild.id());
                }
                publishProgress(progressListener, report);
                remoteChildren.remove(remoteChild.name());
            }
        } catch (Exception e) {
            String relativePath = localRoot.relativize(localDirectory.toAbsolutePath().normalize()).toString();
            report.recordFailure(relativePath, "delete-remote-missing", e.getMessage());
            LOG.warnf(e, "Failed to reconcile remote children for path=%s", relativePath);
            publishProgress(progressListener, report);
        }
    }

    private Map<String, RemoteNodeDTO> loadChildren(
            StartSyncRequestDTO request,
            String parentId,
            Map<String, Map<String, RemoteNodeDTO>> childrenCache
    ) {
        if (parentId.startsWith("__dry_run__/")) {
            return childrenCache.computeIfAbsent(parentId, ignored -> new LinkedHashMap<>());
        }

        return childrenCache.computeIfAbsent(parentId, id -> {
            Map<String, RemoteNodeDTO> indexed = new LinkedHashMap<>();
            for (RemoteNodeDTO child : alfrescoHttpClient.listChildren(request, id)) {
                indexed.put(child.name(), child);
            }
            return indexed;
        });
    }

    void writeReportsIfRequested(StartSyncRequestDTO request, SyncReport report) throws IOException {
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
            RemoteNodeDTO remoteNode,
            SyncStateEntry stateEntry,
            Map<String, String> checksumCache,
            String relativePath
    ) throws IOException {
        long localSize = Files.size(localFile);
        if (stateEntry != null && stateEntry.getRemoteNodeId() != null && stateEntry.getRemoteNodeId().equals(remoteNode.id())) {
            if (localSize != stateEntry.getSizeInBytes()) {
                return true;
            }
            String localChecksum = checksumCache.computeIfAbsent(relativePath, ignored -> sha256(localFile));
            return !localChecksum.equals(stateEntry.getSha256());
        }
        return localSize != remoteNode.sizeInBytes();
    }

    boolean needsUpdate(Path localFile, RemoteNodeDTO remoteNode) throws IOException {
        return needsUpdate(localFile, remoteNode, null, new HashMap<>(), localFile.getFileName().toString());
    }

    private void storeState(
            SyncState syncState,
            Map<String, String> checksumCache,
            String relativePath,
            Path localFile,
            RemoteNodeDTO remoteNode
    ) {
        String checksum = checksumCache.computeIfAbsent(relativePath, ignored -> sha256(localFile));
        syncState.getEntries().put(relativePath, new SyncStateEntry(
                relativePath,
                remoteNode.id(),
                remoteNode.sizeInBytes(),
                checksum,
                remoteNode.modifiedAt(),
                Instant.now()
        ));
    }

    boolean alreadyTransferredSuccessfully(
            Path localFile,
            RemoteNodeDTO remoteNode,
            SyncStateEntry stateEntry,
            Map<String, String> checksumCache,
            String relativePath
    ) throws IOException {
        if (stateEntry == null || !remoteNode.file()) {
            return false;
        }
        if (stateEntry.getRemoteNodeId() == null || !stateEntry.getRemoteNodeId().equals(remoteNode.id())) {
            return false;
        }
        long localSize = Files.size(localFile);
        if (localSize != stateEntry.getSizeInBytes()) {
            return false;
        }
        if (remoteNode.sizeInBytes() != stateEntry.getSizeInBytes()) {
            return false;
        }
        String localChecksum = checksumCache.computeIfAbsent(relativePath, ignored -> sha256(localFile));
        return localChecksum.equals(stateEntry.getSha256());
    }

    boolean shouldForceNewVersion(StartSyncRequestDTO request, RemoteNodeDTO remoteNode) {
        return request.forceNewVersion && remoteNode != null && remoteNode.file();
    }

    private String forcedVersionComment(SyncVersionType forceVersionType) {
        return forceVersionType == SyncVersionType.MAJOR
                ? FORCED_VERSION_COMMENT + " (major)"
                : FORCED_VERSION_COMMENT + " (minor)";
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

    private Set<Path> buildIgnoredPaths(StartSyncRequestDTO request) {
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


