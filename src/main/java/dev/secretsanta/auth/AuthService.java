package dev.secretsanta.auth;

import dev.secretsanta.core.SecretSantaConfiguration.AuthConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class AuthService {
    private final AuthConfiguration configuration;
    private final byte[] secret;

    public AuthService(AuthConfiguration configuration) {
        this.configuration = configuration;
        this.secret = sha256(configuration.getSecret());
    }

    public String issueAccessToken(long userId) {
        Instant now = Instant.now();
        return Jwts.builder().issuer(configuration.getIssuer()).subject(Long.toString(userId))
                .id(UUID.randomUUID().toString()).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(configuration.getAccessTokenMinutes() * 60)))
                .signWith(Keys.hmacShaKeyFor(secret)).compact();
    }

    public String issueRefreshToken(long userId) {
        Instant now = Instant.now();
        return Jwts.builder().issuer(configuration.getIssuer()).subject(Long.toString(userId))
                .claim("type", "refresh").issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(configuration.getRefreshTokenDays() * 86400)))
                .signWith(Keys.hmacShaKeyFor(secret)).compact();
    }

    public long verifyAccessToken(String token) {
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret))
                .requireIssuer(configuration.getIssuer()).build().parseSignedClaims(token).getPayload();
        if ("refresh".equals(claims.get("type", String.class))) throw new IllegalArgumentException("Refresh token is not an access token");
        return Long.parseLong(claims.getSubject());
    }

    public long verifyRefreshToken(String token) {
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret))
                .requireIssuer(configuration.getIssuer()).build().parseSignedClaims(token).getPayload();
        if (!"refresh".equals(claims.get("type", String.class))) throw new IllegalArgumentException("Access token is not a refresh token");
        return Long.parseLong(claims.getSubject());
    }

    public boolean passwordMatches(String password, String storedHash) {
        return MessageDigest.isEqual(hash(password).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    public String hashPassword(String password) {
        return hash(password);
    }

    private String hash(String value) {
        return java.util.HexFormat.of().formatHex(sha256(value));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
