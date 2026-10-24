package vn.fbus.fleet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.common.*;
import java.util.Locale;
import static vn.fbus.common.ApiException.require;

@Service
public class FleetService {
    private final JdbcTemplate jdbc;
    private final FleetRepository repository;
    private final Database db;
    public FleetService(JdbcTemplate jdbc, FleetRepository repository, Database db) { this.jdbc=jdbc; this.repository=repository; this.db=db; }
    @Transactional
    public FleetRepository.Vehicle save(Long id, Contracts.VehicleInput input) {
        jdbc.queryForList("SELECT name FROM operation_lock WHERE name='trip-schedule' FOR UPDATE");
        repository.validateCrew(input.driverId(),input.busAttendantId());
        String plate=input.licensePlate().replaceAll("\\s+","").toUpperCase(Locale.ROOT);
        require(plate.matches("[A-Z0-9.-]{5,30}"),400,"Biển số không hợp lệ.");
        if (id==null) id=db.insert("INSERT INTO vehicle(license_plate,capacity,driver_id,bus_attendant_id,status) VALUES (?,?,?,?,?)",
            plate,input.capacity(),input.driverId(),input.busAttendantId(),input.status());
        else {
            repository.get(id);
            jdbc.update("UPDATE vehicle SET license_plate=?,capacity=?,driver_id=?,bus_attendant_id=?,status=? WHERE id=?",
                plate,input.capacity(),input.driverId(),input.busAttendantId(),input.status(),id);
        }
        return repository.get(id);
    }
}
