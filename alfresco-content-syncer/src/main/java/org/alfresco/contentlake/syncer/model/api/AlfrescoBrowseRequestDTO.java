package org.alfresco.contentlake.syncer.model.api;

public class AlfrescoBrowseRequestDTO extends AlfrescoConnectionPayloadDTO {

    public String nodeId;

    public void validate() {
        validateConnection();
        if (isBlank(nodeId)) {
            throw new IllegalArgumentException("nodeId is required");
        }
    }
}


