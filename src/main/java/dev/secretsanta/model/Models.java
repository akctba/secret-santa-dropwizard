package dev.secretsanta.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public final class Models {
    private Models() {}

    public record CreateUserRequest(
            @JsonProperty("user_name") String userName,
            String email,
            String password) {}

    public record SigninRequest(String email, String password) {}
    public record RefreshTokenRequest(@JsonProperty("refresh_token") String refreshToken) {}
    public record SigninResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("refresh_token") String refreshToken) {}
    public record RefreshTokenResponse(@JsonProperty("access_token") String accessToken) {}

    public record User(@JsonProperty("user_id") long userId,
                       @JsonProperty("user_name") String userName,
                       @JsonProperty("user_email") String email,
                       String gender,
                       @JsonProperty("date_of_birth") Instant dateOfBirth) {}

    public record CreateGroupRequest(String name,
                                     @JsonProperty("date_created") Instant dateCreated,
                                     @JsonProperty("date_draw") Instant dateDraw,
                                     @JsonProperty("creator_user_id") long creatorUserId) {}

    public record Group(@JsonProperty("group_id") String groupId,
                        String name,
                        @JsonProperty("date_created") Instant dateCreated,
                        @JsonProperty("date_draw") Instant dateDraw,
                        @JsonProperty("creator_user_id") long creatorUserId) {}

    public record ParticipantRequest(@JsonProperty("group_id") String groupId,
                                     @JsonProperty("user_id") long userId) {}
}
