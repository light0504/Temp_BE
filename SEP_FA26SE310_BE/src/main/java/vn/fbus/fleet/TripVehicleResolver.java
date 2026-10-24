package vn.fbus.fleet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.fbus.common.*;
import java.util.Locale;
import static vn.fbus.common.ApiException.require;

@Component
public class TripVehicleResolver {
    public record Assignment(Long vehicleId, String licensePlate, int capacity, Long driverId, String driverName,
                             Long busAttendantId, String busAttendantName) {}
    private record Person(Long id, String name, String status) {}
    private final FleetRepository fleet;
    private final JdbcTemplate jdbc;
    public TripVehicleResolver(FleetRepository fleet, JdbcTemplate jdbc) { this.fleet=fleet; this.jdbc=jdbc; }

    public Assignment resolve(Contracts.TripVehicle input) {
        FleetRepository.Vehicle selected = input.vehicleId()==null ? null : fleet.get(input.vehicleId());
        String plate=input.vehicle()==null ? selected==null ? "" : selected.licensePlate() : normalizePlate(input.vehicle());
        require(plate.matches("[A-Z0-9.-]{5,30}"),400,"Biển số không hợp lệ.");
        require(selected==null || selected.licensePlate().equals(plate),400,"Biển số đã thay đổi. Bỏ chọn xe để nhập xe khác.");
        // Match registered plates even when entered manually, so inactive vehicles cannot bypass their status.
        if (selected==null) selected=fleet.all().stream().filter(vehicle -> vehicle.licensePlate().equals(plate)).findFirst().orElse(null);
        require(selected==null || selected.status().equals("ACTIVE"),400,"Xe đang ngừng sử dụng.");
        Integer capacity=input.capacity()!=null ? input.capacity() : selected==null ? null : selected.capacity();
        require(capacity!=null && capacity>=1 && capacity<=100,400,"Số ghế phải từ 1 đến 100.");
        Long driverId=input.driverId(); String driverName=input.driver();
        if (driverId==null && driverName==null && selected!=null) driverId=selected.driverId();
        var driver=person(driverId,driverName,"DRIVER",true);
        Long attendantId=input.busAttendantId(); String attendantName=input.busAttendant();
        if (attendantId==null && attendantName==null && selected!=null) attendantId=selected.busAttendantId();
        var attendant=person(attendantId,attendantName,"BUS_ATTENDANT",false);
        return new Assignment(selected==null ? null : selected.vehicleId(),plate,capacity,driver.id(),driver.name(),attendant.id(),attendant.name());
    }
    private Person person(Long id, String name, String role, boolean required) {
        String label=role.equals("DRIVER") ? "Tài xế" : "Bus attendant";
        if (id!=null) {
            var found=jdbc.query("SELECT id,full_name,status FROM app_user WHERE id=? AND role=?",
                (rs,n) -> new Person(rs.getLong("id"),rs.getString("full_name"),rs.getString("status")),id,role);
            require(found.size()==1 && found.getFirst().status().equals("ACTIVE"),400,label+" không hợp lệ.");
            return found.getFirst();
        }
        String trimmed=name==null ? "" : name.trim();
        require(!required || !trimmed.isEmpty(),400,"Vui lòng nhập tài xế.");
        if (trimmed.isEmpty()) return new Person(null,null,"ACTIVE");
        var matches=jdbc.query("SELECT id,full_name,status FROM app_user WHERE role=? AND LOWER(TRIM(full_name))=?",
            (rs,n) -> new Person(rs.getLong("id"),rs.getString("full_name"),rs.getString("status")),role,trimmed.toLowerCase(Locale.ROOT));
        require(matches.size()<=1,400,label+" trùng tên. Chọn tài khoản từ danh sách gợi ý.");
        if (!matches.isEmpty()) {
            require(matches.getFirst().status().equals("ACTIVE"),400,label+" không hoạt động.");
            return matches.getFirst();
        }
        return new Person(null,trimmed,"ACTIVE");
    }
    private String normalizePlate(String plate) { return plate.replaceAll("\\s+","").toUpperCase(Locale.ROOT); }
}
