package vn.fbus.wishlist;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.ApiResponse;
import vn.fbus.planning.ScheduleContracts;

@RestController @RequestMapping("/api/student/wishlists")
public class WishlistController {
    private final WishlistService service;
    public WishlistController(WishlistService service){this.service=service;}
    @GetMapping public ApiResponse<?> my(@AuthenticationPrincipal Identity user){return ApiResponse.ok(service.my(user.userId()));}
    @PostMapping public ApiResponse<?> join(@AuthenticationPrincipal Identity user,@Valid @RequestBody ScheduleContracts.WishlistInput input){return ApiResponse.ok(service.join(user.userId(),input));}
    @PutMapping("/{id}/cancel") public ApiResponse<?> cancel(@AuthenticationPrincipal Identity user,@PathVariable long id){return ApiResponse.ok(service.cancel(user.userId(),id));}
}
