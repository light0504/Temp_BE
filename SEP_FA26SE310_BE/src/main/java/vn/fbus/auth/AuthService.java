package vn.fbus.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.common.ApiException;
import vn.fbus.common.Contracts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

@Service
public class AuthService {
    public record Tokens(String token, String accessToken, String refreshToken, String fullName, String email, List<String> roles) {}
    private final AuthRepository repository;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwt;
    private final Clock clock;
    private final String dummyHash;
    public AuthService(AuthRepository repository, PasswordEncoder passwords, JwtEncoder jwt, Clock clock) {
        this.repository = repository; this.passwords = passwords; this.jwt = jwt; this.clock = clock;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }
    @Transactional
    public Tokens login(Contracts.Login input) {
        var user = repository.findLogin(input.username().trim()).orElse(null);
        boolean valid = passwords.matches(input.password(), user == null ? dummyHash : user.passwordHash());
        ApiException.require(user != null && valid && user.status().equals("ACTIVE"), 401, "Tên đăng nhập hoặc mật khẩu không đúng.");
        ApiException.require(repository.emailAllowed(user),403,"Email chưa được cấp quyền đăng nhập.");
        String sessionId = UUID.randomUUID().toString(), refresh = newRefresh();
        repository.createSession(sessionId, user.id(), hash(refresh), clock.instant().plus(Duration.ofDays(7)));
        return tokens(user, sessionId, refresh);
    }
    @Transactional
    public Tokens refresh(String refresh) {
        var session = repository.lockRefresh(hash(refresh)).orElseThrow(() -> new ApiException(401, "Phiên đăng nhập không hợp lệ."));
        ApiException.require(!session.revoked() && session.expiresAt().isAfter(clock.instant()), 401, "Phiên đăng nhập đã hết hạn.");
        var user = repository.findUser(session.userId()).orElseThrow(() -> new ApiException(401, "Tài khoản không tồn tại."));
        ApiException.require(user.status().equals("ACTIVE"), 401, "Tài khoản không khả dụng.");
        ApiException.require(repository.emailAllowed(user),403,"Email chưa được cấp quyền đăng nhập.");
        String next = newRefresh(); repository.rotate(session.id(), hash(next));
        return tokens(user, session.id(), next);
    }
    private Tokens tokens(AuthRepository.User user, String sessionId, String refresh) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer("fbus").subject(Long.toString(user.id())).issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(30))).claim("sid", sessionId).claim("roles", List.of(user.role()))
            .claim("fullName", user.fullName()).claim("email", user.email()).build();
        String token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Tokens(token, token, refresh, user.fullName(), user.email(), List.of(user.role()));
    }
    private String newRefresh() {
        byte[] bytes = new byte[48]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
