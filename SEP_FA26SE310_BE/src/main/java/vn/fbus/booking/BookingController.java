package vn.fbus.booking;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.*;

@RestController @RequestMapping("/api/student/bookings")
public class BookingController {
    private final BookingRepository repository;
    private final BookingService service;
    public BookingController(BookingRepository repository, BookingService service) { this.repository=repository; this.service=service; }
    @GetMapping("/my") ApiResponse<?> my(@AuthenticationPrincipal Identity user) { return ApiResponse.ok(repository.my(user.userId())); }
    @PostMapping ApiResponse<?> create(@AuthenticationPrincipal Identity user, @Valid @RequestBody Contracts.NewBooking input) { return ApiResponse.ok(service.create(user.userId(),input)); }
    @PutMapping("/{id}/cancel") ApiResponse<?> cancel(@AuthenticationPrincipal Identity user, @PathVariable long id) { return ApiResponse.ok(service.cancel(user.userId(),id)); }
}
