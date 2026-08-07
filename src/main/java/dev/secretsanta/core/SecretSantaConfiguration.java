package dev.secretsanta.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public class SecretSantaConfiguration extends Configuration {
    @Valid
    @JsonProperty("auth")
    private AuthConfiguration auth = new AuthConfiguration();

    public AuthConfiguration getAuth() {
        return auth;
    }

    public static class AuthConfiguration {
        @NotBlank
        private String issuer = "secret-santa-api";
        @NotBlank
        private String secret = "change-me";
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
