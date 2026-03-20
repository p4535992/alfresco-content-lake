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
import org.alfresco.contentlake.syncer.job.SyncReportArchiveRepository;
import org.alfresco.contentlake.syncer.api.JobRunrSummaryResponse;
import org.alfresco.contentlake.syncer.model.SyncJob;
import org.alfresco.contentlake.syncer.report.CsvReportWriter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;

@Path("/api/sync")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SyncResource {

    @Inject
    SyncJobService syncJobService;

    @Inject
    CsvReportWriter csvReportWriter;

    @Inject
    SyncReportArchiveRepository syncReportArchiveRepository;

    @Inject
    ObjectMapper objectMapper;

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

    @GET
    @Path("/jobrunr/summary")
    public JobRunrSummaryResponse jobRunrSummary() {
        return syncJobService.jobRunrSummary();
    }

    @GET
    @Path("/jobs/{jobId}/report.csv")
    @Produces("text/csv")
    public Response downloadCsvReport(@PathParam("jobId") String jobId) {
        SyncJob job = syncJobService.get(jobId);
        if (job == null) {
            throw new WebApplicationException("Job not found", Response.Status.NOT_FOUND);
        }
        if (job.getReport() == null) {
            throw new WebApplicationException("Report not available yet", Response.Status.CONFLICT);
        }

        String csv = syncReportArchiveRepository.findCsv(jobId)
                .orElseGet(() -> csvReportWriter.write(job.getReport()));
        return Response.ok(csv)
                .type("text/csv")
                .header("Content-Disposition", "attachment; filename=\"" + jobId + "-report.csv\"")
                .build();
    }

    @GET
    @Path("/jobs/{jobId}/report.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response downloadJsonReport(@PathParam("jobId") String jobId) {
        SyncJob job = syncJobService.get(jobId);
        if (job == null) {
            throw new WebApplicationException("Job not found", Response.Status.NOT_FOUND);
        }
        if (job.getReport() == null) {
            throw new WebApplicationException("Report not available yet", Response.Status.CONFLICT);
        }

        String json = syncReportArchiveRepository.findJson(jobId)
                .orElseGet(() -> {
                    try {
                        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(job.getReport());
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to render JSON report for " + jobId, e);
                    }
                });
        return Response.ok(json)
                .type(MediaType.APPLICATION_JSON)
                .header("Content-Disposition", "attachment; filename=\"" + jobId + "-report.json\"")
                .build();
    }

    @POST
    @Path("/jobs")
    public Response startJob(StartSyncRequest request) {
        try {
            SyncJob job = syncJobService.start(request);
            return Response.status(Response.Status.ACCEPTED).entity(job).build();
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.SERVICE_UNAVAILABLE);
        }
    }
}
