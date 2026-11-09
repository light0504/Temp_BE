package vn.fbus.recurrence;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.fbus.common.*;
import vn.fbus.fleet.TripVehicleResolver;
import vn.fbus.priority.BookingOpeningService;
import vn.fbus.transport.*;
import vn.fbus.transport.TransportRepository.TripView;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import static vn.fbus.common.ApiException.require;
@Service
public class TripRecurrenceService {
    private record Rule(long id,RecurrenceContracts.Create input,int generated,LocalDate through,String status,String error,String routeName) {}
    private final JdbcTemplate jdbc;private final Database db;private final TripVehicleResolver vehicles;private final TransportService transport;private final TransportRepository trips;private final Clock clock;private final ObjectMapper json;private final TransactionTemplate tx;
    public TripRecurrenceService(JdbcTemplate jdbc,Database db,TripVehicleResolver vehicles,TransportService transport,TransportRepository trips,Clock clock,ObjectMapper json,PlatformTransactionManager manager){this.jdbc=jdbc;this.db=db;this.vehicles=vehicles;this.transport=transport;this.trips=trips;this.clock=clock;this.json=json;this.tx=new TransactionTemplate(manager);}
    private void lock(){jdbc.queryForList("SELECT name FROM operation_lock WHERE name='trip-schedule' FOR UPDATE");}
    private List<Integer> parseDays(String value){return Arrays.stream(value.split(",")).map(Integer::valueOf).toList();}
    private String days(List<Integer> value){return value.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));}
    private Rule rule(long id){
        return jdbc.query("SELECT a.*,r.name AS route_name FROM trip_recurrence a JOIN route r ON r.id=a.route_id WHERE a.id=?",(rs,n)->new Rule(rs.getLong("id"),
            new RecurrenceContracts.Create(rs.getLong("route_id"),rs.getObject("vehicle_id",Long.class),rs.getObject("start_date",LocalDate.class),rs.getObject("departure_time",LocalTime.class),rs.getInt("slot"),rs.getString("vehicle"),rs.getInt("capacity"),rs.getObject("driver_id",Long.class),rs.getString("driver"),rs.getObject("bus_attendant_id",Long.class),rs.getString("bus_attendant"),rs.getInt("interval_weeks"),parseDays(rs.getString("weekdays")),rs.getString("end_type"),rs.getObject("until_date",LocalDate.class),rs.getObject("occurrence_limit",Integer.class),rs.getString("request_key"),TransportRepository.bookingOpening(rs),rs.getObject("week_limit",Integer.class),TransportRepository.bookingOpeningLead(rs)),
            rs.getInt("generated_count"),rs.getObject("processed_through",LocalDate.class),rs.getString("status"),rs.getString("last_error"),rs.getString("route_name")),id).stream().findFirst().orElseThrow(()->new ApiException(404,"Không tìm thấy lịch lặp."));
    }
    private boolean matches(RecurrenceContracts.Create input,LocalDate date){
        var anchor=input.startDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));var week=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return !date.isBefore(input.startDate()) && input.weekdays().contains(date.getDayOfWeek().getValue()) && ChronoUnit.WEEKS.between(anchor,week)%input.intervalWeeks()==0;
    }
    private LocalDate endDate(RecurrenceContracts.Create input) {
        return switch (input.endType()) {
            case "UNTIL" -> input.untilDate();
            case "WEEKS" -> input.startDate().plusWeeks(input.weeks()).minusDays(1);
            default -> null;
        };
    }
    private void validate(RecurrenceContracts.Create input){
        require(!input.startDate().isBefore(LocalDate.now(clock)) && input.startDate().getYear()<=9998,400,"Ngày bắt đầu phải từ hôm nay và trước năm 9999.");
        require(new HashSet<>(input.weekdays()).size()==input.weekdays().size(),400,"Thứ lặp lại bị trùng.");
        if(input.endType().equals("UNTIL"))require(input.untilDate()!=null && !input.untilDate().isBefore(input.startDate()) && input.untilDate().getYear()<=9998,400,"Chọn ngày kết thúc từ ngày bắt đầu.");
        if(input.endType().equals("COUNT"))require(input.occurrences()!=null && input.occurrences()>=1 && input.occurrences()<=1000,400,"Số lần xuất hiện phải từ 1 đến 1.000.");
        if (input.endType().equals("WEEKS")) {
            require(input.weeks()!=null,400,"Nhập số tuần lặp.");
            require(endDate(input).getYear()<=9998,400,"Ngày kết thúc phải trước năm 9999.");
        }
        var end = endDate(input);
        var limit = input.startDate().plusDays(input.intervalWeeks()*7L+7);
        if (end != null && end.isBefore(limit)) limit = end;
        boolean any=false;for(var date=input.startDate();!date.isAfter(limit);date=date.plusDays(1))if(matches(input,date) && date.atTime(input.time()).isAfter(LocalDateTime.now(clock))){BookingOpeningService.resolve(input.bookingOpening(),input.bookingOpeningLead(),date.atTime(input.time().withSecond(0).withNano(0)));any=true;break;}
        require(any,400,"Không có ngày chạy tương lai phù hợp với lịch lặp.");
    }
    private String fingerprint(RecurrenceContracts.Create input){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input)));}catch(Exception error){throw new IllegalStateException(error);}}
    public Map<String,Object> create(long admin,RecurrenceContracts.Create input){return tx.execute(status->{
        lock();String hash=fingerprint(input);var old=jdbc.queryForList("SELECT id FROM trip_recurrence WHERE created_by=? AND request_key=?",Long.class,admin,input.idempotencyKey());
        if(!old.isEmpty()){
            require(hash.equals(jdbc.queryForObject("SELECT request_hash FROM trip_recurrence WHERE id=?",String.class,old.getFirst())),409,"Mã yêu cầu đã dùng cho lịch lặp khác.");
            var existing=jdbc.queryForList("SELECT trip_id FROM trip_recurrence_day WHERE rule_id=? ORDER BY travel_date LIMIT 90",Long.class,old.getFirst()).stream().map(id->{var trip=trips.trip(id,false);return trips.view(trip,0,trips.tripStops(id).size()-1);}).toList();return Map.of("rule",view(rule(old.getFirst())),"trips",existing);
        }
        validate(input);trips.route(input.routeId());var vehicle=vehicles.resolve(input);
        long id=db.insert("INSERT INTO trip_recurrence(route_id,vehicle_id,vehicle,capacity,driver_id,driver,bus_attendant_id,bus_attendant,departure_time,slot,start_date,interval_weeks,weekdays,end_type,until_date,occurrence_limit,week_limit,processed_through,created_by,request_key,request_hash,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",input.routeId(),vehicle.vehicleId(),vehicle.licensePlate(),vehicle.capacity(),vehicle.driverId(),vehicle.driverName(),vehicle.busAttendantId(),vehicle.busAttendantName(),input.time(),input.slot(),input.startDate(),input.intervalWeeks(),days(input.weekdays()),input.endType(),input.untilDate(),input.occurrences(),input.weeks(),input.startDate().minusDays(1),admin,input.idempotencyKey(),hash,LocalDateTime.now(clock));
        var opening=input.bookingOpening();
        var lead = input.bookingOpeningLead();
        jdbc.update("""
            UPDATE trip_recurrence SET staff_booking_opens_at=?,priority_student_booking_opens_at=?,regular_booking_opens_at=?,
                staff_booking_lead_hours=?,priority_student_booking_lead_hours=?,regular_booking_lead_hours=? WHERE id=?
            """,opening==null?null:opening.staffOpensAt(),opening==null?null:opening.priorityStudentOpensAt(),opening==null?null:opening.regularOpensAt(),
            lead==null?null:lead.staffHours(),lead==null?null:lead.priorityStudentHours(),lead==null?null:lead.regularHours(),id);
        var generated=fill(rule(id),true);return Map.of("rule",view(rule(id)),"trips",generated);
    });}
    private List<TripView> fill(Rule rule,boolean initial){
        var input=rule.input();var today=LocalDate.now(clock);var horizon=(initial && input.startDate().isAfter(today)?input.startDate():today).plusDays(89);
        var end = endDate(input);
        if (end != null && end.isBefore(horizon)) horizon = end;
        var first=rule.through().plusDays(1);if(first.isBefore(today))first=today;if(first.isAfter(horizon)){
            if(end != null && today.isAfter(end))jdbc.update("UPDATE trip_recurrence SET status='COMPLETED',processed_through=?,last_error=? WHERE id=?",end,"Đã qua ngày kết thúc; không tạo bù chuyến quá khứ.",rule.id());
            return List.of();
        }
        var dates=new ArrayList<LocalDate>();int count=rule.generated();
        for(var date=first;!date.isAfter(horizon);date=date.plusDays(1)){
            if(input.endType().equals("COUNT") && count+dates.size()>=input.occurrences())break;
            if(matches(input,date) && date.atTime(input.time()).isAfter(LocalDateTime.now(clock)))dates.add(date);
        }
        List<TripView> generated=dates.isEmpty()?List.of():transport.createBatch(new Contracts.BatchTrips(input.routeId(),input.vehicleId(),dates,input.time(),input.slot(),input.vehicle(),input.capacity(),input.driverId(),input.driver(),input.busAttendantId(),input.busAttendant(),input.bookingOpening(),input.bookingOpeningLead()));
        for(var trip:generated)jdbc.update("INSERT INTO trip_recurrence_day(rule_id,travel_date,trip_id) VALUES (?,?,?)",rule.id(),LocalDate.parse(trip.date()),trip.tripId());
        count+=generated.size();boolean complete=input.endType().equals("COUNT") && count>=input.occurrences() || end != null && !horizon.isBefore(end);
        jdbc.update("UPDATE trip_recurrence SET generated_count=?,processed_through=?,status=?,last_error=NULL WHERE id=?",count,horizon,complete?"COMPLETED":"ACTIVE",rule.id());return generated;
    }
    public List<Map<String,Object>> list(){return jdbc.queryForList("SELECT id FROM trip_recurrence ORDER BY created_at DESC,id DESC",Long.class).stream().map(id->view(rule(id))).toList();}
    public Map<String,Object> changeStatus(long id,String target){return tx.execute(status->{lock();var rule=rule(id);require(!rule.status().equals("COMPLETED"),409,"Lịch lặp đã hoàn tất.");jdbc.update("UPDATE trip_recurrence SET status=?,last_error=NULL WHERE id=?",target,id);if(target.equals("ACTIVE"))fill(rule(id),false);return view(rule(id));});}
    private Map<String,Object> view(Rule rule){
        var input=rule.input();var result=new LinkedHashMap<String,Object>();result.put("ruleId",rule.id());result.put("routeName",rule.routeName());result.put("vehicle",input.vehicle());result.put("slot",input.slot());result.put("time",input.time());result.put("startDate",input.startDate());result.put("intervalWeeks",input.intervalWeeks());result.put("weekdays",input.weekdays());result.put("endType",input.endType());result.put("untilDate",input.untilDate());result.put("occurrences",input.occurrences());result.put("weeks",input.weeks());result.put("generatedCount",rule.generated());result.put("generatedThrough",rule.through());result.put("status",rule.status());result.put("lastError",rule.error());result.put("bookingOpening",input.bookingOpening());result.put("bookingOpeningLead",input.bookingOpeningLead());return result;
    }
    @Scheduled(fixedDelay=3600000,initialDelay=60000)
    public void extendActive(){for(long id:jdbc.queryForList("SELECT id FROM trip_recurrence WHERE status='ACTIVE' ORDER BY id",Long.class)){
        final Rule[] attempted={null};
        try{tx.execute(status->{lock();var current=rule(id);attempted[0]=current;if(current.status().equals("ACTIVE"))fill(current,false);return null;});}
        catch(Exception error){String message=error instanceof ApiException?error.getMessage():"Không thể tạo thêm chuyến. Kiểm tra xe, nhân sự và thử tiếp tục lịch.";LoggerFactory.getLogger(getClass()).error("Cannot extend trip recurrence {}",id,error);if(attempted[0]!=null)jdbc.update("UPDATE trip_recurrence SET status='PAUSED',last_error=? WHERE id=? AND status='ACTIVE' AND processed_through=? AND generated_count=?",message.substring(0,Math.min(300,message.length())),id,attempted[0].through(),attempted[0].generated());}
    }}
}
