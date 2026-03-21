package org.alfresco.contentlake.syncer.model.api;

public record RuntimeInfoResponseDTO(
        String applicationUrl,
        String jobRunrDashboardUrl,
        String settingsUrl
) {
}


