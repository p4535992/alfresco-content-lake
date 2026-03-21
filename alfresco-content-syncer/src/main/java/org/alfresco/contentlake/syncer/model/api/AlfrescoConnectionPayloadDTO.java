package org.alfresco.contentlake.syncer.model.api;

public class AlfrescoConnectionPayloadDTO implements AlfrescoConnectionRequest {

    public String alfrescoBaseUrl;
    public String username;
    public String password;
    public String ticket;

    public void validateConnection() {
        if (isBlank(alfrescoBaseUrl)) {
            throw new IllegalArgumentException("alfrescoBaseUrl is required");
        }

        String sanitized = sanitizedBaseUrl();
        if (!sanitized.toLowerCase().endsWith("/alfresco")) {
            throw new IllegalArgumentException("alfrescoBaseUrl must end with /alfresco");
        }

        boolean hasBasic = !isBlank(username) && !isBlank(password);
        boolean hasTicket = !isBlank(ticket);
        if (!hasBasic && !hasTicket) {
            throw new IllegalArgumentException("Provide username/password or ticket");
        }
    }

    @Override
    public String sanitizedBaseUrl() {
        if (alfrescoBaseUrl == null) {
            return "";
        }
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

    protected boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}


