package vn.fbus.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.fbus.common.ApiResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;

@Component
public class AuthFilter extends OncePerRequestFilter {
    public record Identity(long userId, String sessionId) {}
    private final JwtDecoder decoder;
    private final AuthRepository repository;
    private final ObjectMapper json;
    private final Clock clock;
    public AuthFilter(JwtDecoder decoder, AuthRepository repository, ObjectMapper json, Clock clock) {
        this.decoder=decoder; this.repository=repository; this.json=json; this.clock=clock;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
                var token = decoder.decode(header.substring(7));
                var session = repository.findSession(token.getClaimAsString("sid")).orElseThrow(IllegalArgumentException::new);
                long userId = Long.parseLong(token.getSubject());
                if (session.revoked() || session.userId()!=userId || !session.expiresAt().isAfter(clock.instant())) throw new IllegalArgumentException();
                var user = repository.findUser(userId).orElseThrow(IllegalArgumentException::new);
                if (!user.status().equals("ACTIVE") || !repository.emailAllowed(user)) throw new IllegalArgumentException();
                var auth = new UsernamePasswordAuthenticationToken(new Identity(userId, session.id()), null, List.of(new SimpleGrantedAuthority("ROLE_"+user.role())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException error) {
                response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                json.writeValue(response.getWriter(), new ApiResponse<>(401,"Phiên đăng nhập không hợp lệ hoặc đã hết hạn.",null)); return;
            }
        }
        chain.doFilter(request,response);
    }
}
