package dev.secretsanta.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.secretsanta.model.Models;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemorySecretSantaRepositoryTest {
    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("grp_[0-9a-f]{12}");

    private InMemorySecretSantaRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemorySecretSantaRepository();
    }

    @Test
    void createsUsersAndFindsThemByIdAndCaseInsensitiveEmail() {
        Models.User first = createUser("Alice", "Alice@example.com", "hash-a");
        Models.User second = createUser("Bob", "bob@example.com", "hash-b");

        assertEquals(1L, first.userId());
        assertEquals(2L, second.userId());
        assertEquals(first, repository.findUser(first.userId()));
        assertEquals(first, repository.findUserByEmail("ALICE@EXAMPLE.COM"));
        assertEquals("hash-a", repository.passwordHash(first.userId()));
        assertEquals(null, first.gender());
        assertEquals(null, first.dateOfBirth());
    }

    @Test
    void rejectsDuplicateEmailsIgnoringCase() {
        createUser("Alice", "Alice@example.com", "hash-a");

        assertThrows(IllegalArgumentException.class,
                () -> repository.createUser(userRequest("Alicia", "ALICE@EXAMPLE.COM", "secret"), "hash-b"));
    }

    @Test
    void reportsMissingUsers() {
        assertThrows(IllegalArgumentException.class, () -> repository.findUser(1L));
        assertThrows(IllegalArgumentException.class, () -> repository.findUserByEmail("missing@example.com"));
        assertThrows(IllegalArgumentException.class, () -> repository.passwordHash(1L));
    }

    @Test
    void createsGroupsForExistingCreators() {
        Models.User creator = createUser("Alice", "alice@example.com", "hash");
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant draw = Instant.parse("2026-12-01T00:00:00Z");

        Models.Group group = repository.createGroup(new Models.CreateGroupRequest(
                "Family", created, draw, creator.userId()));

        assertTrue(GROUP_ID_PATTERN.matcher(group.groupId()).matches());
        assertEquals("Family", group.name());
        assertEquals(created, group.dateCreated());
        assertEquals(draw, group.dateDraw());
        assertEquals(creator.userId(), group.creatorUserId());
        assertEquals(group, repository.findGroup(group.groupId()));
        assertThrows(IllegalArgumentException.class, () -> repository.createGroup(
                new Models.CreateGroupRequest("Invalid", created, draw, 999L)));
    }

    @Test
    void reportsMissingGroups() {
        assertThrows(IllegalArgumentException.class, () -> repository.findGroup("missing"));
        assertThrows(IllegalArgumentException.class, () -> repository.listParticipants("missing"));
        assertThrows(IllegalArgumentException.class, () -> repository.canViewParticipants("missing", 1L));
        assertThrows(IllegalArgumentException.class, () -> repository.draw("missing"));
        assertThrows(IllegalArgumentException.class, () -> repository.secretFriend("missing", 1L));
    }

    @Test
    void addsParticipantsOnlyOnceAndListsUsers() {
        Models.User creator = createUser("Alice", "alice@example.com", "hash-a");
        Models.User participant = createUser("Bob", "bob@example.com", "hash-b");
        Models.Group group = createGroup(creator.userId());

        Models.ParticipantRequest added = repository.addParticipant(group.groupId(), participant.userId());
        repository.addParticipant(group.groupId(), participant.userId());

        assertEquals(new Models.ParticipantRequest(group.groupId(), participant.userId()), added);
        assertEquals(List.of(participant), repository.listParticipants(group.groupId()));
        assertThrows(IllegalArgumentException.class,
                () -> repository.addParticipant(group.groupId(), 999L));
    }

    @Test
    void participantVisibilityIsLimitedToCreatorAndParticipants() {
        Models.User creator = createUser("Alice", "alice@example.com", "hash-a");
        Models.User participant = createUser("Bob", "bob@example.com", "hash-b");
        Models.User outsider = createUser("Carol", "carol@example.com", "hash-c");
        Models.Group group = createGroup(creator.userId());
        repository.addParticipant(group.groupId(), participant.userId());

        assertTrue(repository.canViewParticipants(group.groupId(), creator.userId()));
        assertTrue(repository.canViewParticipants(group.groupId(), participant.userId()));
        assertFalse(repository.canViewParticipants(group.groupId(), outsider.userId()));
    }

    @Test
    void drawRequiresTwoParticipantsAndCannotReturnFriendBeforeDraw() {
        Models.User creator = createUser("Alice", "alice@example.com", "hash-a");
        Models.User participant = createUser("Bob", "bob@example.com", "hash-b");
        Models.Group group = createGroup(creator.userId());

        assertThrows(IllegalStateException.class,
                () -> repository.secretFriend(group.groupId(), creator.userId()));
        assertThrows(IllegalArgumentException.class, () -> repository.draw(group.groupId()));
        repository.addParticipant(group.groupId(), creator.userId());
        repository.addParticipant(group.groupId(), participant.userId());
        repository.draw(group.groupId());
    }

    @Test
    void drawAssignsEveryParticipantToAnotherParticipantInTheGroup() {
        Models.User alice = createUser("Alice", "alice@example.com", "hash-a");
        Models.User bob = createUser("Bob", "bob@example.com", "hash-b");
        Models.User carol = createUser("Carol", "carol@example.com", "hash-c");
        Models.Group group = createGroup(alice.userId());
        List<Models.User> participants = List.of(alice, bob, carol);
        participants.forEach(user -> repository.addParticipant(group.groupId(), user.userId()));

        repository.draw(group.groupId());

        var assignedIds = new HashSet<Long>();
        for (Models.User participant : participants) {
            Models.User friend = repository.secretFriend(group.groupId(), participant.userId());
            assertTrue(participants.contains(friend));
            assertNotEquals(participant.userId(), friend.userId());
            assignedIds.add(friend.userId());
        }
        assertEquals(participants.size(), assignedIds.size());

        repository.draw(group.groupId());
        assertEquals(participants.size(), participants.stream()
                .map(user -> repository.secretFriend(group.groupId(), user.userId()).userId())
                .distinct().count());
    }

    private Models.User createUser(String name, String email, String passwordHash) {
        return repository.createUser(userRequest(name, email, "secret"), passwordHash);
    }

    private Models.CreateUserRequest userRequest(String name, String email, String password) {
        return new Models.CreateUserRequest(name, email, password);
    }

    private Models.Group createGroup(long creatorId) {
        return repository.createGroup(new Models.CreateGroupRequest(
                "Test group", Instant.EPOCH, Instant.EPOCH.plusSeconds(3600), creatorId));
    }
}
