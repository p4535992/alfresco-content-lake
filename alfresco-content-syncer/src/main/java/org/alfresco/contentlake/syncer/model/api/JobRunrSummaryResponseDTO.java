package org.alfresco.contentlake.syncer.model.api;

public record JobRunrSummaryResponseDTO(
        long total,
        long awaiting,
        long scheduled,
        long enqueued,
        long processing,
        long failed,
        long succeeded,
        long deleted,
        long allTimeSucceeded,
        int recurringJobs,
        int backgroundJobServers
) {
}


