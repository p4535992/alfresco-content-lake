package org.alfresco.contentlake.syncer.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.api.StartSyncRequest;
import org.alfresco.contentlake.syncer.model.SyncReport;
import org.alfresco.contentlake.syncer.service.LocalFolderSyncService;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SyncJobRunRequestHandler implements JobRequestHandler<SyncJobRunRequest> {

    private static final Logger LOG = Logger.getLogger(SyncJobRunRequestHandler.class);

    @Inject
    SyncJobService syncJobService;

    @Inject
    SyncJobRequestStore syncJobRequestStore;

    @Inject
    LocalFolderSyncService localFolderSyncService;

    @Override
    public void run(SyncJobRunRequest jobRequest) throws Exception {
        String syncJobId = jobRequest.getSyncJobId();
        StartSyncRequest request = syncJobRequestStore.load(syncJobId);
        syncJobService.markRunning(syncJobId, request);
        LOG.infof("JobRunr started sync job %s", syncJobId);

        SyncReport report = null;
        try {
            report = localFolderSyncService.sync(request, currentReport ->
                    syncJobService.updateProgress(syncJobId, currentReport));
            syncJobService.markCompleted(syncJobId, report);
            LOG.infof("JobRunr completed sync job %s", syncJobId);
        } catch (Exception e) {
            syncJobService.markFailed(syncJobId, request, e, report);
            LOG.errorf(e, "JobRunr failed sync job %s", syncJobId);
            throw e;
        }
    }
}
