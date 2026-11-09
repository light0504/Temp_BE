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
   return Objects.equals(t.busAttendantId(),user) || driver(user,t);
 }
 private Operation operation(long id) {
   return jdbc.query("SELECT execution_status,current_stop_position,started_at,completed_at,in_transit FROM trip WHERE id=?",
    (r,n)->new Operation(r.getString(1),r.getInt(2),r.getObject(3,LocalDateTime.class),r.getObject(4,LocalDateTime.class),r.getBoolean("in_transit")),id).getFirst();
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
   int lastStop = trips.tripStops(id).size() - 1;
   return new Detail(trips.view(t, 0, lastStop), operation(id), driver(user, t), attendance(user, t),
       passengers(id), trips.seats(id, 0, lastStop), now());
 }
 private List<Passenger> passengers(long id) {
   return jdbc.query("""
     SELECT b.*, COALESCE(u.full_name, 'Sinh viên ' || b.walk_in_student_code) AS full_name,
       u.email, s.code AS seat_code, bn.name AS boarding_name, an.name AS alighting_name
     FROM booking b LEFT JOIN app_user u ON u.id=b.user_id JOIN seat s ON s.id=b.seat_id
     JOIN trip_stop bs ON bs.id=b.boarding_trip_stop_id JOIN stop bn ON bn.id=bs.stop_id
     JOIN trip_stop als ON als.id=b.alighting_trip_stop_id JOIN stop an ON an.id=als.stop_id
     WHERE b.trip_id=? ORDER BY b.boarding_position,s.id,b.id
     """,(r,n)->new Passenger(r.getLong("id"),r.getString("code"),r.getString("full_name"),r.getString("email"),r.getString("seat_code"),
       r.getLong("boarding_trip_stop_id"),r.getString("boarding_name"),r.getInt("boarding_position"),r.getLong("alighting_trip_stop_id"),
       r.getString("alighting_name"),r.getInt("alighting_position"),
       "NO_SHOW".equals(r.getString("cancellation_reason"))?"NO_SHOW":r.getObject("alighted_at")!=null?"ALIGHTED":r.getString("status"),
       r.getObject("checked_in_at",LocalDateTime.class),r.getObject("alighted_at",LocalDateTime.class),r.getString("walk_in_student_code")),id);
 }
 private void event(long user,long id,Long booking,String action,int position) {
   jdbc.update("INSERT INTO crew_event(trip_id,user_id,booking_id,action,stop_position,created_at) VALUES (?,?,?,?,?,?)",id,user,booking,action,position,now());
 }
 @Transactional
 public Detail progress(long user, long id, Progress input) {
   var trip = assigned(user, id, true);
   require(driver(user, trip), 403, "Chỉ tài xế được cập nhật tiến trình chuyến.");
   var operation = operation(id);
   String action = input.action();
   int position = operation.currentStopPosition();
   int expected = input.expectedPosition();
   boolean inProgress = operation.status().equals("IN_PROGRESS");

   // Retries acknowledge the same step without advancing the trip again.
   boolean replay = switch (action) {
     case "START" -> inProgress && expected == -1 && position == 0;
     case "DEPART" -> operation.inTransit() && position == expected;
     case "ARRIVE" -> inProgress && expected >= 0 && position == expected + 1;
     case "COMPLETE" -> operation.status().equals("COMPLETED") && position == expected;
     default -> false;
   };
   if (replay) return detail(user, id);
   require(position == expected, 409, "Tiến trình đã thay đổi. Vui lòng tải lại.");
   require(trip.status().equals("SCHEDULED"), 409, "Chuyến đang tạm dừng.");

   var stops = trips.tripStops(id);
   int lastPosition = stops.getLast().position();
   var time = now();
   switch (action) {
     case "START" -> {
       require(operation.status().equals("READY"), 409, "Chuyến đã bắt đầu hoặc kết thúc.");
       require(trip.departureAt().toLocalDate().equals(time.toLocalDate())
           && !time.isBefore(trip.departureAt().minusMinutes(30)), 409,
           "Chỉ xác nhận đến điểm đầu trong ngày, từ 30 phút trước giờ khởi hành.");
       position = 0;
       jdbc.update("UPDATE trip SET execution_status='IN_PROGRESS',current_stop_position=?,started_at=? WHERE id=?", position, time, id);
     }
     case "DEPART" -> {
       require(inProgress && !operation.inTransit(), 409, "Xe chưa dừng tại bến.");
       require(position < lastPosition, 409, "Đã đến điểm cuối.");
       requireAttendanceComplete(id, position);
       jdbc.update("UPDATE trip SET in_transit=TRUE WHERE id=?", id);
     }
     case "ARRIVE" -> {
       require(operation.inTransit() && position < lastPosition, 409, "Hãy bắt đầu di chuyển trước.");
       int destination = position + 1;
       require(!time.isBefore(stops.get(destination).arrivalAt().minusMinutes(15)), 409,
           "Chỉ xác nhận đến bến từ 15 phút trước giờ dự kiến.");
       jdbc.update("UPDATE trip SET current_stop_position=?,in_transit=FALSE WHERE id=?", destination, id);
       position = destination;
     }
     case "COMPLETE" -> {
       require(inProgress && !operation.inTransit() && position == lastPosition, 409, "Xe chưa đến điểm cuối.");
       requireAttendanceComplete(id, position);
       jdbc.update("UPDATE trip SET execution_status='COMPLETED',completed_at=? WHERE id=?", time, id);
     }
     default -> throw new ApiException(400, "Thao tác không hợp lệ.");
   }
   event(user, id, null, action, position);
   return detail(user, id);
 }
 private void requireAttendanceComplete(long tripId, int position) {
   int pending = jdbc.queryForObject("""
       SELECT COUNT(*) FROM booking WHERE trip_id=? AND
       ((status='RESERVED' AND boarding_position<=?)
         OR (status='CHECKED_IN' AND alighted_at IS NULL AND alighting_position<=?))
       """, Integer.class, tripId, position, position);
   require(pending == 0, 409, "Còn khách cần điểm danh tại bến.");
 }
 @Transactional
 public Detail mark(long user,long id,long bookingId,Attendance input) {
   var t=assigned(user,id,true); require(attendance(user,t),403,"Chỉ nhân sự được phân công được điểm danh.");
   var p=passengers(id).stream().filter(b->b.bookingId()==bookingId).findFirst().orElseThrow(()->new ApiException(404,"Không tìm thấy đặt chỗ trên chuyến."));
   String desired=switch(input.action()){case "CHECK_IN"->"CHECKED_IN";case "CHECK_OUT"->"ALIGHTED";default->"NO_SHOW";};
   if (p.status().equals(desired)) return detail(user,id);
   var o=operation(id);
   require(t.status().equals("SCHEDULED") && o.status().equals("IN_PROGRESS"),409,"Chuyến chưa bắt đầu, đang tạm dừng hoặc đã kết thúc.");
   require(!o.inTransit(),409,"Xe đang di chuyển.");
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
 public Detail addWalkIn(long user, long tripId, WalkIn input) {
   var trip = assigned(user, tripId, true);
   require(attendance(user, trip), 403, "Bạn không có quyền thêm hành khách trên chuyến này.");
   String studentCode = input.studentCode().trim().toUpperCase(Locale.ROOT);
   require(studentCode.matches("[A-Z0-9][A-Z0-9-]{1,29}"), 400, "Mã số sinh viên không hợp lệ.");

   var previous = jdbc.queryForList("""
       SELECT walk_in_student_code, seat_id, boarding_trip_stop_id, alighting_trip_stop_id
       FROM booking WHERE trip_id=? AND user_id IS NULL AND idempotency_key=?
       """, tripId, input.idempotencyKey());
   if (!previous.isEmpty()) {
     var old = previous.getFirst();
     require(studentCode.equals(old.get("walk_in_student_code"))
         && input.seatId().longValue() == ((Number) old.get("seat_id")).longValue()
         && input.boardingTripStopId().longValue() == ((Number) old.get("boarding_trip_stop_id")).longValue()
         && input.alightingTripStopId().longValue() == ((Number) old.get("alighting_trip_stop_id")).longValue(),
         409, "Mã yêu cầu đã dùng cho một lượt lên xe khác.");
     return detail(user, tripId);
   }

   require(trip.status().equals("SCHEDULED") && trip.executionStatus().equals("IN_PROGRESS"),
       409, "Chỉ thêm khách khi chuyến đang thực hiện.");
   require(!trip.inTransit(), 409, "Xe đang di chuyển.");
   var stops = trips.tripStops(tripId);
   int currentPosition = trip.currentStopPosition();
   var boarding = stops.stream().filter(stop -> stop.position() == currentPosition).findFirst()
       .orElseThrow(() -> new ApiException(409, "Chuyến chưa có điểm đón hiện tại."));
   require(boarding.tripStopId() == input.boardingTripStopId(), 409, "Xe đã đổi điểm dừng. Vui lòng tải lại.");
   var alighting = stops.stream().filter(stop -> stop.tripStopId() == input.alightingTripStopId()).findFirst()
       .orElseThrow(() -> new ApiException(400, "Điểm trả không thuộc chuyến."));
   require(alighting.position() > boarding.position(), 400, "Điểm trả phải sau điểm đón hiện tại.");

   int existing = jdbc.queryForObject("""
       SELECT COUNT(*) FROM booking
       WHERE trip_id=? AND walk_in_student_code=? AND status IN ('RESERVED','CHECKED_IN')
       """, Integer.class, tripId, studentCode);
   require(existing == 0, 409, "Mã sinh viên này đã được ghi nhận trên chuyến.");
   var seat = trips.seats(tripId, boarding.position(), alighting.position()).stream()
       .filter(item -> item.seatId() == input.seatId()).findFirst()
       .orElseThrow(() -> new ApiException(400, "Ghế không thuộc chuyến xe."));
   require(seat.status().equals("AVAILABLE"), 409, "Ghế đã có người trên chặng này. Vui lòng chọn ghế khác.");
   int waitingToAlight = jdbc.queryForObject("""
       SELECT COUNT(*) FROM booking WHERE trip_id=? AND seat_id=? AND status='CHECKED_IN'
       AND alighted_at IS NULL AND alighting_position<=?
       """, Integer.class, tripId, seat.seatId(), currentPosition);
   require(waitingToAlight == 0, 409, "Xác nhận khách xuống ghế này trước khi thêm khách mới.");

   LocalDateTime boardedAt = now();
   long bookingId = db.insert("""
       INSERT INTO booking(code, walk_in_student_code, trip_id, boarding_trip_stop_id, alighting_trip_stop_id,
           boarding_position, alighting_position, seat_id, status, idempotency_key, reserved_at, checked_in_at)
       VALUES (?,?,?,?,?,?,?,?,'CHECKED_IN',?,?,?)
       """, UUID.randomUUID().toString(), studentCode, tripId, boarding.tripStopId(), alighting.tripStopId(),
       boarding.position(), alighting.position(), seat.seatId(), input.idempotencyKey(), boardedAt, boardedAt);
   event(user, tripId, bookingId, "WALK_IN", currentPosition);
   return detail(user, tripId);
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

