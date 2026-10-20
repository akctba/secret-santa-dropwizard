package dev.secretsanta.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.secretsanta.TestFixtures;
import dev.secretsanta.model.Models;
import dev.secretsanta.repository.SqliteSecretSantaRepository;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupResourceTest {
    private SqliteSecretSantaRepository repository;
    private GroupResource resource;
    private Models.User creator;
    private Models.User participant;
    private Models.User outsider;
    private Models.Group group;

    @BeforeEach
    void setUp() {
        repository = new SqliteSecretSantaRepository(":memory:");
        resource = new GroupResource(repository);
        creator = createUser("Alice", "alice@example.com");
        participant = createUser("Bob", "bob@example.com");
        outsider = createUser("Carol", "carol@example.com");
        group = (Models.Group) resource.createGroup(new Models.CreateGroupRequest(
                "Family", Instant.EPOCH, Instant.EPOCH.plusSeconds(86400), creator.userId()))
                .getEntity();
    }

    @Test
    void createsAndRetrievesGroups() {
        assertEquals(group, resource.getGroup(group.groupId()));
        assertEquals(201, resource.createGroup(new Models.CreateGroupRequest(
                "Another", Instant.EPOCH, Instant.EPOCH, creator.userId())).getStatus());
        assertThrows(IllegalArgumentException.class, () -> resource.getGroup("missing"));
    }

    @Test
    void addsParticipantsAndRequiresMatchingPathAndBodyIds() {
        Response response = resource.addParticipant(group.groupId(),
                new Models.ParticipantRequest(group.groupId(), participant.userId()));

        assertEquals(201, response.getStatus());
        assertEquals(new Models.ParticipantRequest(group.groupId(), participant.userId()), response.getEntity());
        assertThrows(BadRequestException.class, () -> resource.addParticipant(group.groupId(),
                new Models.ParticipantRequest("different-group", participant.userId())));
        assertThrows(IllegalArgumentException.class, () -> resource.addParticipant(group.groupId(),
                new Models.ParticipantRequest(group.groupId(), 999L)));
    }

    @Test
    void listsParticipantsForCreatorOrParticipantOnly() {
        repository.addParticipant(group.groupId(), participant.userId());

        assertEquals(List.of(participant), resource.listParticipants(group.groupId(), authenticatedAs(creator.userId())));
        assertEquals(List.of(participant), resource.listParticipants(group.groupId(), authenticatedAs(participant.userId())));
        assertThrows(NotAuthorizedException.class,
                () -> resource.listParticipants(group.groupId(), authenticatedAs(outsider.userId())));
        assertThrows(NotAuthorizedException.class,
                () -> resource.listParticipants(group.groupId(), authenticatedAs(null)));
    }

    @Test
    void runsDrawAndReturnsTheAuthenticatedUsersSecretFriend() {
        repository.addParticipant(group.groupId(), creator.userId());
        repository.addParticipant(group.groupId(), participant.userId());

        Response drawResponse = resource.runDraw(group.groupId());

        assertEquals(200, drawResponse.getStatus());
        assertEquals(participant, resource.getSecretFriend(group.groupId(), authenticatedAs(creator.userId())));
        assertEquals(creator, resource.getSecretFriend(group.groupId(), authenticatedAs(participant.userId())));
    }

    @Test
    void reportsDrawAndFriendLookupFailures() {
        assertThrows(IllegalArgumentException.class, () -> resource.runDraw(group.groupId()));
        assertThrows(IllegalStateException.class,
                () -> resource.getSecretFriend(group.groupId(), authenticatedAs(creator.userId())));
        assertThrows(IllegalArgumentException.class, () -> resource.runDraw("missing"));
        assertThrows(IllegalArgumentException.class,
                () -> resource.getSecretFriend("missing", authenticatedAs(creator.userId())));
        assertThrows(NotAuthorizedException.class,
                () -> resource.getSecretFriend(group.groupId(), authenticatedAs(null)));
    }

    private Models.User createUser(String name, String email) {
        return repository.createUser(new Models.CreateUserRequest(name, email, "password"), "hash");
    }

    private static ContainerRequestContext authenticatedAs(Long userId) {
        var properties = new HashMap<String, Object>();
        if (userId != null) properties.put("authenticatedUserId", userId);
        return TestFixtures.requestContext("v1/group", null, properties, new AtomicReference<>());
    }
}
