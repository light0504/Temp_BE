package vn.fbus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import vn.fbus.auth.AuthFilter;
import vn.fbus.common.ApiResponse;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecretKey jwtKey(@Value("${app.jwt-secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("fbus")); return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, AuthFilter auth, ObjectMapper json,
                                      UrlBasedCorsConfigurationSource corsConfigurationSource) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(access -> access.requestMatchers("/api/login", "/api/refresh-token", "/api/health").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN").requestMatchers("/api/student/**").hasAnyRole("STUDENT","LECTURER")
                .requestMatchers("/api/staff/**").hasAnyRole("DRIVER","BUS_ATTENDANT")
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint((req,res,error) -> {
                res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                json.writeValue(res.getWriter(), new ApiResponse<>(401,"Vui lòng đăng nhập.",null));
            }).accessDeniedHandler((req,res,error) -> {
                res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                json.writeValue(res.getWriter(), new ApiResponse<>(403,"Tài khoản không có quyền truy cập.",null));
            })).addFilterBefore(auth, UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean UrlBasedCorsConfigurationSource corsConfigurationSource(@Value("${app.allowed-origins}") String origins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET","POST","PUT","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type","Accept"));
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", config); return source;
    }
}
