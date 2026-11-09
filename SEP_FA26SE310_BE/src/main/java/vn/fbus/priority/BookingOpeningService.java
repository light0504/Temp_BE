package vn.fbus.priority;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import vn.fbus.common.*;
import vn.fbus.transport.TransportRepository.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static vn.fbus.common.ApiException.require;
@Service
public class BookingOpeningService {
 public record Access(String group,LocalDateTime opensAt,boolean allowed,LocalDateTime serverTime){}
 private final BookingPriorityService priorities;private final Clock clock;private final ObjectMapper json;
 public BookingOpeningService(BookingPriorityService priorities,Clock clock,ObjectMapper json){this.priorities=priorities;this.clock=clock;this.json=json;}
 public static void validate(Contracts.BookingOpening opening,LocalDateTime departure){
  if(opening==null)return;
  require(opening.staffOpensAt()!=null&&opening.priorityStudentOpensAt()!=null&&opening.regularOpensAt()!=null,400,"Điền đủ giờ mở đặt chỗ của ba nhóm.");
  require(!opening.staffOpensAt().isAfter(opening.priorityStudentOpensAt())&&!opening.priorityStudentOpensAt().isAfter(opening.regularOpensAt()),400,"Giờ mở phải theo thứ tự: giảng viên/nhân viên, sinh viên ưu tiên, còn lại.");
  require(opening.regularOpensAt().isBefore(departure),400,"Giờ mở đặt chỗ phải trước giờ khởi hành của mọi chuyến.");
 }
 public static Contracts.BookingOpening resolve(Contracts.BookingOpening opening,
                                                Contracts.BookingOpeningLead lead, LocalDateTime departure) {
  if (lead == null) {
   validate(opening, departure);
   return opening;
  }
  require(opening == null, 400, "Chỉ chọn một cách cấu hình giờ mở đặt chỗ.");
  Integer staff = lead.staffHours(), student = lead.priorityStudentHours(), regular = lead.regularHours();
  if (staff == null && student == null && regular == null) return null;
  if (regular == null) regular = student == null ? staff : student;
  if (student == null) student = regular;
  if (staff == null) staff = student;
  require(regular >= 1 && staff >= student && student >= regular && staff <= 8783, 400,
      "Nhóm ưu tiên cao hơn phải mở đặt chỗ sớm hơn hoặc cùng lúc với nhóm thấp hơn.");
  return new Contracts.BookingOpening(departure.minusHours(staff), departure.minusHours(student), departure.minusHours(regular));
 }
 public Access access(String group,Contracts.BookingOpening opening){
  LocalDateTime at=opening==null?null:switch(group){case "STAFF"->opening.staffOpensAt();case "STUDENT"->opening.priorityStudentOpensAt();default->opening.regularOpensAt();};
  var now=LocalDateTime.now(clock);return new Access(group,at,at==null||!now.isBefore(at),now);
 }
 public Access access(long user,TripRow trip){return access(priorities.group(user),trip.bookingOpening());}
 public void requireOpen(long user,TripRow trip){var a=access(user,trip);require(a.allowed(),409,"Nhóm của bạn mở đặt chỗ lúc "+(a.opensAt()==null?"":a.opensAt().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")))+" (giờ Việt Nam).");}
 public List<Map<String,Object>> studentTrips(long user,List<TripView> rows){
  String group=priorities.group(user);return rows.stream().map(trip->{Map<String,Object> item=json.convertValue(trip,new TypeReference<LinkedHashMap<String,Object>>(){});item.put("bookingAccess",access(group,trip.bookingOpening()));return item;}).toList();
 }
}

