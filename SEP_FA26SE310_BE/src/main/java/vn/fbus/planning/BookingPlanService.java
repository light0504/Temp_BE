package vn.fbus.planning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.booking.*;
import vn.fbus.wishlist.WishlistService;
import vn.fbus.common.*;
import vn.fbus.transport.TransportRepository.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static vn.fbus.common.ApiException.require;

@Service
public class BookingPlanService {
    private record Plan(long id,long user,long route,long boarding,long alighting,int slot,LocalDate from,LocalDate to,String weekdays,String key,String status,boolean wishlistMissingTrips) {}
    private record Choice(LocalDate date,String status,TravelSchedule.Candidate candidate) {}
    private final JdbcTemplate jdbc;private final Database db;private final TravelSchedule schedule;
    private final BookingService bookings;private final Clock clock;private final WishlistService wishlists;
    public BookingPlanService(JdbcTemplate jdbc,Database db,TravelSchedule schedule,BookingService bookings,Clock clock,WishlistService wishlists){this.jdbc=jdbc;this.db=db;this.schedule=schedule;this.bookings=bookings;this.clock=clock;this.wishlists=wishlists;}
    private void lecturer(long user){require("LECTURER".equals(jdbc.queryForObject("SELECT role FROM app_user WHERE id=?",String.class,user)),403,"Chỉ giảng viên được đặt lịch dài hạn.");}
    private String daysKey(List<Integer> days){return days.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));}
    private List<LocalDate> dates(ScheduleContracts.PlanInput input){
        require(!input.startDate().isBefore(LocalDate.now(clock)),400,"Ngày bắt đầu phải từ hôm nay.");
        require(!input.endDate().isBefore(input.startDate()) && ChronoUnit.DAYS.between(input.startDate(),input.endDate())<90,400,"Khoảng đặt lịch tối đa 90 ngày.");
        require(new HashSet<>(input.weekdays()).size()==input.weekdays().size(),400,"Thứ trong tuần bị trùng.");
        var dates=input.startDate().datesUntil(input.endDate().plusDays(1)).filter(d->input.weekdays().contains(d.getDayOfWeek().getValue())).toList();
        require(!dates.isEmpty(),400,"Không có ngày phù hợp với các thứ đã chọn.");return dates;
    }
    private Plan plan(long user,long id){
        return jdbc.query("SELECT * FROM booking_plan WHERE id=? AND user_id=?",(rs,n)->new Plan(rs.getLong("id"),rs.getLong("user_id"),rs.getLong("route_id"),rs.getLong("boarding_stop_id"),rs.getLong("alighting_stop_id"),rs.getInt("slot"),rs.getObject("start_date",LocalDate.class),rs.getObject("end_date",LocalDate.class),rs.getString("weekdays"),rs.getString("request_key"),rs.getString("status"),rs.getBoolean("wishlist_missing_trips")),id,user).stream().findFirst().orElseThrow(()->new ApiException(404,"Không tìm thấy lịch dài hạn."));
    }
    private Choice choose(long user,List<TripRow> trips,LocalDate date,long boarding,long alighting){
        if(date.isBefore(LocalDate.now(clock)))return new Choice(date,"PAST",null);
        var choices=schedule.candidates(trips,date,boarding,alighting);
        if(choices.stream().anyMatch(c->schedule.booked(user,c.trip().id())))return new Choice(date,"ALREADY_BOOKED",null);
        var available=choices.stream().filter(c->c.available()>0).findFirst();
        if(available.isPresent())return new Choice(date,"AVAILABLE",available.get());
        if(!choices.isEmpty())return new Choice(date,"FULL",null);
        return new Choice(date,trips.stream().anyMatch(t->t.departureAt().toLocalDate().equals(date))?"UNAVAILABLE":"NO_TRIP",null);
    }
    private Map<String,Object> previewDay(Choice choice){
        var row=new LinkedHashMap<String,Object>();row.put("date",choice.date());row.put("status",choice.status());
        var candidate=choice.candidate();row.put("tripId",candidate==null?null:candidate.trip().id());
        row.put("tripCode",candidate==null?null:candidate.trip().code());row.put("time",candidate==null?null:candidate.boarding().arrivalAt().toLocalTime().toString());
        row.put("availableSeats",candidate==null?0:candidate.available());return row;
    }
    public Map<String,Object> preview(long user,ScheduleContracts.PlanInput input){
        lecturer(user);var dates=dates(input);schedule.interval(input.routeId(),input.boardingStopId(),input.alightingStopId());
        var trips=schedule.trips(input.routeId(),input.slot(),input.startDate(),input.endDate(),false);
        var days=dates.stream().map(date->previewDay(choose(user,trips,date,input.boardingStopId(),input.alightingStopId()))).toList();
        return Map.of("days",days,"totalDates",days.size(),"availableCount",days.stream().filter(d->d.get("status").equals("AVAILABLE")).count());
    }
    @Transactional
    public Map<String,Object> create(long user,ScheduleContracts.PlanInput input){
        lecturer(user);require(input.idempotencyKey()!=null && !input.idempotencyKey().isBlank(),400,"Thiếu mã yêu cầu đặt lịch.");schedule.lockUser(user);
        var previous=jdbc.queryForList("SELECT id FROM booking_plan WHERE user_id=? AND request_key=?",Long.class,user,input.idempotencyKey());
        if(!previous.isEmpty()){
            var old=plan(user,previous.getFirst());
            require(old.route()==input.routeId() && old.boarding()==input.boardingStopId() && old.alighting()==input.alightingStopId() && old.slot()==input.slot() && old.from().equals(input.startDate()) && old.to().equals(input.endDate()) && old.weekdays().equals(daysKey(input.weekdays())) && old.wishlistMissingTrips()==input.wishlistMissingTrips(),409,"Mã yêu cầu đã dùng cho lịch khác.");return view(user,old.id());
        }
        var dates=dates(input);schedule.interval(input.routeId(),input.boardingStopId(),input.alightingStopId());
        var trips=schedule.trips(input.routeId(),input.slot(),input.startDate(),input.endDate(),true);
        long id=db.insert("INSERT INTO booking_plan(user_id,route_id,boarding_stop_id,alighting_stop_id,slot,start_date,end_date,weekdays,request_key,created_at,wishlist_missing_trips) VALUES (?,?,?,?,?,?,?,?,?,?,?)",user,input.routeId(),input.boardingStopId(),input.alightingStopId(),input.slot(),input.startDate(),input.endDate(),daysKey(input.weekdays()),input.idempotencyKey(),LocalDateTime.now(clock),input.wishlistMissingTrips());
        for(var date:dates){jdbc.update("INSERT INTO booking_plan_day(plan_id,travel_date,status) VALUES (?,?,'NO_TRIP')",id,date);var choice=choose(user,trips,date,input.boardingStopId(),input.alightingStopId());saveDay(user,id,choice);
            if(input.wishlistMissingTrips() && choice.status().equals("NO_TRIP")){
                var outcome=wishlists.joinForPlan(user,new ScheduleContracts.WishlistInput(input.routeId(),input.boardingStopId(),input.alightingStopId(),date,input.slot()));
                jdbc.update("UPDATE booking_plan_day SET wishlist_id=?,wishlist_created=?,wishlist_note=? WHERE plan_id=? AND travel_date=?",outcome.wishlistId(),outcome.created(),outcome.note().isEmpty()?null:outcome.note(),id,date);
            }
        }
        return view(user,id);
    }
    private void saveDay(long user,long plan,Choice choice){
        Long booking=null;String status=choice.status();
        if(choice.candidate()!=null){
            var c=choice.candidate();var seat=c.seats().stream().filter(s->s.status().equals("AVAILABLE")).findFirst().orElseThrow();
            var saved=bookings.create(user,new Contracts.NewBooking(c.trip().id(),c.boarding().tripStopId(),c.alighting().tripStopId(),seat.seatId(),"plan-"+plan+"-"+choice.date()));
            booking=saved.bookingId();status=saved.status();
        }
        jdbc.update("UPDATE booking_plan_day SET booking_id=?,status=? WHERE plan_id=? AND travel_date=?",booking,status,plan,choice.date());
    }
    @Transactional
    public Map<String,Object> retry(long user,long id){
        lecturer(user);schedule.lockUser(user);var plan=plan(user,id);require(plan.status().equals("ACTIVE"),409,"Lịch đã hủy.");
        var trips=schedule.trips(plan.route(),plan.slot(),plan.from(),plan.to(),true);
        var dates=jdbc.queryForList("SELECT travel_date FROM booking_plan_day WHERE plan_id=? AND booking_id IS NULL AND status<>'CANCELLED' AND travel_date>=? ORDER BY travel_date",LocalDate.class,id,LocalDate.now(clock));
        for(var date:dates)saveDay(user,id,choose(user,trips,date,plan.boarding(),plan.alighting()));return view(user,id);
    }
    @Transactional
    public Map<String,Object> cancel(long user,long id){
        lecturer(user);schedule.lockUser(user);var plan=plan(user,id);if(plan.status().equals("CANCELLED"))return view(user,id);
        var ids=jdbc.queryForList("SELECT b.trip_id FROM booking_plan_day d JOIN booking b ON b.id=d.booking_id WHERE d.plan_id=? ORDER BY b.trip_id",Long.class,id);
        for(var trip:ids)jdbc.queryForList("SELECT id FROM trip WHERE id=? FOR UPDATE",trip);
        var future=jdbc.queryForList("SELECT b.id FROM booking_plan_day d JOIN booking b ON b.id=d.booking_id JOIN trip_stop ts ON ts.id=b.boarding_trip_stop_id WHERE d.plan_id=? AND b.status='RESERVED' AND ts.arrival_at>? ORDER BY b.trip_id",Long.class,id,LocalDateTime.now(clock));
        for(var booking:future)bookings.cancel(user,booking);
        jdbc.update("UPDATE trip_wishlist SET status='CANCELLED' WHERE user_id=? AND status='ACTIVE' AND id IN (SELECT wishlist_id FROM booking_plan_day WHERE plan_id=? AND wishlist_created=TRUE AND travel_date>=?)",user,id,LocalDate.now(clock));
        jdbc.update("UPDATE booking_plan SET status='CANCELLED' WHERE id=?",id);
        jdbc.update("UPDATE booking_plan_day SET status='CANCELLED' WHERE plan_id=? AND booking_id IS NULL AND travel_date>=?",id,LocalDate.now(clock));return view(user,id);
    }
    public List<Map<String,Object>> my(long user){lecturer(user);return jdbc.queryForList("SELECT id FROM booking_plan WHERE user_id=? ORDER BY created_at DESC,id DESC",Long.class,user).stream().map(id->view(user,id)).toList();}
    public Map<String,Object> view(long user,long id){
        var plan=plan(user,id);var route=schedule.interval(plan.route(),plan.boarding(),plan.alighting());
        var row=new LinkedHashMap<String,Object>();row.put("planId",id);row.put("routeId",plan.route());row.put("routeName",route.name());
        row.put("boardingStop",route.stops().stream().filter(s->s.stopId()==plan.boarding()).findFirst().orElseThrow().name());
        row.put("alightingStop",route.stops().stream().filter(s->s.stopId()==plan.alighting()).findFirst().orElseThrow().name());
        row.put("boardingStopId",plan.boarding());row.put("alightingStopId",plan.alighting());row.put("wishlistMissingTrips",plan.wishlistMissingTrips());row.put("slot",plan.slot());row.put("startDate",plan.from());row.put("endDate",plan.to());row.put("weekdays",Arrays.stream(plan.weekdays().split(",")).map(Integer::valueOf).toList());row.put("status",plan.status());
        var days=jdbc.query("SELECT d.travel_date,d.status,d.wishlist_id,d.wishlist_note,w.status AS wishlist_status,b.id AS booking_id,b.status AS booking_status,t.id AS trip_id,t.code AS trip_code,s.code AS seat_code,ts.arrival_at FROM booking_plan_day d LEFT JOIN trip_wishlist w ON w.id=d.wishlist_id LEFT JOIN booking b ON b.id=d.booking_id LEFT JOIN trip t ON t.id=b.trip_id LEFT JOIN seat s ON s.id=b.seat_id LEFT JOIN trip_stop ts ON ts.id=b.boarding_trip_stop_id WHERE d.plan_id=? ORDER BY d.travel_date",(rs,n)->{
            var day=new LinkedHashMap<String,Object>();var date=rs.getObject("travel_date",LocalDate.class);day.put("date",date);String status=rs.getString("booking_status")==null?rs.getString("status"):rs.getString("booking_status");if(rs.getObject("booking_id")==null && !status.equals("CANCELLED") && date.isBefore(LocalDate.now(clock)))status="PAST";day.put("status",status);
            day.put("wishlistId",rs.getObject("wishlist_id",Long.class));day.put("wishlistStatus",rs.getString("wishlist_status"));day.put("wishlistNote",rs.getString("wishlist_note"));day.put("bookingId",rs.getObject("booking_id",Long.class));day.put("tripId",rs.getObject("trip_id",Long.class));day.put("tripCode",rs.getString("trip_code"));day.put("seatCode",rs.getString("seat_code"));
            var arrival=rs.getObject("arrival_at",LocalDateTime.class);day.put("time",arrival==null?null:arrival.toLocalTime().toString());return day;
        },id);
        row.put("days",days);return row;
    }
}
