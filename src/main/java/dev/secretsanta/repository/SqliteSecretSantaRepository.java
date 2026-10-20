package dev.secretsanta.repository;

import dev.secretsanta.model.Models;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SQLite-backed repository. Pass a file path, or ":memory:" for a throwaway database. */
public final class SqliteSecretSantaRepository implements AutoCloseable {
    private static final String USER_COLUMNS = "user_id, user_name, user_email, gender, date_of_birth";

    private final Connection connection;

    public SqliteSecretSantaRepository(String databasePath) {
        try {
            if (!":memory:".equals(databasePath)) {
                Path parent = Path.of(databasePath).toAbsolutePath().getParent();
                if (parent != null) Files.createDirectories(parent);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                for (String ddl : loadSchema().split(";")) {
                    if (!ddl.isBlank()) st.execute(ddl);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to open database " + databasePath, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String loadSchema() throws IOException {
        try (var in = SqliteSecretSantaRepository.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IOException("schema.sql not found on classpath");
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return raw.lines().filter(l -> !l.trim().startsWith("--")).reduce("", (a, b) -> a + b + "\n");
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized Models.User createUser(Models.CreateUserRequest request, String passwordHash) {
        try {
            if (exists("SELECT 1 FROM users WHERE user_email = ?", request.email())) {
                throw new IllegalArgumentException("Email is already registered");
            }
            try (var ps = connection.prepareStatement(
                    "INSERT INTO users (user_name, user_email, password_hash) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, request.userName());
                ps.setString(2, request.email());
                ps.setString(3, passwordHash);
                ps.executeUpdate();
                try (var keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return new Models.User(keys.getLong(1), request.userName(), request.email(), null, null);
                }
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public synchronized Models.User findUser(long id) {
        try (var ps = connection.prepareStatement("SELECT " + USER_COLUMNS + " FROM users WHERE user_id = ?")) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("User not found");
                return user(rs);
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public synchronized Models.User findUserByEmail(String email) {
        try (var ps = connection.prepareStatement("SELECT " + USER_COLUMNS + " FROM users WHERE user_email = ?")) {
            ps.setString(1, email);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Invalid credentials");
                return user(rs);
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public synchronized String passwordHash(long id) {
        try (var ps = connection.prepareStatement("SELECT password_hash FROM users WHERE user_id = ?")) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("User not found");
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public synchronized Models.Group createGroup(Models.CreateGroupRequest request) {
        findUser(request.creatorUserId());
        String id = "grp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (var ps = connection.prepareStatement(
                "INSERT INTO groups (group_id, name, creator_user_id, created_date, draw_date) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, request.name());
            ps.setLong(3, request.creatorUserId());
            ps.setString(4, toText(request.dateCreated()));
            ps.setString(5, toText(request.dateDraw()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw failure(e);
        }
        return new Models.Group(id, request.name(), request.dateCreated(),
                request.dateDraw(), request.creatorUserId());
    }

    public synchronized Models.Group findGroup(String id) {
        try (var ps = connection.prepareStatement(
                "SELECT group_id, name, created_date, draw_date, creator_user_id FROM groups WHERE group_id = ?")) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Group not found");
                return new Models.Group(rs.getString(1), rs.getString(2), toInstant(rs.getString(3)),
                        toInstant(rs.getString(4)), rs.getLong(5));
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public synchronized Models.ParticipantRequest addParticipant(String groupId, long userId) {
        findUser(userId);
        findGroup(groupId);
        try (var ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO participants (group_id, user_id, joined_at) VALUES (?, ?, ?)")) {
            ps.setString(1, groupId);
            ps.setLong(2, userId);
            ps.setString(3, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw failure(e);
        }
        return new Models.ParticipantRequest(groupId, userId);
    }

    public synchronized List<Models.User> listParticipants(String groupId) {
        findGroup(groupId);
        return participantIds(groupId).stream().map(this::findUser).toList();
    }

    public synchronized boolean canViewParticipants(String groupId, long userId) {
        Models.Group group = findGroup(groupId);
        if (group.creatorUserId() == userId) return true;
        try {
            return exists("SELECT 1 FROM participants WHERE group_id = ? AND user_id = ?", groupId, userId);
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public synchronized void draw(String groupId) {
        findGroup(groupId);
        List<Long> participants = participantIds(groupId);
        if (participants.size() < 2) throw new IllegalArgumentException("At least two participants are required");
        var shuffled = new ArrayList<>(participants);
        java.util.Collections.rotate(shuffled, 1);
        try {
            connection.setAutoCommit(false);
            try (var ps = connection.prepareStatement(
                    "UPDATE participants SET friend_user_id = ? WHERE group_id = ? AND user_id = ?")) {
                for (int i = 0; i < participants.size(); i++) {
                    ps.setLong(1, shuffled.get(i));
                    ps.setString(2, groupId);
                    ps.setLong(3, participants.get(i));
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public synchronized Models.User secretFriend(String groupId, long userId) {
        findGroup(groupId);
        try (var ps = connection.prepareStatement(
                "SELECT friend_user_id FROM participants WHERE group_id = ? AND user_id = ?")) {
            ps.setString(1, groupId);
            ps.setLong(2, userId);
            try (var rs = ps.executeQuery()) {
                long friendId = rs.next() ? rs.getLong(1) : 0;
                if (friendId == 0) throw new IllegalStateException("Secret friend has not been drawn yet");
                return findUser(friendId);
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    private List<Long> participantIds(String groupId) {
        try (var ps = connection.prepareStatement(
                "SELECT user_id FROM participants WHERE group_id = ? ORDER BY rowid")) {
            ps.setString(1, groupId);
            try (var rs = ps.executeQuery()) {
                var ids = new ArrayList<Long>();
                while (rs.next()) ids.add(rs.getLong(1));
                return ids;
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    private boolean exists(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Models.User user(ResultSet rs) throws SQLException {
        return new Models.User(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), toInstant(rs.getString(5)));
    }

    private static String toText(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant toInstant(String text) {
        return text == null ? null : Instant.parse(text);
    }

    private static IllegalStateException failure(SQLException e) {
        return new IllegalStateException("Database error: " + e.getMessage(), e);
    }
}
