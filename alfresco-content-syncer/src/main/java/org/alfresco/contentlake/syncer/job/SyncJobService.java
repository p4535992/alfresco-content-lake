package org.alfresco.contentlake.syncer.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.model.api.StartSyncRequestDTO;
import org.alfresco.contentlake.syncer.model.api.JobRunrSummaryResponseDTO;
import org.alfresco.contentlake.syncer.model.SyncJob;
import org.alfresco.contentlake.syncer.model.SyncJobStatus;
import org.alfresco.contentlake.syncer.model.SyncReport;
import org.jboss.logging.Logger;
import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobBuilder;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.storage.JobNotFoundException;
import org.jobrunr.storage.JobStats;
import org.jobrunr.storage.StorageProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;

@ApplicationScoped
public class SyncJobService {

    private static final Logger LOG = Logger.getLogger(SyncJobService.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SyncJobRequestStore syncJobRequestStore;

    @Inject
    SyncJobRepository syncJobRepository;

    @Inject
    JobRequestScheduler jobRequestScheduler;

    @Inject
    StorageProvider storageProvider;

    @Inject
    SyncReportArchiveRepository syncReportArchiveRepository;

    @Inject
    org.alfresco.contentlake.syncer.report.CsvReportWriter csvReportWriter;

    @ConfigProperty(name = "syncer.report-store.enabled", defaultValue = "true")
    boolean reportStoreEnabled;

    public SyncJob start(StartSyncRequestDTO request) {
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
        job.setForceNewVersion(request.forceNewVersion);

        syncJobRequestStore.save(jobId, request);
        String jobRunrId = String.valueOf(jobRequestScheduler.create(JobBuilder.aJob()
                .withName("Alfresco content sync " + jobId)
                .withAmountOfRetries(0)
                .withJobRequest(new SyncJobRunRequest(jobId))));
        job.setJobRunrId(jobRunrId);
        syncJobRepository.save(job);
        LOG.infof(
                "Queued JobRunr sync job %s jobRunrId=%s localRoot=%s remoteRoot=%s reportOutput=%s forceNewVersion=%s",
                jobId,
                jobRunrId,
                request.localRootPath(),
                request.remoteRootNodeId,
                request.reportOutputPath(),
                request.forceNewVersion
        );
        return job;
    }

    public SyncJob get(String jobId) {
        SyncJob job = syncJobRepository.findById(jobId).orElse(null);
        syncStatusFromJobRunr(job);
        return job;
    }

    public Collection<SyncJob> list() {
        return syncJobRepository.findAll().stream()
                .peek(this::syncStatusFromJobRunr)
                .sorted(Comparator.comparing(SyncJob::getCreatedAt).reversed())
                .toList();
    }

    public JobRunrSummaryResponseDTO jobRunrSummary() {
        JobStats stats = storageProvider.getJobStats();
        return new JobRunrSummaryResponseDTO(
                stats.getTotal(),
                stats.getAwaiting(),
                stats.getScheduled(),
                stats.getEnqueued(),
                stats.getProcessing(),
                stats.getFailed(),
                stats.getSucceeded(),
                stats.getDeleted(),
                stats.getAllTimeSucceeded(),
                stats.getRecurringJobs(),
                stats.getBackgroundJobServers()
        );
    }

    public void markRunning(String jobId, StartSyncRequestDTO request) {
        SyncJob job = requireJob(jobId);
        job.markRunning();
        job.setErrorMessage(null);
        job.setReport(new SyncReport(
                request.localRootPath().toString(),
                request.remoteRootNodeId,
                request.dryRun,
                request.deleteRemoteMissing
        ));
        job.setForceNewVersion(request.forceNewVersion);
        syncJobRepository.save(job);
        LOG.infof("Sync job %s is running", jobId);
    }

    public void updateProgress(String jobId, SyncReport currentReport) {
        SyncJob job = requireJob(jobId);
        job.setReport(snapshotReport(currentReport));
        syncJobRepository.save(job);
    }

    public void markCompleted(String jobId, SyncReport report) {
        SyncJob job = requireJob(jobId);
        job.markCompleted(report);
        syncJobRepository.save(job);
        archiveReport(jobId, report);
        LOG.infof("Sync job %s completed", jobId);
    }

    public void markFailed(String jobId, StartSyncRequestDTO request, Exception error, SyncReport partialReport) {
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
        syncJobRepository.save(job);
        archiveReport(jobId, failureReport);
        LOG.errorf(error, "Sync job %s failed: %s", jobId, error.getMessage());
    }

    private SyncJob requireJob(String jobId) {
        return syncJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Sync job not found: " + jobId));
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
                syncJobRepository.save(job);
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

    private SyncReport snapshotReport(SyncReport report) {
        return objectMapper.convertValue(report, SyncReport.class);
    }

    private void archiveReport(String jobId, SyncReport report) {
        if (!reportStoreEnabled || report == null) {
            return;
        }
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
            String csv = csvReportWriter.write(report);
            syncReportArchiveRepository.save(jobId, json, csv);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to archive final report for sync job %s", jobId);
        }
    }
}

