package dev.secretsanta.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.secretsanta.TestFixtures;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthFilterTest {
    private AuthService authService;
    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        authService = TestFixtures.authService();
        filter = new AuthFilter(authService);
    }

    @Test
    void authenticatesProtectedPathsAndStoresTheUserId() {
        var properties = new HashMap<String, Object>();
        var aborted = new AtomicReference<Response>();
        ContainerRequestContext context = TestFixtures.requestContext(
                "v1/group/grp_123", "Bearer " + authService.issueAccessToken(12L), properties, aborted);

        filter.filter(context);

        assertEquals(12L, properties.get("authenticatedUserId"));
        assertNull(aborted.get());
    }

    @Test
    void rejectsMissingMalformedAndRefreshTokensOnProtectedPaths() {
        for (String header : new String[]{null, "Basic token", "Bearer ", "Bearer malformed",
                "Bearer " + authService.issueRefreshToken(12L)}) {
            var aborted = new AtomicReference<Response>();
            filter.filter(TestFixtures.requestContext("v1/group/grp_123", header, new HashMap<>(), aborted));

            assertUnauthorized(aborted.get());
        }
    }

    @Test
    void doesNotRequireAuthenticationForPublicUserEndpointsOrOutsideApi() {
        for (String path : new String[]{"v1/user", "v1/user/signin", "v1/user/refresh", "healthcheck"}) {
            var aborted = new AtomicReference<Response>();

            filter.filter(TestFixtures.requestContext(path, null, new HashMap<>(), aborted));

            assertNull(aborted.get(), path);
        }
    }

    private static void assertUnauthorized(Response response) {
        assertTrue(response != null);
        assertEquals(401, response.getStatus());
        assertEquals("Missing or invalid token", response.getEntity());
    }
}
