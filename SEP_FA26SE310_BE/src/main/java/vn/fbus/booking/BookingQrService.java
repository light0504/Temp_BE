package vn.fbus.booking;

import jakarta.validation.constraints.*;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.auth.AuthFilter.Identity;
import vn.fbus.common.*;
import vn.fbus.crew.CrewService;
import vn.fbus.crew.CrewContracts.*;
import vn.fbus.transport.TransportRepository;
import static vn.fbus.common.ApiException.require;

@Service
public class BookingQrService {
 public static final int LIFETIME_SECONDS=30;
 private static final Pattern QR=Pattern.compile("FBUS:CHECKIN:1:([1-9][0-9]{0,18}):([a-f0-9-]{36}):([A-Za-z0-9_-]{43})");
 public record Scan(@NotBlank @Size(max=200) String qrCode) {}
 public record Ticket(String qrCode,Instant serverTime,Instant expiresAt,int refreshAfterSeconds) {}
 public record Preview(long bookingId,String fullName,String email,String seatCode,String boardingStop,String alightingStop,boolean alreadyCheckedIn,Instant serverTime,Instant expiresAt) {}
 public record Result(long bookingId,String fullName,String seatCode,boolean alreadyCheckedIn,Detail detail) {}
 private record Generation(String value,String sessionId,Instant expiresAt) {}
 private record Validated(Passenger passenger,Generation generation) {}
 private final JdbcTemplate jdbc; private final BookingRepository bookings; private final TransportRepository trips;
 private final CrewService crew;private final Clock clock;private final SecretKey key;
 public BookingQrService(JdbcTemplate jdbc,BookingRepository bookings,TransportRepository trips,CrewService crew,Clock clock,SecretKey key){this.jdbc=jdbc;this.bookings=bookings;this.trips=trips;this.crew=crew;this.clock=clock;this.key=key;}
 private Generation generation(long id){return jdbc.query("SELECT generation,session_id,expires_at FROM booking_checkin_qr WHERE booking_id=?",(r,n)->new Generation(r.getString(1),r.getString(2),r.getObject(3,OffsetDateTime.class).toInstant()),id).stream().findFirst().orElse(null);}
 private String signature(long id,Generation g){
  try{var mac=Mac.getInstance("HmacSHA256");mac.init(key);
   var content="fbus-booking-check-in-v1|"+id+"|"+g.value()+"|"+g.sessionId()+"|"+g.expiresAt().toEpochMilli();
   return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
  }catch(java.security.GeneralSecurityException e){throw new IllegalStateException("Cannot sign check-in QR",e);}
 }
 @Transactional
 public Ticket issue(Identity who,long id){
  jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",who.userId());
  var b=bookings.row(id,who.userId());var trip=trips.trip(b.tripId(),true);var view=bookings.view(id);
  require(b.status().equals("RESERVED"),409,"Đặt chỗ đã check-in hoặc không còn hiệu lực.");
  require(trip.status().equals("SCHEDULED")&&!trip.executionStatus().equals("COMPLETED"),409,"Chuyến đang tạm dừng hoặc đã kết thúc.");
  var boarding=trips.tripStops(trip.id()).stream().filter(s->s.tripStopId()==b.boarding()).findFirst().orElseThrow();
  var now=LocalDateTime.now(clock);
  require(boarding.position()>=trip.firstBoardingPosition(),409,"Xe đã qua điểm đón.");
  require(view.departureAt().toLocalDate().equals(now.toLocalDate())&&!now.isBefore(view.departureAt().minusMinutes(30)),409,"QR mở trong ngày đi, từ 30 phút trước giờ đón.");
  var current=generation(id);var time=clock.instant();
  // Reuse the live generation for retries and duplicate renders. Only one generation is valid.
  if(current==null || !current.sessionId().equals(who.sessionId()) || !current.expiresAt().isAfter(time)){
   current=new Generation(UUID.randomUUID().toString(),who.sessionId(),Instant.ofEpochMilli(time.plusSeconds(LIFETIME_SECONDS).toEpochMilli()));
   int updated=jdbc.update("UPDATE booking_checkin_qr SET generation=?,session_id=?,expires_at=? WHERE booking_id=?",current.value(),current.sessionId(),current.expiresAt().atOffset(ZoneOffset.UTC),id);
   if(updated==0)jdbc.update("INSERT INTO booking_checkin_qr(booking_id,generation,session_id,expires_at) VALUES (?,?,?,?)",id,current.value(),current.sessionId(),current.expiresAt().atOffset(ZoneOffset.UTC));
  }
  return new Ticket("FBUS:CHECKIN:1:"+id+":"+current.value()+":"+signature(id,current),clock.instant(),current.expiresAt(),LIFETIME_SECONDS);
 }
 private Validated validate(long user,long tripId,String code){
  trips.trip(tripId,true);
  var detail=crew.detail(user,tripId);
  require(detail.canManagePassengers(),403,"Chỉ nhân sự được phân công điểm danh mới được quét QR.");
  var matcher=QR.matcher(code);require(matcher.matches(),400,"QR không hợp lệ. Hãy quét mã trong Fbus.");
  long bookingId;
  try{bookingId=Long.parseLong(matcher.group(1));}catch(NumberFormatException e){throw new ApiException(400,"QR không hợp lệ.");}
  var p=detail.passengers().stream().filter(b->b.bookingId()==bookingId).findFirst().orElseThrow(()->new ApiException(409,"QR không thuộc chuyến xe này."));
  var g=generation(bookingId);
  require(g!=null&&g.value().equals(matcher.group(2))&&g.expiresAt().isAfter(clock.instant()),409,"QR đã hết hạn. Hãy mở mã mới và quét lại.");
  require(MessageDigest.isEqual(signature(bookingId,g).getBytes(StandardCharsets.US_ASCII),matcher.group(3).getBytes(StandardCharsets.US_ASCII)),400,"QR không hợp lệ.");
  var sessions=jdbc.query("SELECT expires_at FROM auth_session WHERE id=? AND revoked=FALSE",(r,n)->r.getObject(1,OffsetDateTime.class).toInstant(),g.sessionId());
  require(!sessions.isEmpty()&&sessions.getFirst().isAfter(clock.instant()),409,"QR thuộc phiên đã đăng xuất. Hãy mở mã mới.");
  require(p.status().equals("RESERVED")||p.status().equals("CHECKED_IN"),409,"Đặt chỗ đã hủy, vắng mặt hoặc đã xuống xe.");
  // Replays only acknowledge an existing check-in and never create another attendance event.
  if(!p.status().equals("CHECKED_IN")){
   require(detail.trip().status().equals("SCHEDULED")&&detail.operation().status().equals("IN_PROGRESS"),409,"Chuyến chưa bắt đầu, đang tạm dừng hoặc đã kết thúc.");
   require(!detail.operation().inTransit(),409,"Xe đang di chuyển.");
   require(p.boardingPosition()==detail.operation().currentStopPosition(),409,"QR không ở đúng điểm đón hiện tại.");
  }
  return new Validated(p,g);
 }
 @Transactional
 public Preview preview(long user,long tripId,String code){var v=validate(user,tripId,code);var p=v.passenger();return new Preview(p.bookingId(),p.fullName(),p.email(),p.seatCode(),p.boardingStop(),p.alightingStop(),p.status().equals("CHECKED_IN"),clock.instant(),v.generation().expiresAt());}
 @Transactional
 public Result checkIn(long user,long tripId,String code){
  var v=validate(user,tripId,code);var p=v.passenger();boolean already=p.status().equals("CHECKED_IN");
  var detail=crew.mark(user,tripId,p.bookingId(),new Attendance("CHECK_IN"));
  return new Result(p.bookingId(),p.fullName(),p.seatCode(),already,detail);
 }
}

