package vn.fbus.transport;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.ApiResponse;
import java.time.*;

@RestController @RequestMapping("/api/staff")
public class StaffController {
    private final TransportRepository repository;
    private final Clock clock;
    public StaffController(TransportRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }
    @GetMapping("/trips") ApiResponse<?> trips(@AuthenticationPrincipal Identity identity) {
        return ApiResponse.ok(repository.assignedTrips(identity.userId(),LocalDate.now(clock).atStartOfDay()).stream()
            .map(t -> repository.view(t,0,repository.tripStops(t.id()).size()-1)).toList());
    }
}
