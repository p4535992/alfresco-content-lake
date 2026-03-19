package org.alfresco.contentlake.syncer.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.api.StartSyncRequest;
import org.alfresco.contentlake.syncer.model.SyncJob;
import org.alfresco.contentlake.syncer.model.SyncJobStatus;
import org.alfresco.contentlake.syncer.model.SyncReport;
import org.jboss.logging.Logger;
import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobBuilder;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.storage.JobNotFoundException;
import org.jobrunr.storage.StorageProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SyncJobService {

    private static final Logger LOG = Logger.getLogger(SyncJobService.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SyncJobRequestStore syncJobRequestStore;

    @Inject
    JobRequestScheduler jobRequestScheduler;

    @Inject
    StorageProvider storageProvider;

    @ConfigProperty(name = "syncer.data-dir")
    String dataDir;

    private final Map<String, SyncJob> jobs = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        loadPersistedJobs();
    }

    public SyncJob start(StartSyncRequest request) {
        String jobId = UUID.randomUUID().toString();
        request.applyDefaultReportOutput(jobId);
        request.validate();

        SyncJob job = new SyncJob(
                jobId,
                request.localRootPath().toString(),
                request.remoteRootNodeId,
                request.reportOutputPath().toString(),
                request.dryRun,
                request.deleteRemoteMissing
        );

        syncJobRequestStore.save(jobId, request);
        String jobRunrId = String.valueOf(jobRequestScheduler.create(JobBuilder.aJob()
                .withName("Alfresco content sync " + jobId)
                .withAmountOfRetries(0)
                .withJobRequest(new SyncJobRunRequest(jobId))));
        job.setJobRunrId(jobRunrId);
        jobs.put(jobId, job);
        persistJob(job);
        LOG.infof(
                "Queued JobRunr sync job %s jobRunrId=%s localRoot=%s remoteRoot=%s reportOutput=%s",
                jobId,
                jobRunrId,
                request.localRootPath(),
                request.remoteRootNodeId,
                request.reportOutputPath()
        );
        return job;
    }

    public SyncJob get(String jobId) {
        refreshPersistedJobs();
        SyncJob job = jobs.get(jobId);
        syncStatusFromJobRunr(job);
        return job;
    }

    public Collection<SyncJob> list() {
        refreshPersistedJobs();
        jobs.values().forEach(this::syncStatusFromJobRunr);
        return jobs.values().stream()
                .sorted(Comparator.comparing(SyncJob::getCreatedAt).reversed())
                .toList();
    }

    public void markRunning(String jobId, StartSyncRequest request) {
        SyncJob job = requireJob(jobId);
        job.markRunning();
        job.setErrorMessage(null);
        job.setReport(new SyncReport(
                request.localRootPath().toString(),
                request.remoteRootNodeId,
                request.dryRun,
                request.deleteRemoteMissing
        ));
        persistJob(job);
        LOG.infof("Sync job %s is running", jobId);
    }

    public void updateProgress(String jobId, SyncReport currentReport) {
        SyncJob job = requireJob(jobId);
        job.setReport(snapshotReport(currentReport));
        persistJob(job);
    }

    public void markCompleted(String jobId, SyncReport report) {
        SyncJob job = requireJob(jobId);
        job.markCompleted(report);
        persistJob(job);
        LOG.infof("Sync job %s completed", jobId);
    }

    public void markFailed(String jobId, StartSyncRequest request, Exception error, SyncReport partialReport) {
        SyncJob job = requireJob(jobId);
        SyncReport failureReport = partialReport != null
                ? snapshotReport(partialReport)
                : new SyncReport(
                request.localRootPath().toString(),
                request.remoteRootNodeId,
                request.dryRun,
                request.deleteRemoteMissing
        );
        failureReport.recordFailure(request.localRootPath().toString(), "sync-job", error.getMessage());
        failureReport.complete();
        job.markFailed(error.getMessage(), failureReport);
        persistJob(job);
        LOG.errorf(error, "Sync job %s failed: %s", jobId, error.getMessage());
    }

    private SyncJob requireJob(String jobId) {
        refreshPersistedJobs();
        SyncJob job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalStateException("Sync job not found: " + jobId);
        }
        return job;
    }

    private void syncStatusFromJobRunr(SyncJob job) {
        if (job == null || job.getJobRunrId() == null || job.getJobRunrId().isBlank()) {
            return;
        }
        try {
            StateName state = storageProvider.getJobById(JobId.parse(job.getJobRunrId())).getState();
            SyncJobStatus mappedStatus = mapState(state);
            if (mappedStatus != null && mappedStatus != job.getStatus()) {
                job.setStatus(mappedStatus);
                persistJob(job);
            }
        } catch (JobNotFoundException | IllegalArgumentException e) {
            LOG.debugf(e, "Unable to sync JobRunr status for sync job %s", job.getJobId());
        }
    }

    private SyncJobStatus mapState(StateName state) {
        if (state == null) {
            return null;
        }
        return switch (state) {
            case ENQUEUED, SCHEDULED, AWAITING -> SyncJobStatus.QUEUED;
            case PROCESSING -> SyncJobStatus.RUNNING;
            case SUCCEEDED -> SyncJobStatus.COMPLETED;
            case FAILED, DELETED -> SyncJobStatus.FAILED;
        };
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

    private SyncReport snapshotReport(SyncReport report) {
        return objectMapper.convertValue(report, SyncReport.class);
    }
}
