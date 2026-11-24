package vn.fbus.semester;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.common.*;
import java.time.*;
import java.util.*;
import static vn.fbus.common.ApiException.require;

@Service
public class SemesterService {
 public record Semester(long semesterId,String term,int year,String name,LocalDate startDate,LocalDate endDate) {}
 public record Input(@Pattern(regexp="SPRING|SUMMER|FALL") String term,
                     @Min(2000) @Max(9998) Integer year,LocalDate startDate,LocalDate endDate) {}
 private final JdbcTemplate jdbc; private final Database db; private final Clock clock;
 public SemesterService(JdbcTemplate jdbc,Database db,Clock clock){this.jdbc=jdbc;this.db=db;this.clock=clock;}
 public List<Semester> list(){return jdbc.query("SELECT * FROM semester ORDER BY start_date DESC",(r,n)->new Semester(r.getLong("id"),r.getString("term"),r.getInt("academic_year"),r.getString("name"),r.getObject("start_date",LocalDate.class),r.getObject("end_date",LocalDate.class)));}
 public Semester get(long id){return list().stream().filter(s->s.semesterId()==id).findFirst().orElseThrow(()->new ApiException(404,"Không tìm thấy học kỳ."));}
 public Semester forDate(LocalDate date){return list().stream().filter(s->!date.isBefore(s.startDate())&&!date.isAfter(s.endDate())).findFirst().orElseThrow(()->new ApiException(400,"Chưa có học kỳ cho ngày "+date+". Tạo học kỳ trước."));}
 public Semester resolve(Long id,LocalDate from,LocalDate to){
  var semester=id==null?forDate(from):get(id);
  require(!from.isBefore(semester.startDate())&&!to.isAfter(semester.endDate())&&!to.isBefore(from),400,"Ngày phải trong học kỳ "+semester.name()+".");
  return semester;
 }
 public void lock(){jdbc.queryForList("SELECT name FROM operation_lock WHERE name='trip-schedule' FOR UPDATE");}
 @Transactional public Semester save(Long id,Input input){
  lock();var today=LocalDate.now(clock);int year=input.year()==null?today.getYear()+(today.getMonthValue()>=9?1:0):input.year();
  String term=input.term()==null?(today.getMonthValue()>=9?"SPRING":today.getMonthValue()>=5?"SUMMER":"SPRING"):input.term();
  int month=switch(term){case "SPRING"->1;case "SUMMER"->5;default->9;};
  var start=input.startDate()==null?LocalDate.of(year,month,1):input.startDate();
  var end=input.endDate()==null?LocalDate.of(year,month+3,1).withDayOfMonth(LocalDate.of(year,month+3,1).lengthOfMonth()):input.endDate();
  require(!end.isBefore(start)&&start.getYear()==year&&end.getYear()==year,400,"Khoảng ngày học kỳ không hợp lệ.");
  require(jdbc.queryForObject("SELECT COUNT(*) FROM semester WHERE id<>? AND (start_date<=? AND end_date>=? OR term=? AND academic_year=?)",Integer.class,id==null?-1:id,end,start,term,year)==0,409,"Học kỳ hoặc khoảng ngày đã tồn tại.");
  if(id!=null){
   get(id);
   require(jdbc.queryForObject("SELECT COUNT(*) FROM trip WHERE semester_id=? AND (CAST(departure_at AS DATE)<? OR CAST(departure_at AS DATE)>?)",Integer.class,id,start,end)==0,409,"Ngày mới không bao phủ các chuyến đã tạo.");
   require(jdbc.queryForObject("SELECT COUNT(*) FROM booking_plan WHERE semester_id=? AND (start_date<? OR end_date>?)",Integer.class,id,start,end)==0,409,"Ngày mới không bao phủ lịch đặt dài hạn.");
   require(jdbc.queryForObject("SELECT COUNT(*) FROM trip_wishlist WHERE semester_id=? AND (travel_date<? OR travel_date>?)",Integer.class,id,start,end)==0,409,"Ngày mới không bao phủ wishlist.");
   require(jdbc.queryForObject("SELECT COUNT(*) FROM trip_recurrence WHERE semester_id=? AND (start_date<? OR start_date>?)",Integer.class,id,start,end)==0,409,"Ngày mới không bao phủ lịch lặp.");
  }
  String name=term.substring(0,1)+term.substring(1).toLowerCase(Locale.ROOT)+" "+year;
  if(id==null)id=db.insert("INSERT INTO semester(term,academic_year,name,start_date,end_date) VALUES (?,?,?,?,?)",term,year,name,start,end);
  else jdbc.update("UPDATE semester SET term=?,academic_year=?,name=?,start_date=?,end_date=? WHERE id=?",input.term(),year,name,start,end,id);
  return get(id);
 }
}