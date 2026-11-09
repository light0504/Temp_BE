package vn.fbus.planning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.fbus.transport.TransportRepository;
import vn.fbus.transport.TransportRepository.*;
import java.time.*;
import java.util.*;
import static vn.fbus.common.ApiException.require;

@Component
public class TravelSchedule {
    public record Candidate(TripRow trip,TripStopView boarding,TripStopView alighting,List<SeatView> seats) {
        public int available() { return (int) seats.stream().filter(s -> s.status().equals("AVAILABLE")).count(); }
    }
    private final JdbcTemplate jdbc;
    private final TransportRepository trips;
    private final Clock clock;
    public TravelSchedule(JdbcTemplate jdbc,TransportRepository trips,Clock clock) { this.jdbc=jdbc;this.trips=trips;this.clock=clock; }
    public void lockUser(long user) { jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",user); }
    public RouteView interval(long route,long boarding,long alighting) {
        var found=trips.route(route); int start=-1,end=-1;
        for (int i=0;i<found.stops().size();i++) { var stop=found.stops().get(i);if(stop.stopId()==boarding)start=i;if(stop.stopId()==alighting)end=i; }
        require(start>=0 && end>start,400,"Chọn điểm đón/trả đúng chiều của tuyến."); return found;
    }
    public List<TripRow> trips(long route,int slot,LocalDate from,LocalDate to,boolean lock) {
        if(lock) jdbc.queryForList("SELECT id FROM trip WHERE route_id=? AND slot=? AND departure_at>=? AND departure_at<? ORDER BY id FOR UPDATE",route,slot,from.atStartOfDay(),to.plusDays(1).atStartOfDay());
        return jdbc.queryForList("SELECT id FROM trip WHERE route_id=? AND slot=? AND departure_at>=? AND departure_at<? ORDER BY departure_at,id",Long.class,route,slot,from.atStartOfDay(),to.plusDays(1).atStartOfDay()).stream().map(id -> trips.trip(id,false)).toList();
    }
    public List<Candidate> candidates(List<TripRow> rows,LocalDate date,long boarding,long alighting) {
        var result=new ArrayList<Candidate>();
        for(var trip:rows) {
            if(!trip.departureAt().toLocalDate().equals(date) || !trip.status().equals("SCHEDULED") || trip.executionStatus().equals("COMPLETED"))continue;
            var points=trips.tripStops(trip.id());
            var start=points.stream().filter(p -> p.stopId()==boarding).findFirst().orElse(null);
            var end=points.stream().filter(p -> p.stopId()==alighting).findFirst().orElse(null);
            if(start!=null && end!=null && start.position()<end.position() && start.position()>=trip.firstBoardingPosition() && start.arrivalAt().isAfter(LocalDateTime.now(clock)))
                result.add(new Candidate(trip,start,end,trips.seats(trip.id(),start.position(),end.position())));
        }
        return result;
    }
    public boolean booked(long user,long trip) { return jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE user_id=? AND trip_id=? AND status IN ('RESERVED','CHECKED_IN')",Integer.class,user,trip)>0; }
}
