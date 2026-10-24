package vn.fbus.fleet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.fbus.common.ApiException;
import java.util.List;

@Repository
public class FleetRepository {
    public record Staff(long userId, String fullName, String email, String role) {}
    public record Vehicle(long vehicleId, String licensePlate, int capacity, long driverId, String driverName,
                          Long busAttendantId, String busAttendantName, String status) {}
    private final JdbcTemplate jdbc;
    private static final String SELECT = """
        SELECT v.*,d.full_name AS driver_name,a.full_name AS attendant_name
        FROM vehicle v JOIN app_user d ON d.id=v.driver_id LEFT JOIN app_user a ON a.id=v.bus_attendant_id
        """;
    private final RowMapper<Vehicle> mapper = (rs,n) -> new Vehicle(rs.getLong("id"),rs.getString("license_plate"),rs.getInt("capacity"),
        rs.getLong("driver_id"),rs.getString("driver_name"),rs.getObject("bus_attendant_id",Long.class),rs.getString("attendant_name"),rs.getString("status"));
    public FleetRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Vehicle> all() { return jdbc.query(SELECT+" ORDER BY v.license_plate",mapper); }
    public Vehicle get(long id) {
        return jdbc.query(SELECT+" WHERE v.id=?",mapper,id).stream().findFirst().orElseThrow(() -> new ApiException(404,"Không tìm thấy xe."));
    }
    public List<Staff> staff() {
        return jdbc.query("SELECT id,full_name,email,role FROM app_user WHERE role IN ('DRIVER','BUS_ATTENDANT') AND status='ACTIVE' ORDER BY full_name,id",
            (rs,n) -> new Staff(rs.getLong("id"),rs.getString("full_name"),rs.getString("email"),rs.getString("role")));
    }
    public void validateCrew(long driver, Long attendant) {
        ApiException.require(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND role='DRIVER' AND status='ACTIVE'",Integer.class,driver)==1,400,"Tài xế không hợp lệ.");
        if (attendant!=null) ApiException.require(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND role='BUS_ATTENDANT' AND status='ACTIVE'",Integer.class,attendant)==1,400,"Bus attendant không hợp lệ.");
    }
}
