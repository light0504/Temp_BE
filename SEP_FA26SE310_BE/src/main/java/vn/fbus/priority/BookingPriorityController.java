package vn.fbus.priority;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.ApiResponse;
@RestController @RequestMapping("/api/admin/booking-priority")
public class BookingPriorityController {
 private final BookingPriorityService service;private final PriorityWorkbook workbook;
 public BookingPriorityController(BookingPriorityService service,PriorityWorkbook workbook){this.service=service;this.workbook=workbook;}
 @GetMapping ApiResponse<?> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.ok(service.list(page,size));}
 @PostMapping ApiResponse<?> save(@AuthenticationPrincipal Identity who,@Valid @RequestBody PriorityContracts.Entry input){return ApiResponse.ok(service.save(input,who.userId()));}
 @PutMapping("/remove") ApiResponse<?> remove(@Valid @RequestBody PriorityContracts.Remove input){return ApiResponse.ok(service.remove(input.email()));}
 @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) ApiResponse<?> upload(@AuthenticationPrincipal Identity who,@RequestParam("file") MultipartFile file){return ApiResponse.ok(service.upload(workbook.parse(file),who.userId()));}
 @GetMapping("/template") ResponseEntity<byte[]> template(){return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"fbus-priority-template.xlsx\"").cacheControl(CacheControl.noStore()).body(workbook.template());}
}

