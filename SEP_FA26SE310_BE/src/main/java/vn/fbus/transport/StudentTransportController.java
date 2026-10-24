package vn.fbus.transport;

import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.*;
import java.time.LocalDate;

@RestController @RequestMapping("/api/student")
public class StudentTransportController {
    private final TransportRepository repository;
    private final TransportService service;
    private final JdbcTemplate jdbc;
    public StudentTransportController(TransportRepository repository, TransportService service, JdbcTemplate jdbc) { this.repository=repository; this.service=service; this.jdbc=jdbc; }
    @GetMapping("/routes") ApiResponse<?> routes() { return ApiResponse.ok(repository.routes()); }
    @GetMapping("/stops") ApiResponse<?> stops(@RequestParam long routeId) { return ApiResponse.ok(repository.route(routeId).stops()); }
    @GetMapping("/trips") ApiResponse<?> trips(@RequestParam LocalDate date, @RequestParam(required=false) Long routeId,
        @RequestParam(required=false) Long boardingStopId, @RequestParam(required=false) Long alightingStopId, @RequestParam(required=false) Integer slot) {
        return ApiResponse.ok(service.search(date,routeId,boardingStopId,alightingStopId,slot));
    }
    @GetMapping("/trips/{id}/stops") ApiResponse<?> tripStops(@PathVariable long id) { repository.trip(id,false); return ApiResponse.ok(repository.tripStops(id)); }
    @GetMapping("/trips/{id}/seats") ApiResponse<?> seats(@PathVariable long id, @RequestParam long boardingTripStopId, @RequestParam long alightingTripStopId) {
        return ApiResponse.ok(service.availableSeats(id,boardingTripStopId,alightingTripStopId));
    }
    @PutMapping("/profile/preferred-stop") ApiResponse<?> preferred(@AuthenticationPrincipal Identity identity, @Valid @RequestBody Contracts.PreferredStop input) {
        ApiException.require(jdbc.queryForObject("SELECT COUNT(*) FROM stop WHERE id=?",Integer.class,input.stopId())==1,404,"Điểm dừng không tồn tại.");
        jdbc.update("UPDATE app_user SET preferred_stop_id=? WHERE id=?",input.stopId(),identity.userId()); return ApiResponse.ok(input.stopId());
    }
}
