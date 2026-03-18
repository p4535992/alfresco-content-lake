package org.alfresco.contentlake.syncer.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.alfresco.contentlake.syncer.job.SyncJobService;
import org.alfresco.contentlake.syncer.model.SyncJob;

import java.util.Collection;

@Path("/api/sync")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SyncResource {

    @Inject
    SyncJobService syncJobService;

    @GET
    @Path("/jobs")
    public Collection<SyncJob> listJobs() {
        return syncJobService.list();
    }

    @GET
    @Path("/jobs/{jobId}")
    public SyncJob getJob(@PathParam("jobId") String jobId) {
        SyncJob job = syncJobService.get(jobId);
        if (job == null) {
            throw new WebApplicationException("Job not found", Response.Status.NOT_FOUND);
        }
        return job;
    }

    @POST
    @Path("/jobs")
    public Response startJob(StartSyncRequest request) {
        try {
            SyncJob job = syncJobService.start(request);
            return Response.status(Response.Status.ACCEPTED).entity(job).build();
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        }
    }
}
