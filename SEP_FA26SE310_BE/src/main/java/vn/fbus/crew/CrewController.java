package vn.fbus.crew;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.ApiResponse;
import java.time.*;
import static vn.fbus.crew.CrewContracts.*;

@RestController @RequestMapping("/api/staff")
public class CrewController {
 private final CrewService service;private final Clock clock;
 public CrewController(CrewService service,Clock clock){this.service=service;this.clock=clock;}
 @GetMapping("/schedule") ApiResponse<?> schedule(@AuthenticationPrincipal Identity who,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){var start=from==null?LocalDate.now(clock):from;return ApiResponse.ok(service.list(who.userId(),start,to==null?start.plusDays(30):to));}
 @GetMapping("/trips/{id}") ApiResponse<?> detail(@AuthenticationPrincipal Identity who,@PathVariable long id){return ApiResponse.ok(service.detail(who.userId(),id));}
 @PutMapping("/trips/{id}/progress") ApiResponse<?> progress(@AuthenticationPrincipal Identity who,@PathVariable long id,@Valid @RequestBody Progress input){return ApiResponse.ok(service.progress(who.userId(),id,input));}
 @PutMapping("/trips/{id}/passengers/{bookingId}") ApiResponse<?> mark(@AuthenticationPrincipal Identity who,@PathVariable long id,@PathVariable long bookingId,@Valid @RequestBody Attendance input){return ApiResponse.ok(service.mark(who.userId(),id,bookingId,input));}
 @GetMapping("/trips/{id}/reports") ApiResponse<?> reports(@AuthenticationPrincipal Identity who,@PathVariable long id){return ApiResponse.ok(service.reports(who.userId(),id));}
 @PostMapping("/trips/{id}/reports") ApiResponse<?> report(@AuthenticationPrincipal Identity who,@PathVariable long id,@Valid @RequestBody ReportInput input){return ApiResponse.ok(service.report(who.userId(),id,input));}
}

