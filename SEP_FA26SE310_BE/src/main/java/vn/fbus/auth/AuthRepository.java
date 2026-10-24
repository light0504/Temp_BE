package vn.fbus.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class AuthRepository {
    public record User(long id, String username, String email, String passwordHash, String fullName, String role, String status) {}
    public record Session(String id, long userId, String refreshHash, Instant expiresAt, boolean revoked) {}
    private final JdbcTemplate jdbc;
    private final RowMapper<User> users = (rs, n) -> new User(rs.getLong("id"), rs.getString("username"), rs.getString("email"),
        rs.getString("password_hash"), rs.getString("full_name"), rs.getString("role"), rs.getString("status"));
    private final RowMapper<Session> sessions = (rs, n) -> new Session(rs.getString("id"), rs.getLong("user_id"),
        rs.getString("refresh_hash"), rs.getObject("expires_at", OffsetDateTime.class).toInstant(), rs.getBoolean("revoked"));
    public AuthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<User> findLogin(String login) {
        return jdbc.query("SELECT * FROM app_user WHERE LOWER(username)=? OR LOWER(email)=?", users, login.toLowerCase(), login.toLowerCase()).stream().findFirst();
    }
    public Optional<User> findUser(long id) { return jdbc.query("SELECT * FROM app_user WHERE id=?", users, id).stream().findFirst(); }
    public boolean emailAllowed(User user) {
        return user.role().equals("ADMIN") || jdbc.queryForObject("SELECT COUNT(*) FROM email_access WHERE email=? AND role=?",Integer.class,user.email().trim().toLowerCase(java.util.Locale.ROOT),user.role())==1;
    }
    public Optional<Session> findSession(String id) { return jdbc.query("SELECT * FROM auth_session WHERE id=?", sessions, id).stream().findFirst(); }
    public Optional<Session> lockRefresh(String hash) {
        return jdbc.query("SELECT * FROM auth_session WHERE refresh_hash=? FOR UPDATE", sessions, hash).stream().findFirst();
    }
    public void createSession(String id, long user, String hash, Instant expiry) {
        jdbc.update("INSERT INTO auth_session(id,user_id,refresh_hash,expires_at) VALUES (?,?,?,?)", id, user, hash, OffsetDateTime.ofInstant(expiry, java.time.ZoneOffset.UTC));
    }
    public void rotate(String id, String hash) { jdbc.update("UPDATE auth_session SET refresh_hash=? WHERE id=?", hash, id); }
    public void revoke(String id) { jdbc.update("UPDATE auth_session SET revoked=TRUE WHERE id=?", id); }
}
