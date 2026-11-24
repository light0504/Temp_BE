package vn.fbus.history;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.booking.BookingRepository;
import vn.fbus.semester.SemesterService;
import java.time.*;
import java.util.*;
import static vn.fbus.common.ApiException.require;

@Service
public class UsageHistoryService {
 public record PassengerItem(BookingRepository.BookingView booking,String usageStatus) {}
 public record DriverItem(long tripId,String tripCode,String routeName,LocalDate date,String time,String vehicle,String usageStatus,LocalDateTime startedAt,LocalDateTime completedAt) {}
 private final JdbcTemplate jdbc;private final BookingRepository bookings;private final SemesterService semesters;private final Clock clock;
 public UsageHistoryService(JdbcTemplate jdbc,BookingRepository bookings,SemesterService semesters,Clock clock){this.jdbc=jdbc;this.bookings=bookings;this.semesters=semesters;this.clock=clock;}
 private void page(int page,int size){require(page>=0&&page<=100000&&size>=1&&size<=50,400,"Phân trang không hợp lệ.");}
 private Map<String,Object> response(SemesterService.Semester semester,List<?> rows,Map<String,Long> summary,int page,int size){
  return Map.of("semester",semester,"summary",summary,"items",rows.stream().skip((long)page*size).limit(size).toList(),
   "page",page,"totalPages",(rows.size()+size-1)/size,"totalElements",rows.size());
 }
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public Map<String,Object> passenger(long user,long semesterId,int page,int size){
  page(page,size);var semester=semesters.get(semesterId);var now=LocalDateTime.now(clock);
  var rows=bookings.my(user).stream().filter(b->b.semesterId()==semesterId)
   .filter(b->b.status().equals("CANCELLED")||b.checkedInAt()!=null||!b.departureAt().isAfter(now))
   .sorted(Comparator.comparing(BookingRepository.BookingView::departureAt).reversed().thenComparing(BookingRepository.BookingView::bookingId,Comparator.reverseOrder()))
   .map(b->{
    String status;
    if(b.status().equals("CANCELLED"))status="NO_SHOW".equals(b.cancellationReason())?"NO_SHOW":"CANCELLED";
    else if(b.checkedInAt()!=null){
     boolean completed=b.alightedAt()!=null||"COMPLETED".equals(b.executionStatus());
     status=completed?"TRAVELLED":"ON_BOARD";
    }else status="UNUSED";
    return new PassengerItem(b,status);
   }).toList();
  var summary=new LinkedHashMap<String,Long>();
  for(String status:List.of("TRAVELLED","ON_BOARD","CANCELLED","NO_SHOW","UNUSED"))summary.put(status,rows.stream().filter(r->r.usageStatus().equals(status)).count());
  return response(semester,rows,summary,page,size);
 }
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public Map<String,Object> driver(long user,long semesterId,int page,int size){
  page(page,size);var semester=semesters.get(semesterId);
  var rows=jdbc.query("""
   SELECT t.*,r.name AS route_name FROM trip t JOIN route r ON r.id=t.route_id
   WHERE t.driver_id=? AND t.semester_id=?
    AND (t.execution_status='COMPLETED' OR t.status='CANCELLED' OR t.started_at IS NOT NULL OR t.arrival_at<=?)
   ORDER BY t.departure_at DESC,t.id DESC
   """,(r,n)->new DriverItem(r.getLong("id"),r.getString("code"),r.getString("route_name"),
    r.getObject("departure_at",LocalDateTime.class).toLocalDate(),r.getObject("departure_at",LocalDateTime.class).toLocalTime().toString(),
    r.getString("vehicle"),r.getString("status").equals("CANCELLED")?"CANCELLED":r.getString("execution_status").equals("COMPLETED")?"COMPLETED":r.getString("execution_status").equals("IN_PROGRESS")?"IN_PROGRESS":"NOT_OPERATED",
    r.getObject("started_at",LocalDateTime.class),r.getObject("completed_at",LocalDateTime.class)),
    user,semesterId,LocalDateTime.now(clock));
  var summary=new LinkedHashMap<String,Long>();
  for(String status:List.of("COMPLETED","IN_PROGRESS","CANCELLED","NOT_OPERATED"))summary.put(status,rows.stream().filter(r->r.usageStatus().equals(status)).count());
  return response(semester,rows,summary,page,size);
 }
}