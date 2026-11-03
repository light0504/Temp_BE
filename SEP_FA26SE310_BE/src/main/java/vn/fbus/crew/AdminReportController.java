package vn.fbus.crew;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.ApiResponse;
@RestController @RequestMapping("/api/admin/reports")
public class AdminReportController {
 private final CrewService service;
 public AdminReportController(CrewService service){this.service=service;}
 @GetMapping ApiResponse<?> list(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(service.adminReports(status,page,size));}
 @PutMapping("/{id}") ApiResponse<?> update(@AuthenticationPrincipal Identity who,@PathVariable long id,@Valid @RequestBody CrewContracts.Resolution input){return ApiResponse.ok(service.resolve(who.userId(),id,input));}
}

