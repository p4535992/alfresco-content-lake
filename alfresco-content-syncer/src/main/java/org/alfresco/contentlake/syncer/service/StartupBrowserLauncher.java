package org.alfresco.contentlake.syncer.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

@ApplicationScoped
public class StartupBrowserLauncher {

    private static final Logger LOG = Logger.getLogger(StartupBrowserLauncher.class);

    @ConfigProperty(name = "syncer.ui.open-browser-on-startup", defaultValue = "true")
    boolean openBrowserOnStartup;

    @ConfigProperty(name = "syncer.ui.startup-host", defaultValue = "127.0.0.1")
    String startupHost;

    @ConfigProperty(name = "syncer.ui.startup-delay-ms", defaultValue = "1200")
    long startupDelayMs;

    @ConfigProperty(name = "quarkus.http.port")
    int httpPort;

    void onStart(@Observes StartupEvent ignored) {
        if (!openBrowserOnStartup) {
            LOG.info("Browser auto-open on startup is disabled");
            return;
        }
        if (httpPort <= 0) {
            LOG.warnf("Skipping browser auto-open because quarkus.http.port=%d", httpPort);
            return;
        }

        URI target = URI.create("http://" + startupHost + ":" + httpPort + "/");
        Thread launcher = new Thread(() -> openBrowser(target), "startup-browser-launcher");
        launcher.setDaemon(true);
        launcher.start();
    }

    private void openBrowser(URI target) {
        sleepBeforeLaunch();
        if (tryDesktopBrowse(target)) {
            return;
        }
        if (isWindows() && tryWindowsShellBrowse(target)) {
            return;
        }
        LOG.warnf("Unable to open browser automatically for %s", target);
    }

    private void sleepBeforeLaunch() {
        try {
            Thread.sleep(Math.max(0L, startupDelayMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Browser auto-open interrupted");
        }
    }

    private boolean tryDesktopBrowse(URI target) {
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
            return false;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return false;
            }
            desktop.browse(target);
            LOG.infof("Opened browser automatically at %s", target);
            return true;
        } catch (Exception e) {
            LOG.debugf(e, "Desktop browse failed for %s", target);
            return false;
        }
    }

    private boolean tryWindowsShellBrowse(URI target) {
        try {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", target.toString()).start();
            LOG.infof("Opened browser through Windows shell at %s", target);
            return true;
        } catch (IOException e) {
            LOG.debugf(e, "Windows shell browse failed for %s", target);
            return false;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}


