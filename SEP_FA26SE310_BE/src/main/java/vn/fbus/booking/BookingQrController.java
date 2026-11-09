package vn.fbus.booking;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.ApiResponse;

@RestController
public class BookingQrController {
 private final BookingQrService qr;
 public BookingQrController(BookingQrService qr){this.qr=qr;}
 @PostMapping("/api/student/bookings/{id}/check-in-qr")
 ResponseEntity<?> issue(@AuthenticationPrincipal Identity who,@PathVariable long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(qr.issue(who,id)));}
 @PostMapping("/api/staff/trips/{id}/check-in-qr/preview")
 ResponseEntity<?> preview(@AuthenticationPrincipal Identity who,@PathVariable long id,@Valid @RequestBody BookingQrService.Scan input){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(qr.preview(who.userId(),id,input.qrCode())));}
 @PostMapping("/api/staff/trips/{id}/check-in-qr")
 ResponseEntity<?> checkIn(@AuthenticationPrincipal Identity who,@PathVariable long id,@Valid @RequestBody BookingQrService.Scan input){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(qr.checkIn(who.userId(),id,input.qrCode())));}
}

