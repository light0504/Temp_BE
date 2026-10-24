package vn.fbus.booking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.fbus.common.ApiException;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class BookingRepository {
    public record BookingView(long bookingId, String bookingCode, long tripId, String tripCode,
        long boardingStopId, String boardingStop, long alightingStopId, String alightingStop, long seatId,
        String seatCode, String status, LocalDateTime reservedAt, LocalDateTime checkedInAt, LocalDateTime cancelledAt,
        LocalDateTime departureAt, long boardingTripStopId, long alightingTripStopId) {}
    public record BookingRow(long id, long userId, long tripId, long seatId, long boarding, long alighting, String status) {}
    private final JdbcTemplate jdbc;
    private static final String VIEW = """
        SELECT b.*,t.code AS trip_code,s.code AS seat_code,bs.stop_id AS boarding_stop_id,
        als.stop_id AS alighting_stop_id,bn.name AS boarding_stop,an.name AS alighting_stop,bs.arrival_at AS departure_at
        FROM booking b JOIN trip t ON t.id=b.trip_id JOIN seat s ON s.id=b.seat_id
        JOIN trip_stop bs ON bs.id=b.boarding_trip_stop_id JOIN trip_stop als ON als.id=b.alighting_trip_stop_id
        JOIN stop bn ON bn.id=bs.stop_id JOIN stop an ON an.id=als.stop_id
        """;
    private final RowMapper<BookingRow> rowMapper=(rs,n) -> new BookingRow(rs.getLong("id"),rs.getLong("user_id"),rs.getLong("trip_id"),
        rs.getLong("seat_id"),rs.getLong("boarding_trip_stop_id"),rs.getLong("alighting_trip_stop_id"),rs.getString("status"));
    private final RowMapper<BookingView> viewMapper=(rs,n) -> new BookingView(rs.getLong("id"),rs.getString("code"),rs.getLong("trip_id"),rs.getString("trip_code"),
        rs.getLong("boarding_stop_id"),rs.getString("boarding_stop"),rs.getLong("alighting_stop_id"),rs.getString("alighting_stop"),rs.getLong("seat_id"),rs.getString("seat_code"),
        rs.getString("status"),rs.getObject("reserved_at",LocalDateTime.class),rs.getObject("checked_in_at",LocalDateTime.class),rs.getObject("cancelled_at",LocalDateTime.class),
        rs.getObject("departure_at",LocalDateTime.class),rs.getLong("boarding_trip_stop_id"),rs.getLong("alighting_trip_stop_id"));
    public BookingRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<BookingRow> byKey(long user, String key) {
        return jdbc.query("SELECT * FROM booking WHERE user_id=? AND idempotency_key=?",rowMapper,user,key).stream().findFirst();
    }
    public BookingRow row(long id, long user) {
        return jdbc.query("SELECT * FROM booking WHERE id=? AND user_id=?",rowMapper,id,user).stream().findFirst()
            .orElseThrow(() -> new ApiException(404,"Không tìm thấy đặt chỗ."));
    }
    public BookingView view(long id) { return jdbc.query(VIEW+" WHERE b.id=?",viewMapper,id).getFirst(); }
    public List<BookingView> my(long user) { return jdbc.query(VIEW+" WHERE b.user_id=? ORDER BY b.reserved_at DESC,b.id DESC",viewMapper,user); }
}
