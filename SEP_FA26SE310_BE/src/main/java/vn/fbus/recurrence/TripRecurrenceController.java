package vn.fbus.recurrence;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.ApiResponse;
@RestController @RequestMapping("/api/admin/trips/recurring")
public class TripRecurrenceController {
    private final TripRecurrenceService service;
    public TripRecurrenceController(TripRecurrenceService service){this.service=service;}
    @GetMapping public ApiResponse<?> list(){return ApiResponse.ok(service.list());}
    @PostMapping public ApiResponse<?> create(@AuthenticationPrincipal Identity admin,@Valid @RequestBody RecurrenceContracts.Create input){return ApiResponse.ok(service.create(admin.userId(),input));}
    @PutMapping("/{id}/status") public ApiResponse<?> status(@PathVariable long id,@Valid @RequestBody RecurrenceContracts.Status input){return ApiResponse.ok(service.changeStatus(id,input.status()));}
}
