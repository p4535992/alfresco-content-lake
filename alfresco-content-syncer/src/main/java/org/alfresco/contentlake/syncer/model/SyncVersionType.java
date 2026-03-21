package org.alfresco.contentlake.syncer.model;

public enum SyncVersionType {
    MINOR,
    MAJOR;

    public boolean isMajor() {
        return this == MAJOR;
    }

    public static SyncVersionType defaultValue() {
        return MINOR;
    }
}
