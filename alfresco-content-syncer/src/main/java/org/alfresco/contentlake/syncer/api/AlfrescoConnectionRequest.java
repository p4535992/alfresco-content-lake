package org.alfresco.contentlake.syncer.api;

public interface AlfrescoConnectionRequest {

    String sanitizedBaseUrl();

    String username();

    String password();

    String ticket();
}
