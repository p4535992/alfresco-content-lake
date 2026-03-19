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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
        refreshPersistedJobs();
        return jobs.get(jobId);
    }

    public Collection<SyncJob> list() {
        refreshPersistedJobs();
        return jobs.values().stream()
                .sorted(Comparator.comparing(SyncJob::getCreatedAt).reversed())
                .toList();
    }

    private void runJob(SyncJob job, StartSyncRequest request) {
        job.markRunning();
        job.setReport(new SyncReport(
                request.localRootPath().toString(),
                request.remoteRootNodeId,
                request.dryRun,
                request.deleteRemoteMissing
        ));
        persistJob(job);

        AtomicReference<SyncReport> latestReport = new AtomicReference<>(snapshotReport(job.getReport()));
        AtomicLong lastProgressPersistAt = new AtomicLong(System.currentTimeMillis());
        try {
            SyncReport report = localFolderSyncService.sync(request, currentReport -> {
                SyncReport snapshot = snapshotReport(currentReport);
                latestReport.set(snapshot);
                job.setReport(snapshot);
                persistProgress(job, lastProgressPersistAt, snapshot.getCompletedAt() != null);
            });
            job.markCompleted(report);
            persistJob(job);
        } catch (Exception e) {
            SyncReport partialReport = latestReport.get();
            if (partialReport == null) {
                partialReport = new SyncReport(
                        request.localRootPath().toString(),
                        request.remoteRootNodeId,
                        request.dryRun,
                        request.deleteRemoteMissing
                );
            }
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

    private void refreshPersistedJobs() {
        Path jobsDir = jobsDir();
        if (!Files.exists(jobsDir)) {
            return;
        }

        try (var stream = Files.list(jobsDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            SyncJob persistedJob = objectMapper.readValue(path.toFile(), SyncJob.class);
                            jobs.put(persistedJob.getJobId(), persistedJob);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to refresh persisted job " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to refresh persisted jobs", e);
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

    private void persistProgress(SyncJob job, AtomicLong lastProgressPersistAt, boolean force) {
        long now = System.currentTimeMillis();
        long previous = lastProgressPersistAt.get();
        if (!force && now - previous < 500) {
            return;
        }
        if (lastProgressPersistAt.compareAndSet(previous, now) || force) {
            persistJob(job);
        }
    }

    private SyncReport snapshotReport(SyncReport report) {
        return objectMapper.convertValue(report, SyncReport.class);
    }

    @PreDestroy
    void shutdown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
