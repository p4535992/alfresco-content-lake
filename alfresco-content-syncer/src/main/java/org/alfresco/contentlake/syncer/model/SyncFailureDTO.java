package org.alfresco.contentlake.syncer.model;

public record SyncFailureDTO(String path, String operation, String message) {
}


