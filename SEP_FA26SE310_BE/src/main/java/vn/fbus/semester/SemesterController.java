package vn.fbus.semester;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.fbus.common.ApiResponse;
@RestController
public class SemesterController {
 private final SemesterService service;
 public SemesterController(SemesterService service){this.service=service;}
 @GetMapping({"/api/semesters","/api/admin/semesters"}) public ApiResponse<?> list(){return ApiResponse.ok(service.list());}
 @PostMapping("/api/admin/semesters") public ApiResponse<?> create(@Valid @RequestBody SemesterService.Input input){return ApiResponse.ok(service.save(null,input));}
 @PutMapping("/api/admin/semesters/{id}") public ApiResponse<?> update(@PathVariable long id,@Valid @RequestBody SemesterService.Input input){return ApiResponse.ok(service.save(id,input));}
}