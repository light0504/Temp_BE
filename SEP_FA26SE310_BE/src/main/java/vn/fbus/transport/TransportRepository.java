package vn.fbus.transport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.fbus.common.ApiException;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class TransportRepository {
    public record StopView(long stopId, String stopCode, String name, String address, Integer offsetMinutes) {}
    public record RouteView(long routeId, String routeCode, String name, List<StopView> stops) {}
    public record TripStopView(long tripStopId, long stopId, String name, int position, LocalDateTime arrivalAt) {}
    public record TripRow(long id, String code, long routeId, String routeName, LocalDateTime departureAt,
                          LocalDateTime arrivalAt, int slot, String vehicle, String driver, int capacity, String status,
                          Long vehicleId, Long driverId, Long busAttendantId, String busAttendant) {}
    public record SeatView(long seatId, String seatCode, String status) {}
    public record TripView(long tripId, String tripCode, long routeId, String routeName, String date, String time,
                           String arrivalTime, int slot, String vehicle, String driver, int capacity, int booked,
                           String status, int availableSeats, List<TripStopView> stops,
                           Long vehicleId, Long driverId, Long busAttendantId, String busAttendant) {}
    private final JdbcTemplate jdbc;
    private final RowMapper<TripRow> tripMapper = (rs,n) -> new TripRow(rs.getLong("id"),rs.getString("code"),rs.getLong("route_id"),
        rs.getString("route_name"),rs.getObject("departure_at",LocalDateTime.class),rs.getObject("arrival_at",LocalDateTime.class),
        rs.getInt("slot"),rs.getString("vehicle"),rs.getString("driver"),rs.getInt("capacity"),rs.getString("status"),
        rs.getObject("vehicle_id",Long.class),rs.getObject("driver_id",Long.class),rs.getObject("bus_attendant_id",Long.class),rs.getString("bus_attendant"));
    public TransportRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<StopView> stops() {
        return jdbc.query("SELECT * FROM stop ORDER BY id", (rs,n) -> new StopView(rs.getLong("id"),rs.getString("code"),rs.getString("name"),rs.getString("address"),null));
    }
    public List<StopView> routeStops(long routeId) {
        return jdbc.query("SELECT s.*,r.offset_minutes FROM route_stop r JOIN stop s ON s.id=r.stop_id WHERE r.route_id=? ORDER BY r.position",
            (rs,n) -> new StopView(rs.getLong("id"),rs.getString("code"),rs.getString("name"),rs.getString("address"),rs.getInt("offset_minutes")), routeId);
    }
    public List<RouteView> routes() {
        return jdbc.query("SELECT * FROM route ORDER BY id DESC", (rs,n) -> new RouteView(rs.getLong("id"),rs.getString("code"),rs.getString("name"),routeStops(rs.getLong("id"))));
    }
    public RouteView route(long id) {
        return jdbc.query("SELECT * FROM route WHERE id=?", (rs,n) -> new RouteView(rs.getLong("id"),rs.getString("code"),rs.getString("name"),routeStops(id)),id)
            .stream().findFirst().orElseThrow(() -> new ApiException(404,"Không tìm thấy tuyến xe."));
    }
    public TripRow trip(long id, boolean lock) {
        if (lock) jdbc.queryForList("SELECT id FROM trip WHERE id=? FOR UPDATE", id);
        return jdbc.query("SELECT t.*,r.name AS route_name FROM trip t JOIN route r ON r.id=t.route_id WHERE t.id=?", tripMapper,id)
            .stream().findFirst().orElseThrow(() -> new ApiException(404,"Không tìm thấy chuyến xe."));
    }
    public List<TripRow> trips() {
        return jdbc.query("SELECT t.*,r.name AS route_name FROM trip t JOIN route r ON r.id=t.route_id ORDER BY t.departure_at DESC", tripMapper);
    }
    public List<TripRow> assignedTrips(long userId, LocalDateTime from) {
        return jdbc.query("SELECT t.*,r.name AS route_name FROM trip t JOIN route r ON r.id=t.route_id WHERE (t.driver_id=? OR t.bus_attendant_id=?) AND t.arrival_at>=? ORDER BY t.departure_at",tripMapper,userId,userId,from);
    }
    public List<TripStopView> tripStops(long id) {
        return jdbc.query("SELECT t.*,s.name FROM trip_stop t JOIN stop s ON s.id=t.stop_id WHERE t.trip_id=? ORDER BY t.position",
            (rs,n) -> new TripStopView(rs.getLong("id"),rs.getLong("stop_id"),rs.getString("name"),rs.getInt("position"),rs.getObject("arrival_at",LocalDateTime.class)),id);
    }
    public List<SeatView> seats(long tripId, int start, int end) {
        return jdbc.query("""
            SELECT s.id,s.code,CASE WHEN EXISTS (
              SELECT 1 FROM booking b WHERE b.seat_id=s.id AND b.trip_id=s.trip_id
              AND b.status IN ('RESERVED','CHECKED_IN') AND b.boarding_position < ? AND b.alighting_position > ?
            ) THEN 'BOOKED' ELSE 'AVAILABLE' END AS availability
            FROM seat s WHERE s.trip_id=? ORDER BY s.id
            """, (rs,n) -> new SeatView(rs.getLong("id"),rs.getString("code"),rs.getString("availability")),end,start,tripId);
    }
    public TripView view(TripRow trip, int start, int end) {
        var points = tripStops(trip.id());
        int available = (int) seats(trip.id(),start,end).stream().filter(s -> s.status().equals("AVAILABLE")).count();
        int peak = 0;
        for (int i=0; i<points.size()-1; i++) {
            Integer occupied = jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE trip_id=? AND status IN ('RESERVED','CHECKED_IN') AND boarding_position<=? AND alighting_position>?",Integer.class,trip.id(),i,i);
            peak = Math.max(peak, occupied == null ? 0 : occupied);
        }
        return new TripView(trip.id(),trip.code(),trip.routeId(),trip.routeName(),trip.departureAt().toLocalDate().toString(),
            trip.departureAt().toLocalTime().toString(),trip.arrivalAt().toLocalTime().toString(),trip.slot(),trip.vehicle(),trip.driver(),trip.capacity(),peak,trip.status(),available,points,
            trip.vehicleId(),trip.driverId(),trip.busAttendantId(),trip.busAttendant());
    }
}
