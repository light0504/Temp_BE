package vn.fbus.planning;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.ApiResponse;

@RestController @RequestMapping("/api/lecturer/booking-plans")
public class BookingPlanController {
    private final BookingPlanService service;
    public BookingPlanController(BookingPlanService service){this.service=service;}
    @GetMapping public ApiResponse<?> my(@AuthenticationPrincipal Identity user){return ApiResponse.ok(service.my(user.userId()));}
    @PostMapping("/preview") public ApiResponse<?> preview(@AuthenticationPrincipal Identity user,@Valid @RequestBody ScheduleContracts.PlanInput input){return ApiResponse.ok(service.preview(user.userId(),input));}
    @PostMapping public ApiResponse<?> create(@AuthenticationPrincipal Identity user,@Valid @RequestBody ScheduleContracts.PlanInput input){return ApiResponse.ok(service.create(user.userId(),input));}
    @PutMapping("/{id}/retry") public ApiResponse<?> retry(@AuthenticationPrincipal Identity user,@PathVariable long id){return ApiResponse.ok(service.retry(user.userId(),id));}
    @PutMapping("/{id}/cancel") public ApiResponse<?> cancel(@AuthenticationPrincipal Identity user,@PathVariable long id){return ApiResponse.ok(service.cancel(user.userId(),id));}
}
