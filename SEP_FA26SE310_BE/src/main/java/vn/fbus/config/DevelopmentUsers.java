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
        seed("admin2", "admin2@example.com", "Admin2@12345", "Quản trị viên 2", "ADMIN");
        seed("admin3", "admin3@example.com", "Admin3@12345", "Quản trị viên 3", "ADMIN");
        seed("student2", "student2@example.com", "Student2@12345", "Học sinh 2", "STUDENT");
        seed("student3", "student3@example.com", "Student3@12345", "Học sinh 3", "STUDENT");
        seed("driver2", "driver2@example.com", "Driver2@12345", "Tài xế 2", "DRIVER");
        seed("driver3", "driver3@example.com", "Driver3@12345", "Tài xế 3", "DRIVER");
        seed("attendant2", "attendant2@example.com", "Attendant2@12345", "Bus attendant 2", "BUS_ATTENDANT");
        seed("attendant3", "attendant3@example.com", "Attendant3@12345", "Bus attendant 3", "BUS_ATTENDANT");
        seed("lecturer2", "lecturer2@example.com", "Lecturer2@12345", "Giảng viên 2", "LECTURER");
        seed("lecturer3", "lecturer3@example.com", "Lecturer3@12345", "Giảng viên 3", "LECTURER");
    }
    private void seed(String username, String email, String password, String name, String role) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username=?", Integer.class, username) == 0) {
            jdbc.update("INSERT INTO app_user(username,email,password_hash,full_name,role) VALUES (?,?,?,?,?)", username,email,passwords.encode(password),name,role);
            if (!role.equals("ADMIN") && jdbc.queryForObject("SELECT COUNT(*) FROM email_access WHERE email=?",Integer.class,email)==0)
                jdbc.update("INSERT INTO email_access(email,full_name,role) VALUES (?,?,?)",email,name,role);
        }
    }
}
