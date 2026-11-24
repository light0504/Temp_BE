package vn.fbus.history;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.ApiResponse;
@RestController
public class UsageHistoryController {
 private final UsageHistoryService service;
 public UsageHistoryController(UsageHistoryService service){this.service=service;}
 @GetMapping("/api/student/history")
 public ApiResponse<?> passenger(@AuthenticationPrincipal Identity who,@RequestParam long semesterId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(service.passenger(who.userId(),semesterId,page,size));}
 @GetMapping("/api/staff/history")
 public ApiResponse<?> driver(@AuthenticationPrincipal Identity who,@RequestParam long semesterId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(service.driver(who.userId(),semesterId,page,size));}
}