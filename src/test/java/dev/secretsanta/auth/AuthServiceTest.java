package dev.secretsanta.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.secretsanta.core.SecretSantaConfiguration.AuthConfiguration;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    private AuthConfiguration configuration;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        configuration = new AuthConfiguration();
        configuration.setIssuer("test-issuer");
        configuration.setSecret("test-secret");
        authService = new AuthService(configuration);
    }

    @Test
    void hashesPasswordsAndComparesThem() {
        String hash = authService.hashPassword("correct horse battery staple");

        assertNotEquals("correct horse battery staple", hash);
        assertEquals(hash, authService.hashPassword("correct horse battery staple"));
        assertTrue(authService.passwordMatches("correct horse battery staple", hash));
        assertFalse(authService.passwordMatches("wrong password", hash));
    }

    @Test
    void accessTokenVerifiesForItsUser() {
        assertEquals(42L, authService.verifyAccessToken(authService.issueAccessToken(42L)));
    }

    @Test
    void refreshTokenVerifiesForItsUser() {
        assertEquals(27L, authService.verifyRefreshToken(authService.issueRefreshToken(27L)));
    }

    @Test
    void accessAndRefreshTokensCannotBeUsedInterchangeably() {
        String accessToken = authService.issueAccessToken(1L);
        String refreshToken = authService.issueRefreshToken(1L);

        assertThrows(IllegalArgumentException.class, () -> authService.verifyAccessToken(refreshToken));
        assertThrows(IllegalArgumentException.class, () -> authService.verifyRefreshToken(accessToken));
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        var otherConfiguration = new AuthConfiguration();
        otherConfiguration.setIssuer("test-issuer");
        otherConfiguration.setSecret("another-secret");

        assertThrows(JwtException.class,
                () -> new AuthService(otherConfiguration).verifyAccessToken(authService.issueAccessToken(1L)));
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        var otherConfiguration = new AuthConfiguration();
        otherConfiguration.setIssuer("another-issuer");
        otherConfiguration.setSecret("test-secret");

        assertThrows(JwtException.class,
                () -> new AuthService(otherConfiguration).verifyAccessToken(authService.issueAccessToken(1L)));
    }

    @Test
    void rejectsExpiredToken() {
        configuration.setAccessTokenMinutes(0);
        var immediatelyExpiringAuth = new AuthService(configuration);

        assertThrows(JwtException.class,
                () -> immediatelyExpiringAuth.verifyAccessToken(immediatelyExpiringAuth.issueAccessToken(1L)));
    }

    @Test
    void rejectsMalformedToken() {
        assertThrows(JwtException.class, () -> authService.verifyAccessToken("not-a-jwt"));
    }
}
