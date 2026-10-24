package vn.fbus.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component @Profile("dev")
public class DevelopmentUsers implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public DevelopmentUsers(JdbcTemplate jdbc, PasswordEncoder passwords) { this.jdbc=jdbc; this.passwords=passwords; }
    @Override public void run(String... args) {
        seed("admin", "admin@example.com", "Admin@12345", "Quản trị viên", "ADMIN");
        seed("student", "student@example.com", "Student@12345", "Học sinh", "STUDENT");
        seed("driver", "driver@example.com", "Driver@12345", "Tài xế", "DRIVER");
        seed("attendant", "attendant@example.com", "Attendant@12345", "Bus attendant", "BUS_ATTENDANT");
        seed("lecturer", "lecturer@example.com", "Lecturer@12345", "Giảng viên", "LECTURER");
    }
    private void seed(String username, String email, String password, String name, String role) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username=?", Integer.class, username) == 0) {
            jdbc.update("INSERT INTO app_user(username,email,password_hash,full_name,role) VALUES (?,?,?,?,?)", username,email,passwords.encode(password),name,role);
            if (!role.equals("ADMIN") && jdbc.queryForObject("SELECT COUNT(*) FROM email_access WHERE email=?",Integer.class,email)==0)
                jdbc.update("INSERT INTO email_access(email,full_name,role) VALUES (?,?,?)",email,name,role);
        }
    }
}
