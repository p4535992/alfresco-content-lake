package org.alfresco.contentlake.syncer.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.api.StartSyncRequest;
import org.alfresco.contentlake.syncer.model.SyncJob;
import org.alfresco.contentlake.syncer.model.SyncReport;
import org.alfresco.contentlake.syncer.service.LocalFolderSyncService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "syncer.jobs.max-concurrent", defaultValue = "2")
    int maxConcurrent;

    @ConfigProperty(name = "syncer.data-dir", defaultValue = ".syncer-data")
    String dataDir;

    private final Map<String, SyncJob> jobs = new ConcurrentHashMap<>();
    private ExecutorService executorService;

    @PostConstruct
    void init() {
        executorService = Executors.newFixedThreadPool(Math.max(1, maxConcurrent));
        loadPersistedJobs();
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
        persistJob(job);

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
        persistJob(job);
        try {
            SyncReport report = localFolderSyncService.sync(request);
            job.markCompleted(report);
            persistJob(job);
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
            persistJob(job);
        }
    }

    private void loadPersistedJobs() {
        Path jobsDir = jobsDir();
        if (!Files.exists(jobsDir)) {
            return;
        }

        try (var stream = Files.list(jobsDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            SyncJob job = objectMapper.readValue(path.toFile(), SyncJob.class);
                            jobs.put(job.getJobId(), job);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to read persisted job " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read persisted jobs", e);
        }
    }

    private void persistJob(SyncJob job) {
        try {
            Files.createDirectories(jobsDir());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jobPath(job.getJobId()).toFile(), job);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist job " + job.getJobId(), e);
        }
    }

    private Path jobsDir() {
        return Path.of(dataDir).toAbsolutePath().normalize().resolve("jobs");
    }

    private Path jobPath(String jobId) {
        return jobsDir().resolve(jobId + ".json");
    }

    @PreDestroy
    void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
