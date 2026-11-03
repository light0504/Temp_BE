package vn.fbus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
@EnableScheduling
public class FbusApplication {
    public static void main(String[] args) { SpringApplication.run(FbusApplication.class, args); }
    @Bean Clock clock() { return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")); }
}
