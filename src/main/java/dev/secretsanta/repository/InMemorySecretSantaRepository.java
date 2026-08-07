package dev.secretsanta.repository;

import dev.secretsanta.model.Models;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemorySecretSantaRepository {
    private record StoredUser(Models.User user, String passwordHash) {}
    private record StoredGroup(Models.Group group, List<Long> participants,
                               Map<Long, Long> friends) {}

    private final AtomicLong userIds = new AtomicLong();
    private final Map<Long, StoredUser> users = new ConcurrentHashMap<>();
    private final Map<String, StoredGroup> groups = new ConcurrentHashMap<>();

    public synchronized Models.User createUser(Models.CreateUserRequest request, String passwordHash) {
        if (users.values().stream().anyMatch(u -> u.user.email().equalsIgnoreCase(request.email()))) {
            throw new IllegalArgumentException("Email is already registered");
        }
        long id = userIds.incrementAndGet();
        var user = new Models.User(id, request.userName(), request.email(), null, null);
        users.put(id, new StoredUser(user, passwordHash));
        return user;
    }

    public Models.User findUser(long id) {
        var stored = users.get(id);
        if (stored == null) throw new IllegalArgumentException("User not found");
        return stored.user;
    }

    public Models.User findUserByEmail(String email) {
        return users.values().stream()
                .filter(u -> u.user.email().equalsIgnoreCase(email))
                .map(StoredUser::user).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
    }

    public String passwordHash(long id) {
        var stored = users.get(id);
        if (stored == null) throw new IllegalArgumentException("User not found");
        return stored.passwordHash;
    }

    public Models.Group createGroup(Models.CreateGroupRequest request) {
        findUser(request.creatorUserId());
        String id = "grp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var group = new Models.Group(id, request.name(), request.dateCreated(),
                request.dateDraw(), request.creatorUserId());
        groups.put(id, new StoredGroup(group, new ArrayList<>(), new ConcurrentHashMap<>()));
        return group;
    }

    public Models.Group findGroup(String id) {
        var stored = groups.get(id);
        if (stored == null) throw new IllegalArgumentException("Group not found");
        return stored.group;
    }

    public synchronized Models.ParticipantRequest addParticipant(String groupId, long userId) {
        findUser(userId);
        var stored = group(groupId);
        if (!stored.participants.contains(userId)) stored.participants.add(userId);
        return new Models.ParticipantRequest(groupId, userId);
    }

    public synchronized void draw(String groupId) {
        var stored = group(groupId);
        if (stored.participants.size() < 2) throw new IllegalArgumentException("At least two participants are required");
        var shuffled = new ArrayList<>(stored.participants);
        java.util.Collections.rotate(shuffled, 1);
        stored.friends.clear();
        for (int i = 0; i < stored.participants.size(); i++) {
            stored.friends.put(stored.participants.get(i), shuffled.get(i));
        }
    }

    public Models.User secretFriend(String groupId, long userId) {
        var stored = group(groupId);
        Long friendId = stored.friends.get(userId);
        if (friendId == null) throw new IllegalStateException("Secret friend has not been drawn yet");
        return findUser(friendId);
    }

    private StoredGroup group(String id) {
        var group = groups.get(id);
        if (group == null) throw new IllegalArgumentException("Group not found");
        return group;
    }
}
