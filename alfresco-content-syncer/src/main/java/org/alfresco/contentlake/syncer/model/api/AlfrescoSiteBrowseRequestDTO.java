package org.alfresco.contentlake.syncer.model.api;

public class AlfrescoSiteBrowseRequestDTO extends AlfrescoConnectionPayloadDTO {

    public String siteId;
    public String folderNodeId;

    public void validate() {
        validateConnection();
        if (isBlank(siteId)) {
            throw new IllegalArgumentException("siteId is required");
        }
    }
}



