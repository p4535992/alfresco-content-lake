package org.alfresco.contentlake.syncer.api;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

@Path("/api/system")
@Produces(MediaType.APPLICATION_JSON)
public class DesktopResource {

    @POST
    @Path("/local-folder/select")
    public LocalFolderSelectionResponse selectLocalFolder() {
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

        return new LocalFolderSelectionResponse(selectedPath.get());
    }
}
