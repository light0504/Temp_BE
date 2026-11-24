package vn.fbus.wishlist;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.common.Database;
import vn.fbus.priority.BookingOpeningService;
import vn.fbus.transport.TransportRepository;
import java.time.*;
import java.util.*;

@Service
public class WishlistAllocationService {
    private record Waiting(long id,long user,long boarding,long alighting,String role) {}
    private final JdbcTemplate jdbc;
    private final Database db;
    private final TransportRepository trips;
    private final BookingOpeningService opening;
    private final Clock clock;

    public WishlistAllocationService(JdbcTemplate jdbc,Database db,TransportRepository trips,BookingOpeningService opening,Clock clock) {
        this.jdbc=jdbc;this.db=db;this.trips=trips;this.opening=opening;this.clock=clock;
    }

    // Allocate after trip creation commits, locking users by ID before the trip as in manual booking.
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public int allocate(long tripId) {
        var initial=trips.trip(tripId,false);
        if(!initial.status().equals("SCHEDULED") || !initial.executionStatus().equals("READY")
            || !initial.departureAt().isAfter(LocalDateTime.now(clock)))return 0;
        var date=initial.departureAt().toLocalDate();
        var users=jdbc.queryForList("""
            SELECT DISTINCT user_id FROM trip_wishlist WHERE route_id=? AND travel_date=? AND slot=?
              AND semester_id=? AND status='ACTIVE' ORDER BY user_id
            """,Long.class,initial.routeId(),date,initial.slot(),initial.semesterId());
        if(users.isEmpty())return 0;
        for(long user:users)jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",user);
        var lockedUsers=new HashSet<>(users);
        var trip=trips.trip(tripId,true);
        if(!trip.status().equals("SCHEDULED") || !trip.executionStatus().equals("READY")
            || !trip.departureAt().isAfter(LocalDateTime.now(clock)))return 0;
        var waiting=jdbc.query("""
            SELECT w.id,w.user_id,w.boarding_stop_id,w.alighting_stop_id,u.role
            FROM trip_wishlist w JOIN app_user u ON u.id=w.user_id
            WHERE w.route_id=? AND w.travel_date=? AND w.slot=? AND w.semester_id=? AND w.status='ACTIVE'
              AND u.status='ACTIVE' AND u.role IN ('STUDENT','LECTURER')
            ORDER BY w.created_at,w.id
            """,(r,n)->new Waiting(r.getLong("id"),r.getLong("user_id"),r.getLong("boarding_stop_id"),r.getLong("alighting_stop_id"),r.getString("role")),
            trip.routeId(),date,trip.slot(),trip.semesterId());
        var stops=trips.tripStops(tripId);
        int assigned=0;
        for(var request:waiting) {
            if(!lockedUsers.contains(request.user()))continue;
            boolean lecturerPlan=request.role().equals("LECTURER") && jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking_plan_day d JOIN booking_plan p ON p.id=d.plan_id
                WHERE d.wishlist_id=? AND p.user_id=? AND p.status='ACTIVE'
                """,Integer.class,request.id(),request.user())>0;
            if(!lecturerPlan && !opening.access(request.user(),trip).allowed())continue;
            var start=stops.stream().filter(s->s.stopId()==request.boarding()).findFirst().orElse(null);
            var end=stops.stream().filter(s->s.stopId()==request.alighting()).findFirst().orElse(null);
            if(start==null || end==null || start.position()>=end.position()
                || !start.arrivalAt().isAfter(LocalDateTime.now(clock)))continue;
            int existing=jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking b JOIN trip t ON t.id=b.trip_id
                WHERE b.user_id=? AND t.route_id=? AND t.slot=? AND t.departure_at>=? AND t.departure_at<?
                  AND b.status IN ('RESERVED','CHECKED_IN') AND t.status<>'CANCELLED'
                """,Integer.class,request.user(),trip.routeId(),trip.slot(),date.atStartOfDay(),date.plusDays(1).atStartOfDay());
            if(existing>0)continue;
            var seat=trips.seats(tripId,start.position(),end.position()).stream()
                .filter(s->s.status().equals("AVAILABLE")).findFirst().orElse(null);
            if(seat==null)continue;
            var now=LocalDateTime.now(clock);
            long booking=db.insert("""
                INSERT INTO booking(code,user_id,trip_id,boarding_trip_stop_id,alighting_trip_stop_id,
                    boarding_position,alighting_position,seat_id,idempotency_key,reserved_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID().toString(),request.user(),tripId,start.tripStopId(),end.tripStopId(),
                start.position(),end.position(),seat.seatId(),"wishlist-"+request.id()+"-"+UUID.randomUUID(),now);
            jdbc.update("UPDATE trip_wishlist SET status='FULFILLED' WHERE id=? AND status='ACTIVE'",request.id());
            jdbc.update("""
                UPDATE booking_plan_day SET status='RESERVED',booking_id=?
                WHERE wishlist_id=? AND plan_id IN (SELECT id FROM booking_plan WHERE user_id=? AND status='ACTIVE')
                """,booking,request.id(),request.user());
            String message="Wishlist đã được xếp chỗ: "+trip.routeName()+", "+date+", slot "+trip.slot()
                +", ghế "+seat.seatCode()+". "+start.name()+" → "+end.name()+".";
            if(jdbc.update("UPDATE user_notification SET message=?,created_at=?,read_at=NULL WHERE user_id=? AND trip_id=?",message,now,request.user(),tripId)==0)
                jdbc.update("INSERT INTO user_notification(user_id,trip_id,message,created_at) VALUES (?,?,?,?)",request.user(),tripId,message,now);
            assigned++;
        }
        return assigned;
    }

    public List<Long> pendingTrips() {
        return jdbc.queryForList("""
            SELECT t.id FROM trip t WHERE t.status='SCHEDULED' AND t.execution_status='READY' AND t.departure_at>?
              AND EXISTS (SELECT 1 FROM trip_wishlist w WHERE w.route_id=t.route_id AND w.slot=t.slot
                AND w.semester_id=t.semester_id AND w.travel_date=CAST(t.departure_at AS DATE) AND w.status='ACTIVE')
            ORDER BY t.departure_at,t.id
            """,Long.class,LocalDateTime.now(clock));
    }
}
