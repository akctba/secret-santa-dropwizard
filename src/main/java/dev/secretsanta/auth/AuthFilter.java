package dev.secretsanta.auth;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHENTICATION)
public final class AuthFilter implements ContainerRequestFilter {
    private final AuthService authService;

    public AuthFilter(AuthService authService) { this.authService = authService; }

    @Override
    public void filter(ContainerRequestContext context) {
        if (!context.getUriInfo().getPath().startsWith("v1/")
                || context.getUriInfo().getPath().equals("v1/user")
                || context.getUriInfo().getPath().equals("v1/user/signin")
                || context.getUriInfo().getPath().equals("v1/user/refresh")) return;
        String header = context.getHeaderString("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            abort(context, "Missing or invalid token");
            return;
        }
        try {
            context.setProperty("authenticatedUserId", authService.verifyAccessToken(header.substring(7)));
        } catch (RuntimeException e) {
            abort(context, "Missing or invalid token");
        }
    }

    private static void abort(ContainerRequestContext context, String message) {
        context.abortWith(Response.status(Response.Status.UNAUTHORIZED).entity(message).type("text/plain").build());
    }
}
