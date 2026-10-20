package dev.secretsanta.resource;

import dev.secretsanta.auth.AuthService;
import dev.secretsanta.model.Models;
import dev.secretsanta.repository.SqliteSecretSantaRepository;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/v1/user")
@Produces(MediaType.APPLICATION_JSON)
public final class UserResource {
    private final SqliteSecretSantaRepository repository;
    private final AuthService auth;

    public UserResource(SqliteSecretSantaRepository repository, AuthService auth) {
        this.repository = repository; this.auth = auth;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createUser(Models.CreateUserRequest request) {
        validate(request.userName(), request.email(), request.password());
        return Response.status(Response.Status.CREATED)
                .entity(repository.createUser(request, auth.hashPassword(request.password()))).build();
    }

    @POST
    @Path("/signin")
    @Consumes(MediaType.APPLICATION_JSON)
    public Models.SigninResponse signin(Models.SigninRequest request) {
        var user = repository.findUserByEmail(request.email());
        if (!auth.passwordMatches(request.password(), repository.passwordHash(user.userId())))
            throw new NotAuthorizedException("Invalid credentials");
        return new Models.SigninResponse(auth.issueAccessToken(user.userId()), auth.issueRefreshToken(user.userId()));
    }

    @POST
    @Path("/refresh")
    @Consumes(MediaType.APPLICATION_JSON)
    public Models.RefreshTokenResponse refresh(Models.RefreshTokenRequest request) {
        return new Models.RefreshTokenResponse(auth.issueAccessToken(auth.verifyRefreshToken(request.refreshToken())));
    }

    @GET
    @Path("/{id}")
    public Models.User getUser(@PathParam("id") long id) { return repository.findUser(id); }

    private static void validate(String... values) {
        for (String value : values) if (value == null || value.isBlank()) throw new BadRequestException("Required field is missing");
    }
}
