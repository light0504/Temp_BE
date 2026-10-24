package vn.fbus.transport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.common.*;
import vn.fbus.transport.TransportRepository.*;
import vn.fbus.fleet.TripVehicleResolver;
import java.time.*;
import java.util.*;
import static vn.fbus.common.ApiException.require;

@Service
public class TransportService {
    private final TransportRepository repository;
    private final JdbcTemplate jdbc;
    private final Database db;
    private final Clock clock;
    private final TripVehicleResolver vehicles;
    public TransportService(TransportRepository repository, JdbcTemplate jdbc, Database db, Clock clock, TripVehicleResolver vehicles) {
        this.repository=repository; this.jdbc=jdbc; this.db=db; this.clock=clock; this.vehicles=vehicles;
    }
    @Transactional
    public StopView createStop(Contracts.NewStop input) {
        long id = db.insert("INSERT INTO stop(code,name,address) VALUES (?,?,?)",input.stopCode().trim().toUpperCase(Locale.ROOT),input.name().trim(),input.address().trim());
        return new StopView(id,input.stopCode().trim().toUpperCase(Locale.ROOT),input.name().trim(),input.address().trim(),null);
    }
    @Transactional
    public RouteView createRoute(Contracts.NewRoute input) {
        var seen = new HashSet<Long>(); int previous = -1;
        for (var point : input.stops()) {
            require(seen.add(point.stopId()),400,"Mỗi điểm dừng chỉ xuất hiện một lần trong tuyến.");
            require(point.offsetMinutes() > previous,400,"Thời gian đến phải tăng theo thứ tự điểm dừng.");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM stop WHERE id=?",Integer.class,point.stopId()) == 1,400,"Điểm dừng không tồn tại.");
            previous = point.offsetMinutes();
        }
        require(input.stops().getFirst().offsetMinutes()==0,400,"Điểm đầu phải có thời gian 0 phút.");
        long id = db.insert("INSERT INTO route(code,name) VALUES (?,?)",input.routeCode().trim().toUpperCase(Locale.ROOT),input.name().trim());
        for (int i=0; i<input.stops().size(); i++) {
            var point = input.stops().get(i);
            jdbc.update("INSERT INTO route_stop(route_id,stop_id,position,offset_minutes) VALUES (?,?,?,?)",id,point.stopId(),i,point.offsetMinutes());
        }
        return repository.route(id);
    }
    @Transactional
    public TripView createTrip(Contracts.NewTrip input) {
        jdbc.queryForList("SELECT name FROM operation_lock WHERE name='trip-schedule' FOR UPDATE");
        return insertTrip(repository.route(input.routeId()),vehicles.resolve(input),input.date(),input.time(),input.slot());
    }
    @Transactional
    public List<TripView> createBatch(Contracts.BatchTrips input) {
        require(new HashSet<>(input.dates()).size()==input.dates().size(),400,"Danh sách ngày bị trùng.");
        jdbc.queryForList("SELECT name FROM operation_lock WHERE name='trip-schedule' FOR UPDATE");
        var route=repository.route(input.routeId()); var vehicle=vehicles.resolve(input);
        var created=new ArrayList<TripView>();
        for (var date : input.dates().stream().sorted().toList()) created.add(insertTrip(route,vehicle,date,input.time(),input.slot()));
        return created;
    }
    private TripView insertTrip(RouteView route, TripVehicleResolver.Assignment vehicle, LocalDate date, LocalTime time, int slot) {
        var departure = date.atTime(time.withSecond(0).withNano(0));
        require(departure.isAfter(LocalDateTime.now(clock)),400,"Giờ khởi hành phải ở tương lai: "+date+".");
        var arrival = departure.plusMinutes(route.stops().getLast().offsetMinutes());
        var params=new ArrayList<Object>(); params.add(vehicle.licensePlate());
        String sql="SELECT COUNT(*) FROM trip WHERE (UPPER(REPLACE(vehicle,' ',''))=?";
        if (vehicle.vehicleId()!=null) { sql+=" OR vehicle_id=?"; params.add(vehicle.vehicleId()); }
        if (vehicle.driverId()!=null) { sql+=" OR driver_id=? OR (driver_id IS NULL AND LOWER(TRIM(driver))=LOWER(?))"; params.add(vehicle.driverId()); }
        else sql+=" OR LOWER(TRIM(driver))=LOWER(?)";
        params.add(vehicle.driverName());
        if (vehicle.busAttendantName()!=null) {
            if (vehicle.busAttendantId()!=null) { sql+=" OR bus_attendant_id=? OR (bus_attendant_id IS NULL AND LOWER(TRIM(bus_attendant))=LOWER(?))"; params.add(vehicle.busAttendantId()); }
            else sql+=" OR LOWER(TRIM(bus_attendant))=LOWER(?)";
            params.add(vehicle.busAttendantName());
        }
        sql+=") AND departure_at<? AND arrival_at>?"; params.add(arrival); params.add(departure);
        int conflicts=jdbc.queryForObject(sql,Integer.class,params.toArray());
        require(conflicts==0,409,"Trùng lịch xe, tài xế hoặc bus attendant ngày "+date+". Chưa tạo chuyến nào trong lần lưu này.");
        String code = route.routeCode()+"-"+UUID.randomUUID().toString().substring(0,8);
        long id = db.insert("INSERT INTO trip(code,route_id,departure_at,arrival_at,slot,vehicle,driver,capacity,vehicle_id,driver_id,bus_attendant_id,bus_attendant) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            code,route.routeId(),departure,arrival,slot,vehicle.licensePlate(),vehicle.driverName(),vehicle.capacity(),vehicle.vehicleId(),vehicle.driverId(),vehicle.busAttendantId(),vehicle.busAttendantName());
        for (int i=0; i<route.stops().size(); i++) {
            var point=route.stops().get(i);
            jdbc.update("INSERT INTO trip_stop(trip_id,stop_id,position,arrival_at) VALUES (?,?,?,?)",id,point.stopId(),i,departure.plusMinutes(point.offsetMinutes()));
        }
        for (String seatCode : BusSeatLayout.codes(vehicle.capacity())) jdbc.update("INSERT INTO seat(trip_id,code) VALUES (?,?)",id,seatCode);
        return repository.view(repository.trip(id,false),0,route.stops().size()-1);
    }
    @Transactional
    public TripView changeStatus(long id, String status) {
        repository.trip(id,true);
        jdbc.update("UPDATE trip SET status=? WHERE id=?",status,id);
        return repository.view(repository.trip(id,false),0,repository.tripStops(id).size()-1);
    }
    public List<TripView> adminTrips() {
        return repository.trips().stream().map(t -> repository.view(t,0,repository.tripStops(t.id()).size()-1)).toList();
    }
    public List<TripView> search(LocalDate date, Long routeId, Long boardingStopId, Long alightingStopId, Integer slot) {
        require(!date.isBefore(LocalDate.now(clock)),400,"Ngày khởi hành phải từ hôm nay trở đi.");
        require(slot==null || (slot>=1 && slot<=6),400,"Slot phải từ 1 đến 6.");
        var found = new ArrayList<TripView>();
        for (var trip : repository.trips()) {
            if (!trip.departureAt().toLocalDate().equals(date) || !trip.status().equals("SCHEDULED") || (routeId!=null && trip.routeId()!=routeId) || (slot!=null && trip.slot()!=slot)) continue;
            var points = repository.tripStops(trip.id());
            var start = boardingStopId==null ? points.getFirst() : points.stream().filter(p -> p.stopId()==boardingStopId).findFirst().orElse(null);
            var end = alightingStopId==null ? points.getLast() : points.stream().filter(p -> p.stopId()==alightingStopId).findFirst().orElse(null);
            if (start==null || end==null || start.position()>=end.position() || !start.arrivalAt().isAfter(LocalDateTime.now(clock))) continue;
            found.add(repository.view(trip,start.position(),end.position()));
        }
        found.sort(Comparator.comparing(TripView::time)); return found;
    }
    public List<TripStopView> interval(long tripId, long boarding, long alighting) {
        var points=repository.tripStops(tripId);
        var start=points.stream().filter(p -> p.tripStopId()==boarding).findFirst().orElseThrow(() -> new ApiException(400,"Điểm đón không thuộc chuyến."));
        var end=points.stream().filter(p -> p.tripStopId()==alighting).findFirst().orElseThrow(() -> new ApiException(400,"Điểm trả không thuộc chuyến."));
        require(start.position()<end.position(),400,"Điểm trả phải sau điểm đón.");
        return List.of(start,end);
    }
    public List<SeatView> availableSeats(long tripId, long boarding, long alighting) {
        var trip=repository.trip(tripId,false); var points=interval(tripId,boarding,alighting);
        require(trip.status().equals("SCHEDULED") && points.getFirst().arrivalAt().isAfter(LocalDateTime.now(clock)),409,"Chuyến không còn nhận đặt chỗ.");
        return repository.seats(tripId,points.getFirst().position(),points.getLast().position());
    }
}
