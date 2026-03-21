package org.alfresco.contentlake.syncer.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class SyncState {

    private Map<String, SyncStateEntry> entries = new LinkedHashMap<>();

    public Map<String, SyncStateEntry> getEntries() {
        return entries;
    }

    public void setEntries(Map<String, SyncStateEntry> entries) {
        this.entries = entries;
    }
}

