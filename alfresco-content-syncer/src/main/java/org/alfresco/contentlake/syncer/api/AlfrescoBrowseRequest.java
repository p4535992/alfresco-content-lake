package org.alfresco.contentlake.syncer.api;

public class AlfrescoBrowseRequest implements AlfrescoConnectionRequest {

    public String nodeId;
    public String alfrescoBaseUrl;
    public String username;
    public String password;
    public String ticket;

    public void validate() {
        if (isBlank(nodeId)) {
            throw new IllegalArgumentException("nodeId is required");
        }
        if (isBlank(alfrescoBaseUrl)) {
            throw new IllegalArgumentException("alfrescoBaseUrl is required");
        }
        boolean hasBasic = !isBlank(username) && !isBlank(password);
        boolean hasTicket = !isBlank(ticket);
        if (!hasBasic && !hasTicket) {
            throw new IllegalArgumentException("Provide username/password or ticket");
        }
    }

    @Override
    public String sanitizedBaseUrl() {
        return alfrescoBaseUrl.endsWith("/")
                ? alfrescoBaseUrl.substring(0, alfrescoBaseUrl.length() - 1)
                : alfrescoBaseUrl;
    }

    @Override
    public String username() {
        return username;
    }

    @Override
    public String password() {
        return password;
    }

    @Override
    public String ticket() {
        return ticket;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
