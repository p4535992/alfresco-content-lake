package org.alfresco.contentlake.syncer.api;

public class AlfrescoBrowseRequest extends AlfrescoConnectionPayload {

    public String nodeId;

    public void validate() {
        validateConnection();
        if (isBlank(nodeId)) {
            throw new IllegalArgumentException("nodeId is required");
        }
    }
}
