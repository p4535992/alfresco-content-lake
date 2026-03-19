package org.alfresco.contentlake.syncer.api;

public record RuntimeInfoResponse(
        String applicationUrl,
        String jobRunrDashboardUrl
) {
}
