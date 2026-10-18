package dev.secretsanta.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.secretsanta.auth.AuthService;
import dev.secretsanta.model.Models;
import dev.secretsanta.repository.InMemorySecretSantaRepository;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserResourceTest {
    private InMemorySecretSantaRepository repository;
    private AuthService authService;
    private UserResource resource;

    @BeforeEach
    void setUp() {
        repository = new InMemorySecretSantaRepository();
        authService = new AuthService(testConfiguration());
        resource = new UserResource(repository, authService);
    }

    @Test
    void createsUserAndReturnsCreatedResponse() {
        Response response = resource.createUser(
                new Models.CreateUserRequest("Alice", "alice@example.com", "password"));

        assertEquals(201, response.getStatus());
        Models.User user = (Models.User) response.getEntity();
        assertEquals(1L, user.userId());
        assertEquals("Alice", user.userName());
        assertEquals("alice@example.com", user.email());
        assertNotEquals("password", repository.passwordHash(user.userId()));
        assertTrue(authService.passwordMatches("password", repository.passwordHash(user.userId())));
    }

    @Test
    void rejectsMissingOrBlankRegistrationFields() {
        for (Models.CreateUserRequest request : new Models.CreateUserRequest[]{
                new Models.CreateUserRequest(null, "alice@example.com", "password"),
                new Models.CreateUserRequest(" ", "alice@example.com", "password"),
                new Models.CreateUserRequest("Alice", null, "password"),
                new Models.CreateUserRequest("Alice", " ", "password"),
                new Models.CreateUserRequest("Alice", "alice@example.com", null),
                new Models.CreateUserRequest("Alice", "alice@example.com", " ")}) {
            assertThrows(BadRequestException.class, () -> resource.createUser(request));
        }
    }

    @Test
    void signsInAndIssuesMatchingAccessAndRefreshTokens() {
        Models.User user = createUser();

        Models.SigninResponse response = resource.signin(
                new Models.SigninRequest("ALICE@example.com", "password"));

        assertEquals(user.userId(), authService.verifyAccessToken(response.accessToken()));
        assertEquals(user.userId(), authService.verifyRefreshToken(response.refreshToken()));
    }

    @Test
    void rejectsIncorrectSigninPassword() {
        createUser();

        assertThrows(NotAuthorizedException.class,
                () -> resource.signin(new Models.SigninRequest("alice@example.com", "wrong")));
    }

    @Test
    void refreshesAccessTokenAndRejectsAnAccessTokenAsRefreshToken() {
        Models.User user = createUser();
        String refreshToken = authService.issueRefreshToken(user.userId());

        Models.RefreshTokenResponse response = resource.refresh(new Models.RefreshTokenRequest(refreshToken));

        assertEquals(user.userId(), authService.verifyAccessToken(response.accessToken()));
        assertThrows(IllegalArgumentException.class,
                () -> resource.refresh(new Models.RefreshTokenRequest(authService.issueAccessToken(user.userId()))));
    }

    @Test
    void retrievesUsersById() {
        Models.User user = createUser();

        assertEquals(user, resource.getUser(user.userId()));
        assertThrows(IllegalArgumentException.class, () -> resource.getUser(999L));
    }

    private Models.User createUser() {
        return (Models.User) resource.createUser(
                new Models.CreateUserRequest("Alice", "alice@example.com", "password")).getEntity();
    }

    private static dev.secretsanta.core.SecretSantaConfiguration.AuthConfiguration testConfiguration() {
        var configuration = new dev.secretsanta.core.SecretSantaConfiguration.AuthConfiguration();
        configuration.setIssuer("test-issuer");
        configuration.setSecret("test-secret");
        return configuration;
    }
}
