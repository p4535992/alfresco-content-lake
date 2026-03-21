package org.alfresco.contentlake.syncer.api;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.model.api.LocalFolderSelectionResponseDTO;
import org.alfresco.contentlake.syncer.model.api.RuntimeInfoResponseDTO;
import org.alfresco.contentlake.syncer.model.api.RuntimeSettingsResponseDTO;
import org.alfresco.contentlake.syncer.model.api.UpdateRuntimeSettingsRequestDTO;
import org.alfresco.contentlake.syncer.service.RuntimeSettingsService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

@Path("/api/system")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DesktopResource {

    @Inject
    RuntimeSettingsService runtimeSettingsService;

    @ConfigProperty(name = "syncer.ui.startup-host", defaultValue = "127.0.0.1")
    String startupHost;

    @ConfigProperty(name = "quarkus.http.port")
    int httpPort;

    @ConfigProperty(name = "syncer.jobrunr.dashboard-host", defaultValue = "127.0.0.1")
    String dashboardHost;

    @ConfigProperty(name = "syncer.jobrunr.dashboard-port", defaultValue = "8000")
    int dashboardPort;

    @GET
    @Path("/runtime")
    public RuntimeInfoResponseDTO runtimeInfo() {
        return new RuntimeInfoResponseDTO(
                "http://" + startupHost + ":" + httpPort + "/",
                "http://" + dashboardHost + ":" + dashboardPort + "/",
                "http://" + startupHost + ":" + httpPort + "/settings.html"
        );
    }

    @GET
    @Path("/settings")
    public RuntimeSettingsResponseDTO settings() {
        return runtimeSettingsService.load();
    }

    @POST
    @Path("/settings")
    public RuntimeSettingsResponseDTO saveSettings(UpdateRuntimeSettingsRequestDTO request) {
        try {
            return runtimeSettingsService.save(request);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/local-folder/select")
    public LocalFolderSelectionResponseDTO selectLocalFolder() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new WebApplicationException("Local folder picker is not available in headless mode",
                    Response.Status.SERVICE_UNAVAILABLE);
        }

        AtomicReference<String> selectedPath = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select local folder");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selected = chooser.getSelectedFile();
                    if (selected != null) {
                        selectedPath.set(selected.getAbsolutePath());
                    }
                }
            });
        } catch (Exception e) {
            throw new WebApplicationException("Failed to open local folder picker: " + e.getMessage(),
                    Response.Status.INTERNAL_SERVER_ERROR);
        }

        return new LocalFolderSelectionResponseDTO(selectedPath.get());
    }
}

