package dev.secretsanta.resource;

import dev.secretsanta.model.Models;
import dev.secretsanta.repository.SqliteSecretSantaRepository;
import java.util.List;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/v1/group")
@Produces(MediaType.APPLICATION_JSON)
public final class GroupResource {
    private final SqliteSecretSantaRepository repository;
    public GroupResource(SqliteSecretSantaRepository repository) { this.repository = repository; }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createGroup(Models.CreateGroupRequest request) {
        return Response.status(Response.Status.CREATED).entity(repository.createGroup(request)).build();
    }

    @GET
    @Path("/{id}")
    public Models.Group getGroup(@PathParam("id") String id) { return repository.findGroup(id); }

    @POST
    @Path("/{id}/participant")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addParticipant(@PathParam("id") String id, Models.ParticipantRequest request) {
        if (!id.equals(request.groupId())) throw new BadRequestException("Path and body group_id must match");
        return Response.status(Response.Status.CREATED).entity(repository.addParticipant(id, request.userId())).build();
    }

    @GET
    @Path("/{id}/participants")
    public List<Models.User> listParticipants(@PathParam("id") String id,
                                              @Context ContainerRequestContext request) {
        Object userId = request.getProperty("authenticatedUserId");
        if (!(userId instanceof Long authenticatedUserId)) throw new NotAuthorizedException("Missing or invalid token");
        if (!repository.canViewParticipants(id, authenticatedUserId)) {
            throw new NotAuthorizedException("You are not part of this group and cannot view its participants");
        }
        return repository.listParticipants(id);
    }

    @POST
    @Path("/{id}/draw")
    public Response runDraw(@PathParam("id") String id) {
        repository.draw(id);
        return Response.ok().build();
    }

    @GET
    @Path("/{id}/friend")
    public Models.User getSecretFriend(@PathParam("id") String id,
                                       @Context ContainerRequestContext request) {
        Object userId = request.getProperty("authenticatedUserId");
        if (!(userId instanceof Long authenticatedUserId)) throw new NotAuthorizedException("Missing or invalid token");
        return repository.secretFriend(id, authenticatedUserId);
    }
}
