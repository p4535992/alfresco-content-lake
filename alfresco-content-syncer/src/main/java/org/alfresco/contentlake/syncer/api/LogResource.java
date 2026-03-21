package org.alfresco.contentlake.syncer.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.alfresco.contentlake.syncer.service.LogFileService;

@Path("/api/logs")
@Produces(MediaType.APPLICATION_JSON)
public class LogResource {

    @Inject
    LogFileService logFileService;

    @GET
    public LogViewResponseDTO readLogs(
            @QueryParam("file") String fileName,
            @QueryParam("limit") @DefaultValue("200") int limit
    ) {
        try {
            return logFileService.readLogs(fileName, limit);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}

