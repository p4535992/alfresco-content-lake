package org.alfresco.contentlake.syncer.job;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.api.StartSyncRequest;
import org.alfresco.contentlake.syncer.model.SyncJob;
import org.alfresco.contentlake.syncer.model.SyncReport;
import org.alfresco.contentlake.syncer.service.LocalFolderSyncService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class SyncJobService {

    @Inject
    LocalFolderSyncService localFolderSyncService;

    private final Map<String, SyncJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executorService;

    public SyncJobService(@ConfigProperty(name = "syncer.jobs.max-concurrent", defaultValue = "2") int maxConcurrent) {
        this.executorService = Executors.newFixedThreadPool(Math.max(1, maxConcurrent));
    }

    public SyncJob start(StartSyncRequest request) {
        request.validate();

        String jobId = UUID.randomUUID().toString();
        SyncJob job = new SyncJob(
                jobId,
                request.localRootPath().toString(),
                request.remoteRootNodeId,
                request.dryRun,
                request.deleteRemoteMissing
        );
        jobs.put(jobId, job);

        executorService.submit(() -> runJob(job, request));
        return job;
    }

    public SyncJob get(String jobId) {
        return jobs.get(jobId);
    }

    public Collection<SyncJob> list() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(SyncJob::getCreatedAt).reversed())
                .toList();
    }

    private void runJob(SyncJob job, StartSyncRequest request) {
        job.markRunning();
        try {
            SyncReport report = localFolderSyncService.sync(request);
            job.markCompleted(report);
        } catch (Exception e) {
            SyncReport partialReport = new SyncReport(
                    request.localRootPath().toString(),
                    request.remoteRootNodeId,
                    request.dryRun,
                    request.deleteRemoteMissing
            );
            partialReport.recordFailure(request.localRootPath().toString(), "sync-job", e.getMessage());
            partialReport.complete();
            job.markFailed(e.getMessage(), partialReport);
        }
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdownNow();
    }
}
