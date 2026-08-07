package dev.secretsanta.core;

import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public final class ApiExceptionMapper implements ExceptionMapper<RuntimeException> {
    @Override
    public Response toResponse(RuntimeException exception) {
        int status = exception instanceof jakarta.ws.rs.NotAuthorizedException ? 401
                : exception instanceof jakarta.ws.rs.BadRequestException
                || exception instanceof IllegalArgumentException ? 400
                : exception instanceof IllegalStateException ? 409 : 500;
        return Response.status(status).entity(exception.getMessage() == null ? "Unexpected server-side failure" : exception.getMessage())
                .type(MediaType.TEXT_PLAIN).build();
    }
}
