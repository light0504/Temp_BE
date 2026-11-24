package vn.fbus.notification;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.ApiResponse;

@RestController
@RequestMapping("/api/student/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service){this.service=service;}
    @GetMapping public ApiResponse<?> list(@AuthenticationPrincipal Identity who,@RequestParam(required=false) Long semesterId){return ApiResponse.ok(service.list(who.userId(),semesterId));}
    @GetMapping("/unread-count") public ApiResponse<?> unread(@AuthenticationPrincipal Identity who){return ApiResponse.ok(service.unread(who.userId()));}
    @PutMapping("/read") public ApiResponse<?> read(@AuthenticationPrincipal Identity who,@Valid @RequestBody NotificationService.ReadBatch input){return ApiResponse.ok(service.markRead(who.userId(),input));}
}
