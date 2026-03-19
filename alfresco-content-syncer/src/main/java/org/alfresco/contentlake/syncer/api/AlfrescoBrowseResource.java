package org.alfresco.contentlake.syncer.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.alfresco.contentlake.syncer.client.AlfrescoHttpClient;
import org.alfresco.contentlake.syncer.model.RemoteNode;

import java.util.List;

@Path("/api/alfresco")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AlfrescoBrowseResource {

    @Inject
    AlfrescoHttpClient alfrescoHttpClient;

    @POST
    @Path("/connection/verify")
    public AlfrescoConnectionStatusResponse verifyConnection(AlfrescoConnectionPayload request) {
        try {
            request.validateConnection();
            return alfrescoHttpClient.verifyConnection(request);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_GATEWAY);
        }
    }

    @POST
    @Path("/sites")
    public List<AlfrescoSiteInfo> listSites(AlfrescoConnectionPayload request) {
        try {
            request.validateConnection();
            return alfrescoHttpClient.listSites(request);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_GATEWAY);
        }
    }

    @POST
    @Path("/sites/browse")
    public AlfrescoSiteFolderBrowseResponse browseSiteFolders(AlfrescoSiteBrowseRequest request) {
        try {
            request.validate();
            AlfrescoSiteInfo site = alfrescoHttpClient.getSite(request, request.siteId);
            String documentLibraryNodeId = alfrescoHttpClient.getDocumentLibraryNodeId(request, request.siteId);
            String currentNodeId = request.folderNodeId == null || request.folderNodeId.isBlank()
                    ? documentLibraryNodeId
                    : request.folderNodeId;
            RemoteNode documentLibrary = alfrescoHttpClient.getNode(request, documentLibraryNodeId);
            RemoteNode currentNode = alfrescoHttpClient.getNode(request, currentNodeId);
            List<RemoteNode> children = alfrescoHttpClient.listChildren(request, currentNodeId).stream()
                    .filter(RemoteNode::folder)
                    .toList();
            return new AlfrescoSiteFolderBrowseResponse(site, documentLibrary, currentNode, children);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_GATEWAY);
        }
    }
}
