package org.alfresco.contentlake.syncer.model.api;

public interface AlfrescoConnectionRequest {

    String sanitizedBaseUrl();

    String username();

    String password();

    String ticket();

    default String publicApiBaseUrl() {
        return sanitizedBaseUrl() + "/api/-default-/public/alfresco/versions/1";
    }

    default String authenticationApiBaseUrl() {
        return sanitizedBaseUrl() + "/api/-default-/public/authentication/versions/1";
    }
}


