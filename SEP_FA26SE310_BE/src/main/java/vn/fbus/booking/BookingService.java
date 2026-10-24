package vn.fbus.booking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.common.*;
import vn.fbus.transport.*;
import java.time.*;
import java.util.UUID;
import static vn.fbus.common.ApiException.require;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final TransportRepository trips;
    private final TransportService transport;
    private final JdbcTemplate jdbc;
    private final Database db;
    private final Clock clock;
    public BookingService(BookingRepository bookings, TransportRepository trips, TransportService transport, JdbcTemplate jdbc, Database db, Clock clock) {
        this.bookings=bookings; this.trips=trips; this.transport=transport; this.jdbc=jdbc; this.db=db; this.clock=clock;
    }
    @Transactional
    public BookingRepository.BookingView create(long user, Contracts.NewBooking input) {
        // Lock order is always user then trip. A trip lock serializes all seat/interval changes across API instances.
        jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",user);
        var previous=bookings.byKey(user,input.idempotencyKey());
        if (previous.isPresent()) {
            var old=previous.get();
            require(old.tripId()==input.tripId() && old.seatId()==input.seatId() && old.boarding()==input.boardingTripStopId()
                && old.alighting()==input.alightingTripStopId(),409,"Mã yêu cầu đã dùng cho đặt chỗ khác.");
            return bookings.view(old.id());
        }
        var trip=trips.trip(input.tripId(),true);
        var interval=transport.interval(trip.id(),input.boardingTripStopId(),input.alightingTripStopId());
        var start=interval.getFirst(); var end=interval.getLast();
        require(trip.status().equals("SCHEDULED") && start.arrivalAt().isAfter(LocalDateTime.now(clock)),409,"Chuyến không còn nhận đặt chỗ.");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE user_id=? AND trip_id=? AND status IN ('RESERVED','CHECKED_IN')",Integer.class,user,trip.id())==0,409,"Bạn đã có đặt chỗ trên chuyến này.");
        var seat=trips.seats(trip.id(),start.position(),end.position()).stream().filter(s -> s.seatId()==input.seatId()).findFirst()
            .orElseThrow(() -> new ApiException(400,"Ghế không thuộc chuyến xe."));
        require(seat.status().equals("AVAILABLE"),409,"Ghế vừa được đặt. Vui lòng chọn ghế khác.");
        long id=db.insert("INSERT INTO booking(code,user_id,trip_id,boarding_trip_stop_id,alighting_trip_stop_id,boarding_position,alighting_position,seat_id,idempotency_key,reserved_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),user,trip.id(),start.tripStopId(),end.tripStopId(),start.position(),end.position(),seat.seatId(),input.idempotencyKey(),LocalDateTime.now(clock));
        return bookings.view(id);
    }
    @Transactional
    public BookingRepository.BookingView cancel(long user, long id) {
        jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",user);
        var booking=bookings.row(id,user); trips.trip(booking.tripId(),true);
        if (booking.status().equals("CANCELLED")) return bookings.view(id);
        require(booking.status().equals("RESERVED"),409,"Không thể hủy đặt chỗ đã check-in.");
        require(bookings.view(id).departureAt().isAfter(LocalDateTime.now(clock)),409,"Đã qua giờ đón, không thể hủy đặt chỗ.");
        jdbc.update("UPDATE booking SET status='CANCELLED',cancelled_at=? WHERE id=?",LocalDateTime.now(clock),id);
        return bookings.view(id);
    }
}
