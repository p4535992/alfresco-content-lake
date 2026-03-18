package org.alfresco.contentlake.syncer.model;

public record SyncFailure(String path, String operation, String message) {
}
