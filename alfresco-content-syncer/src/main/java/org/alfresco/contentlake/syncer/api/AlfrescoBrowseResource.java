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

@Path("/api/alfresco")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AlfrescoBrowseResource {

    @Inject
    AlfrescoHttpClient alfrescoHttpClient;

    @POST
    @Path("/browse")
    public AlfrescoBrowseResponse browse(AlfrescoBrowseRequest request) {
        try {
            request.validate();
            return new AlfrescoBrowseResponse(
                    alfrescoHttpClient.getNode(request, request.nodeId),
                    alfrescoHttpClient.listChildren(request, request.nodeId)
            );
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        }
    }
}
