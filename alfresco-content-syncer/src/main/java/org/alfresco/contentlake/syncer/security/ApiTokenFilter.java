package org.alfresco.contentlake.syncer.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.util.Optional;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiTokenFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "syncer.ui.auth-token")
    Optional<String> expectedToken;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String configuredToken = expectedToken.orElse("").trim();
        if (configuredToken.isBlank()) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();
        if (!path.startsWith("api/")) {
            return;
        }

        String provided = requestContext.getHeaderString("X-Syncer-Token");
        if (provided == null || provided.isBlank()) {
            String auth = requestContext.getHeaderString("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                provided = auth.substring("Bearer ".length());
            }
        }

        if (!configuredToken.equals(provided)) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Missing or invalid syncer token")
                    .build());
        }
    }
}

