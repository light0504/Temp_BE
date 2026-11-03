package vn.fbus.crew;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.common.*;
import vn.fbus.transport.TransportRepository;
import vn.fbus.transport.TransportRepository.TripRow;
import java.time.*;
import java.util.*;
import static vn.fbus.common.ApiException.require;
import static vn.fbus.crew.CrewContracts.*;

@Service
public class CrewService {
 private final JdbcTemplate jdbc; private final Database db; private final TransportRepository trips; private final Clock clock;
 public CrewService(JdbcTemplate jdbc, Database db, TransportRepository trips, Clock clock) {
   this.jdbc=jdbc;this.db=db;this.trips=trips;this.clock=clock;
 }
 private LocalDateTime now() { return LocalDateTime.now(clock); }
 private TripRow assigned(long user, long id, boolean lock) {
   var t=trips.trip(id,lock);
   require(Objects.equals(t.driverId(),user)||Objects.equals(t.busAttendantId(),user),403,"Bạn không được phân công chuyến này.");
   return t;
 }
 private boolean driver(long user, TripRow t) { return Objects.equals(t.driverId(),user); }
 private boolean attendance(long user, TripRow t) {
   return Objects.equals(t.busAttendantId(),user) || (driver(user,t) && t.busAttendantId()==null);
 }
 private Operation operation(long id) {
   return jdbc.query("SELECT execution_status,current_stop_position,started_at,completed_at FROM trip WHERE id=?",
    (r,n)->new Operation(r.getString(1),r.getInt(2),r.getObject(3,LocalDateTime.class),r.getObject(4,LocalDateTime.class)),id).getFirst();
 }
 public List<CrewTrip> list(long user, LocalDate from, LocalDate to) {
   require(!to.isBefore(from) && !to.isAfter(from.plusDays(92)),400,"Khoảng thời gian tối đa 93 ngày.");
   return trips.assignedTrips(user,from.atStartOfDay()).stream()
     .filter(t->t.departureAt().isBefore(to.plusDays(1).atStartOfDay()))
     .map(t->new CrewTrip(trips.view(t,0,trips.tripStops(t.id()).size()-1),operation(t.id()),driver(user,t),attendance(user,t))).toList();
 }
 @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public Detail detail(long user,long id) {
   var t=assigned(user,id,false);
   return new Detail(trips.view(t,0,trips.tripStops(id).size()-1),operation(id),driver(user,t),attendance(user,t),passengers(id));
 }
 private List<Passenger> passengers(long id) {
   return jdbc.query("""
     SELECT b.*,u.full_name,u.email,s.code AS seat_code,bn.name AS boarding_name,an.name AS alighting_name
     FROM booking b JOIN app_user u ON u.id=b.user_id JOIN seat s ON s.id=b.seat_id
     JOIN trip_stop bs ON bs.id=b.boarding_trip_stop_id JOIN stop bn ON bn.id=bs.stop_id
     JOIN trip_stop als ON als.id=b.alighting_trip_stop_id JOIN stop an ON an.id=als.stop_id
     WHERE b.trip_id=? ORDER BY b.boarding_position,s.id,b.id
     """,(r,n)->new Passenger(r.getLong("id"),r.getString("code"),r.getString("full_name"),r.getString("email"),r.getString("seat_code"),
       r.getLong("boarding_trip_stop_id"),r.getString("boarding_name"),r.getInt("boarding_position"),r.getLong("alighting_trip_stop_id"),
       r.getString("alighting_name"),r.getInt("alighting_position"),
       "NO_SHOW".equals(r.getString("cancellation_reason"))?"NO_SHOW":r.getObject("alighted_at")!=null?"ALIGHTED":r.getString("status"),
       r.getObject("checked_in_at",LocalDateTime.class),r.getObject("alighted_at",LocalDateTime.class)),id);
 }
 private void event(long user,long id,Long booking,String action,int position) {
   jdbc.update("INSERT INTO crew_event(trip_id,user_id,booking_id,action,stop_position,created_at) VALUES (?,?,?,?,?,?)",id,user,booking,action,position,now());
 }
 @Transactional
 public Detail progress(long user,long id,Progress input) {
   var t=assigned(user,id,true); require(driver(user,t),403,"Chỉ tài xế được cập nhật tiến trình chuyến.");
   var o=operation(id); var stops=trips.tripStops(id); var time=now();
   int expected=input.expectedPosition();
   // Replay after a lost response must not move the bus to the next stop twice.
   if (input.action().equals("START") && o.status().equals("IN_PROGRESS") && expected==-1 && o.currentStopPosition()==0) return detail(user,id);
   if (input.action().equals("ARRIVE") && o.status().equals("IN_PROGRESS") && o.currentStopPosition()==expected+1) return detail(user,id);
   if (input.action().equals("COMPLETE") && o.status().equals("COMPLETED") && expected==o.currentStopPosition()) return detail(user,id);
   require(expected==o.currentStopPosition(),409,"Tiến trình đã thay đổi. Vui lòng tải lại.");
   require(t.status().equals("SCHEDULED"),409,"Chuyến đang tạm dừng.");
   if (input.action().equals("START")) {
     require(o.status().equals("READY"),409,"Chuyến đã bắt đầu hoặc kết thúc.");
     require(t.departureAt().toLocalDate().equals(time.toLocalDate()) && !time.isBefore(t.departureAt().minusMinutes(30)),409,"Chỉ bắt đầu trong ngày, từ 30 phút trước giờ khởi hành.");
     jdbc.update("UPDATE trip SET execution_status='IN_PROGRESS',current_stop_position=0,started_at=? WHERE id=?",time,id);
     event(user,id,null,"START",0);
   } else {
     require(o.status().equals("IN_PROGRESS"),409,"Chuyến chưa bắt đầu hoặc đã kết thúc.");
     int position=o.currentStopPosition();
     int pending=jdbc.queryForObject("""
       SELECT COUNT(*) FROM booking WHERE trip_id=? AND
       ((status='RESERVED' AND boarding_position<=?) OR (status='CHECKED_IN' AND alighted_at IS NULL AND alighting_position<=?))
       """,Integer.class,id,position,position);
     require(pending==0,409,"Cần hoàn tất điểm danh đón/trả tại điểm hiện tại.");
     if (input.action().equals("ARRIVE")) {
       require(position<stops.size()-1,409,"Đã đến điểm cuối.");
       require(!time.isBefore(stops.get(position+1).arrivalAt().minusMinutes(15)),409,"Chưa đến thời gian của điểm tiếp theo (sớm nhất 15 phút trước lịch).");
       jdbc.update("UPDATE trip SET current_stop_position=? WHERE id=?",position+1,id);
       event(user,id,null,"ARRIVE",position+1);
     } else {
       require(position==stops.size()-1,409,"Chưa đến điểm cuối.");
       require(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE trip_id=? AND (status='RESERVED' OR (status='CHECKED_IN' AND alighted_at IS NULL))",Integer.class,id)==0,409,"Còn hành khách chưa hoàn tất điểm danh.");
       jdbc.update("UPDATE trip SET execution_status='COMPLETED',completed_at=? WHERE id=?",time,id);
       event(user,id,null,"COMPLETE",position);
     }
   }
   return detail(user,id);
 }
 @Transactional
 public Detail mark(long user,long id,long bookingId,Attendance input) {
   var t=assigned(user,id,true); require(attendance(user,t),403,"Phụ xe được phân công thực hiện điểm danh.");
   var p=passengers(id).stream().filter(b->b.bookingId()==bookingId).findFirst().orElseThrow(()->new ApiException(404,"Không tìm thấy đặt chỗ trên chuyến."));
   String desired=switch(input.action()){case "CHECK_IN"->"CHECKED_IN";case "CHECK_OUT"->"ALIGHTED";default->"NO_SHOW";};
   if (p.status().equals(desired)) return detail(user,id);
   var o=operation(id);
   require(t.status().equals("SCHEDULED") && o.status().equals("IN_PROGRESS"),409,"Chuyến chưa bắt đầu, đang tạm dừng hoặc đã kết thúc.");
   if (input.action().equals("CHECK_OUT")) {
     require(p.status().equals("CHECKED_IN") && p.alightingPosition()==o.currentStopPosition(),409,"Chỉ trả hành khách đã lên xe tại đúng điểm trả.");
     jdbc.update("UPDATE booking SET alighted_at=? WHERE id=?",now(),bookingId);
   } else {
     require(p.status().equals("RESERVED") && p.boardingPosition()==o.currentStopPosition(),409,"Chỉ điểm danh đặt chỗ còn hiệu lực tại đúng điểm đón.");
     if (input.action().equals("CHECK_IN")) jdbc.update("UPDATE booking SET status='CHECKED_IN',checked_in_at=? WHERE id=?",now(),bookingId);
     else {
       var boarding=trips.tripStops(id).stream().filter(s->s.tripStopId()==p.boardingTripStopId()).findFirst().orElseThrow();
       require(!now().isBefore(boarding.arrivalAt()),409,"Chưa đến giờ đón, không thể ghi nhận vắng mặt.");
       jdbc.update("UPDATE booking SET status='CANCELLED',cancellation_reason='NO_SHOW',cancelled_at=? WHERE id=?",now(),bookingId);
     }
   }
   event(user,id,bookingId,input.action(),o.currentStopPosition());
   return detail(user,id);
 }
 @Transactional
 public Report report(long user,long id,ReportInput input) {
   assigned(user,id,true);
   var existing=jdbc.queryForList("SELECT id,trip_id,category,priority,location,description FROM trip_report WHERE reporter_id=? AND request_key=?",user,input.idempotencyKey());
   if(!existing.isEmpty()) {
     var old=existing.getFirst();
     require(((Number)old.get("trip_id")).longValue()==id && Objects.equals(old.get("category"),input.category()) && Objects.equals(old.get("priority"),input.priority()) && Objects.equals(old.get("location"),input.location().trim()) && Objects.equals(old.get("description"),input.description().trim()),409,"Mã yêu cầu đã được dùng cho báo cáo khác.");
     return reportView(((Number)old.get("id")).longValue());
   }
   long reportId=db.insert("INSERT INTO trip_report(trip_id,reporter_id,category,priority,location,description,request_key,created_at) VALUES (?,?,?,?,?,?,?,?)",
     id,user,input.category(),input.priority(),input.location().trim(),input.description().trim(),input.idempotencyKey(),now());
   return reportView(reportId);
 }
 private static final String REPORT_SQL="""
   SELECT p.*,t.code AS trip_code,t.vehicle,r.name AS route_name,u.full_name AS reporter,u.role AS reporter_role,a.full_name AS resolved_name
   FROM trip_report p JOIN trip t ON t.id=p.trip_id JOIN route r ON r.id=t.route_id
   JOIN app_user u ON u.id=p.reporter_id LEFT JOIN app_user a ON a.id=p.resolved_by
   """;
 private List<Report> queryReports(String where,Object... args) {
   return jdbc.query(REPORT_SQL+where,(r,n)->new Report(r.getLong("id"),r.getLong("trip_id"),r.getString("trip_code"),r.getString("route_name"),r.getString("vehicle"),r.getString("reporter"),r.getString("reporter_role"),r.getString("category"),r.getString("priority"),r.getString("location"),r.getString("description"),r.getString("status"),r.getObject("created_at",LocalDateTime.class),r.getString("resolution"),r.getString("resolved_name"),r.getObject("updated_at",LocalDateTime.class)),args);
 }
 private Report reportView(long id) { return queryReports(" WHERE p.id=?",id).stream().findFirst().orElseThrow(()->new ApiException(404,"Không tìm thấy báo cáo.")); }
 public List<Report> reports(long user,long tripId) { assigned(user,tripId,false);return queryReports(" WHERE p.trip_id=? AND p.reporter_id=? ORDER BY p.created_at DESC,p.id DESC",tripId,user); }
 public List<Report> adminReports(String status,int page,int size) {
   require(status==null||List.of("OPEN","IN_REVIEW","RESOLVED").contains(status),400,"Trạng thái không hợp lệ.");
   require(page>=0&&page<=100000&&size>=1&&size<=100,400,"Phân trang không hợp lệ.");
   return status==null?queryReports(" ORDER BY p.created_at DESC,p.id DESC LIMIT ? OFFSET ?",size,page*size):queryReports(" WHERE p.status=? ORDER BY p.created_at DESC,p.id DESC LIMIT ? OFFSET ?",status,size,page*size);
 }
 @Transactional
 public Report resolve(long admin,long id,Resolution input) {
   jdbc.queryForList("SELECT id FROM trip_report WHERE id=? FOR UPDATE",id); var old=reportView(id);
   if(old.status().equals(input.status())&&Objects.equals(old.resolution(),input.resolution()==null?null:input.resolution().trim())) return old;
   require(!input.status().equals("RESOLVED") || (input.resolution()!=null&&!input.resolution().isBlank()),400,"Nhập kết quả xử lý.");
   jdbc.update("UPDATE trip_report SET status=?,resolution=?,resolved_by=?,updated_at=? WHERE id=?",input.status(),input.resolution()==null?null:input.resolution().trim(),admin,now(),id);
   return reportView(id);
 }
}

