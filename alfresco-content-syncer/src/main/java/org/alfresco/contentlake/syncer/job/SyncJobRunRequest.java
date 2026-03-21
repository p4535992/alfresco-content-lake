package org.alfresco.contentlake.syncer.job;

import org.jobrunr.jobs.lambdas.JobRequest;

public class SyncJobRunRequest implements JobRequest {

    private String syncJobId;

    public SyncJobRunRequest() {
    }

    public SyncJobRunRequest(String syncJobId) {
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

