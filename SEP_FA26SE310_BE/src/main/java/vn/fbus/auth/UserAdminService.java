package vn.fbus.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.common.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import static vn.fbus.common.ApiException.require;

@Service
public class UserAdminService {
    private final JdbcTemplate jdbc;
    private final Database db;
    private final PasswordEncoder passwords;
    public UserAdminService(JdbcTemplate jdbc, Database db, PasswordEncoder passwords) { this.jdbc=jdbc; this.db=db; this.passwords=passwords; }
    @Transactional
    public Map<String,Object> create(Contracts.NewUser input) {
        String username=input.username().toLowerCase(Locale.ROOT), email=input.email().trim().toLowerCase(Locale.ROOT);
        require(input.password().getBytes(StandardCharsets.UTF_8).length<=72,400,"Mật khẩu tối đa 72 byte.");
        jdbc.queryForList("SELECT name FROM operation_lock WHERE name='email-access' FOR UPDATE");
        var allowed=jdbc.queryForList("SELECT role FROM email_access WHERE email=?",String.class,email);
        require(allowed.isEmpty() || allowed.getFirst().equals(input.role()),409,"Email đã được cấp vai trò khác.");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE LOWER(username) IN (?,?) OR LOWER(email) IN (?,?)",Integer.class,username,email,username,email)==0,409,"Tên đăng nhập hoặc email đã tồn tại.");
        long id=db.insert("INSERT INTO app_user(username,email,password_hash,full_name,role) VALUES (?,?,?,?,?)",username,email,passwords.encode(input.password()),input.fullName().trim(),input.role());
        if (allowed.isEmpty()) jdbc.update("INSERT INTO email_access(email,full_name,role) VALUES (?,?,?)",email,input.fullName().trim(),input.role());
        return Map.of("userId",id,"username",username,"email",email,"fullName",input.fullName().trim(),"role",input.role());
    }
}
