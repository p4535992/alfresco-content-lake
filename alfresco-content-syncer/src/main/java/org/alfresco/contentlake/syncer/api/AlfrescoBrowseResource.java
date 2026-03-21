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
import org.alfresco.contentlake.syncer.model.RemoteNodeDTO;
import org.alfresco.contentlake.syncer.model.api.AlfrescoConnectionPayloadDTO;
import org.alfresco.contentlake.syncer.model.api.AlfrescoConnectionStatusResponseDTO;
import org.alfresco.contentlake.syncer.model.api.AlfrescoSiteBrowseRequestDTO;
import org.alfresco.contentlake.syncer.model.api.AlfrescoSiteFolderBrowseResponseDTO;
import org.alfresco.contentlake.syncer.model.api.AlfrescoSiteInfoDTO;

import java.util.List;

@Path("/api/alfresco")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AlfrescoBrowseResource {

    @Inject
    AlfrescoHttpClient alfrescoHttpClient;

    @POST
    @Path("/connection/verify")
    public AlfrescoConnectionStatusResponseDTO verifyConnection(AlfrescoConnectionPayloadDTO request) {
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
    public List<AlfrescoSiteInfoDTO> listSites(AlfrescoConnectionPayloadDTO request) {
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
    public AlfrescoSiteFolderBrowseResponseDTO browseSiteFolders(AlfrescoSiteBrowseRequestDTO request) {
        try {
            request.validate();
            AlfrescoSiteInfoDTO site = alfrescoHttpClient.getSite(request, request.siteId);
            String documentLibraryNodeId = alfrescoHttpClient.getDocumentLibraryNodeId(request, request.siteId);
            String currentNodeId = request.folderNodeId == null || request.folderNodeId.isBlank()
                    ? documentLibraryNodeId
                    : request.folderNodeId;
            RemoteNodeDTO documentLibrary = alfrescoHttpClient.getNode(request, documentLibraryNodeId);
            RemoteNodeDTO currentNode = alfrescoHttpClient.getNode(request, currentNodeId);
            List<RemoteNodeDTO> children = alfrescoHttpClient.listChildren(request, currentNodeId).stream()
                    .filter(RemoteNodeDTO::folder)
                    .toList();
            return new AlfrescoSiteFolderBrowseResponseDTO(site, documentLibrary, currentNode, children);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_GATEWAY);
        }
    }
}


