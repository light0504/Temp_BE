package vn.fbus.transport;

import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import vn.fbus.common.*;
import vn.fbus.auth.UserAdminService;
import java.util.*;

@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final TransportRepository repository;
    private final TransportService service;
    private final JdbcTemplate jdbc;
    private final UserAdminService users;
    public AdminController(TransportRepository repository, TransportService service, JdbcTemplate jdbc, UserAdminService users) { this.repository=repository; this.service=service; this.jdbc=jdbc; this.users=users; }
    @GetMapping("/stops") ApiResponse<?> stops() { return ApiResponse.ok(repository.stops()); }
    @PostMapping("/stops") ApiResponse<?> stop(@Valid @RequestBody Contracts.NewStop input) { return ApiResponse.ok(service.createStop(input)); }
    @GetMapping("/routes") ApiResponse<?> routes() { return ApiResponse.ok(repository.routes()); }
    @PostMapping("/routes") ApiResponse<?> route(@Valid @RequestBody Contracts.NewRoute input) { return ApiResponse.ok(service.createRoute(input)); }
    @GetMapping("/trips") ApiResponse<?> trips() { return ApiResponse.ok(service.adminTrips()); }
    @PostMapping("/trips") ApiResponse<?> trip(@Valid @RequestBody Contracts.NewTrip input) { return ApiResponse.ok(service.createTrip(input)); }
    @PostMapping("/trips/batch") ApiResponse<?> batch(@Valid @RequestBody Contracts.BatchTrips input) { return ApiResponse.ok(service.createBatch(input)); }
    @PostMapping("/users") ApiResponse<?> createUser(@Valid @RequestBody Contracts.NewUser input) { return ApiResponse.ok(users.create(input)); }
    @PutMapping("/trips/{id}/status") ApiResponse<?> status(@PathVariable long id, @Valid @RequestBody Contracts.TripStatus input) { return ApiResponse.ok(service.changeStatus(id,input.status())); }
    @GetMapping("/users") ApiResponse<?> users(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        ApiException.require(page>=0 && page<=100000 && size>=1 && size<=100,400,"Phân trang không hợp lệ.");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Long.class);
        var content=jdbc.query("SELECT * FROM app_user ORDER BY id LIMIT ? OFFSET ?",(rs,n) -> {
            var row=new LinkedHashMap<String,Object>();
            row.put("userId",rs.getLong("id")); row.put("username",rs.getString("username")); row.put("email",rs.getString("email"));
            row.put("fullName",rs.getString("full_name")); row.put("role",rs.getString("role")); row.put("status",rs.getString("status"));
            row.put("studentCode",null); row.put("phone",null); row.put("preferredStopId",rs.getObject("preferred_stop_id"));
            row.put("emailVerified",false); row.put("locked",false); return row;
        },size,page*size);
        return ApiResponse.ok(Map.of("content",content,"page",Map.of("size",size,"number",page,"totalElements",total,"totalPages",(total+size-1)/size)));
    }
}
