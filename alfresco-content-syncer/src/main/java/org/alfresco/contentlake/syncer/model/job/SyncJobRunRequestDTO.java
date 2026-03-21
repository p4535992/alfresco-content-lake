package org.alfresco.contentlake.syncer.model.job;

import org.alfresco.contentlake.syncer.job.SyncJobRunRequestHandler;
import org.jobrunr.jobs.lambdas.JobRequest;

public class SyncJobRunRequestDTO implements JobRequest {

    private String syncJobId;

    public SyncJobRunRequestDTO() {
    }

    public SyncJobRunRequestDTO(String syncJobId) {
        this.syncJobId = syncJobId;
    }

    public String getSyncJobId() {
        return syncJobId;
    }

    public void setSyncJobId(String syncJobId) {
        this.syncJobId = syncJobId;
    }

    @Override
    public Class<SyncJobRunRequestHandler> getJobRequestHandler() {
        return SyncJobRunRequestHandler.class;
    }
}
