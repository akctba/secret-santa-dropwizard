package dev.secretsanta.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class ApiExceptionMapperTest {
    private final ApiExceptionMapper mapper = new ApiExceptionMapper();

    @Test
    void mapsAuthenticationAndInputErrors() {
        var unauthorized = new NotAuthorizedException("unauthorized");
        var badRequest = new BadRequestException("bad request");
        var invalidArgument = new IllegalArgumentException("invalid argument");

        assertResponse(mapper.toResponse(unauthorized), 401, unauthorized.getMessage());
        assertResponse(mapper.toResponse(badRequest), 400, badRequest.getMessage());
        assertResponse(mapper.toResponse(invalidArgument), 400, invalidArgument.getMessage());
    }

    @Test
    void mapsStateConflictsAndUnexpectedFailures() {
        assertResponse(mapper.toResponse(new IllegalStateException("not ready")), 409, "not ready");
        assertResponse(mapper.toResponse(new IllegalStateException()), 409, "Unexpected server-side failure");
        assertResponse(mapper.toResponse(new RuntimeException("unexpected")), 500, "unexpected");
    }

    private static void assertResponse(Response response, int status, String entity) {
        assertEquals(status, response.getStatus());
        assertEquals(entity, response.getEntity());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getMediaType());
    }
}
