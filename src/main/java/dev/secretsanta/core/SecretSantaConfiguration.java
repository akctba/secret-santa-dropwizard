package dev.secretsanta.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public class SecretSantaConfiguration extends Configuration {
    @Valid
    @JsonProperty("auth")
    private AuthConfiguration auth = new AuthConfiguration();

    @Valid
    @JsonProperty("database")
    private DatabaseConfiguration database = new DatabaseConfiguration();

    public DatabaseConfiguration getDatabase() {
        return database;
    }

    public static class DatabaseConfiguration {
        @NotBlank
        private String path = "secret-santa.db";

        public String getPath() { return path; }

        @JsonProperty
        public void setPath(String path) { this.path = path; }
    }

    public AuthConfiguration getAuth() {
        return auth;
    }

    public static class AuthConfiguration {
        @NotBlank
        private String issuer = "secret-santa-api";
        @NotBlank
        private String secret = System.getenv("SECRET_SANTA_SECRET");
        private long accessTokenMinutes = 60;
        private long refreshTokenDays = 30;

        public String getIssuer() { return issuer; }
        public String getSecret() { return secret; }
        public long getAccessTokenMinutes() { return accessTokenMinutes; }
        public long getRefreshTokenDays() { return refreshTokenDays; }

        @JsonProperty
        public void setIssuer(String issuer) { this.issuer = issuer; }
        @JsonProperty
        public void setSecret(String secret) { this.secret = secret; }
        @JsonProperty
        public void setAccessTokenMinutes(long minutes) { this.accessTokenMinutes = minutes; }
        @JsonProperty
        public void setRefreshTokenDays(long days) { this.refreshTokenDays = days; }
    }
}
