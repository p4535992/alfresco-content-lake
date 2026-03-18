package org.alfresco.contentlake.syncer.api;

import java.nio.file.Files;
import java.nio.file.Path;

public class StartSyncRequest implements AlfrescoConnectionRequest {

    public String localRoot;
    public String remoteRootNodeId;
    public String alfrescoBaseUrl;
    public String username;
    public String password;
    public String ticket;
    public boolean dryRun;
    public boolean deleteRemoteMissing;
    public String reportOutput;

    public void validate() {
        if (isBlank(localRoot)) {
            throw new IllegalArgumentException("localRoot is required");
        }
        Path localRootPath = Path.of(localRoot).toAbsolutePath().normalize();
        if (!Files.exists(localRootPath)) {
            throw new IllegalArgumentException("localRoot does not exist: " + localRootPath);
        }
        if (!Files.isDirectory(localRootPath)) {
            throw new IllegalArgumentException("localRoot is not a directory: " + localRootPath);
        }
        if (isBlank(remoteRootNodeId)) {
            throw new IllegalArgumentException("remoteRootNodeId is required");
        }
        validateConnection();
    }

    public void validateConnection() {
        if (isBlank(alfrescoBaseUrl)) {
            throw new IllegalArgumentException("alfrescoBaseUrl is required");
        }
        boolean hasBasic = !isBlank(username) && !isBlank(password);
        boolean hasTicket = !isBlank(ticket);
        if (!hasBasic && !hasTicket) {
            throw new IllegalArgumentException("Provide username/password or ticket");
        }
    }

    public Path localRootPath() {
        return Path.of(localRoot).toAbsolutePath().normalize();
    }

    public Path reportOutputPath() {
        return isBlank(reportOutput) ? null : Path.of(reportOutput).toAbsolutePath().normalize();
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
