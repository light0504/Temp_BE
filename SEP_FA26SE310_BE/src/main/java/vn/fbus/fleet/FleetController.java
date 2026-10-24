package vn.fbus.fleet;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.fbus.common.*;

@RestController @RequestMapping("/api/admin")
public class FleetController {
    private final FleetRepository repository;
    private final FleetService service;
    public FleetController(FleetRepository repository, FleetService service) { this.repository=repository; this.service=service; }
    @GetMapping("/vehicles") ApiResponse<?> vehicles() { return ApiResponse.ok(repository.all()); }
    @PostMapping("/vehicles") ApiResponse<?> create(@Valid @RequestBody Contracts.VehicleInput input) { return ApiResponse.ok(service.save(null,input)); }
    @PutMapping("/vehicles/{id}") ApiResponse<?> update(@PathVariable long id, @Valid @RequestBody Contracts.VehicleInput input) { return ApiResponse.ok(service.save(id,input)); }
    @GetMapping("/staff") ApiResponse<?> staff() { return ApiResponse.ok(repository.staff()); }
}
