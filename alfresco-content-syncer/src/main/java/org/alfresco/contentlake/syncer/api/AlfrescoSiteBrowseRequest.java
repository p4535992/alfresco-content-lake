package org.alfresco.contentlake.syncer.api;

public class AlfrescoSiteBrowseRequest extends AlfrescoConnectionPayload {

    public String siteId;
    public String folderNodeId;

    public void validate() {
        validateConnection();
        if (isBlank(siteId)) {
            throw new IllegalArgumentException("siteId is required");
        }
    }
}
