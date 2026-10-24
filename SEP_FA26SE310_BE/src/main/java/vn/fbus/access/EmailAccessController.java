package vn.fbus.access;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.ApiResponse;

@RestController
@RequestMapping("/api/admin/email-access")
public class EmailAccessController {
    private final EmailWorkbook workbook;
    private final EmailAccessService service;
    public EmailAccessController(EmailWorkbook workbook, EmailAccessService service) { this.workbook=workbook; this.service=service; }
    @GetMapping
    public ApiResponse<?> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return ApiResponse.ok(service.list(page,size)); }
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"fbus-email-template.xlsx\"")
            .header(HttpHeaders.CACHE_CONTROL,"no-store").body(workbook.template());
    }
    @PostMapping(value="/import", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> upload(@RequestParam("file") MultipartFile file,@AuthenticationPrincipal Identity admin) {
        return ApiResponse.ok(service.save(workbook.parse(file),admin.userId()));
    }
}
