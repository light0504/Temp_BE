package vn.fbus.priority;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static vn.fbus.common.ApiException.require;
@Service
public class BookingPriorityService {
 private final JdbcTemplate jdbc;private final Clock clock;
 public BookingPriorityService(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
 private String email(String value){return value.trim().toLowerCase(Locale.ROOT);}
 private void lock(){jdbc.queryForList("SELECT name FROM operation_lock WHERE name='booking-priority' FOR UPDATE");}
 public String group(long user){return jdbc.queryForObject("SELECT COALESCE(p.priority_group,'REGULAR') FROM app_user u LEFT JOIN booking_priority p ON p.email=LOWER(u.email) WHERE u.id=?",String.class,user);}
 public Map<String,Object> list(int page,int size){
  require(page>=0&&page<=100000&&size>=1&&size<=100,400,"Phân trang không hợp lệ.");
  long count=jdbc.queryForObject("SELECT COUNT(*) FROM booking_priority",Long.class);
  var rows=jdbc.query("SELECT p.*,u.id AS user_id,u.full_name FROM booking_priority p LEFT JOIN app_user u ON LOWER(u.email)=p.email ORDER BY p.updated_at DESC,p.email LIMIT ? OFFSET ?",(r,n)->{
   var row=new LinkedHashMap<String,Object>();row.put("email",r.getString("email"));row.put("group",r.getString("priority_group"));row.put("userId",r.getObject("user_id",Long.class));row.put("fullName",r.getString("full_name"));row.put("updatedAt",r.getObject("updated_at",LocalDateTime.class));return row;
  },size,page*size);
  return Map.of("content",rows,"page",Map.of("number",page,"size",size,"totalElements",count,"totalPages",(count+size-1)/size));
 }
 private String saveEntry(PriorityContracts.Entry entry,long admin){
  String target=email(entry.email());var old=jdbc.queryForList("SELECT priority_group FROM booking_priority WHERE email=?",String.class,target);
  if(old.isEmpty()){jdbc.update("INSERT INTO booking_priority(email,priority_group,updated_by,updated_at) VALUES (?,?,?,?)",target,entry.group(),admin,LocalDateTime.now(clock));return "IMPORTED";}
  if(old.getFirst().equals(entry.group()))return "SKIPPED";
  jdbc.update("UPDATE booking_priority SET priority_group=?,updated_by=?,updated_at=? WHERE email=?",entry.group(),admin,LocalDateTime.now(clock),target);return "UPDATED";
 }
 @Transactional public Map<String,String> save(PriorityContracts.Entry input,long admin){lock();saveEntry(input,admin);return Map.of("email",email(input.email()),"group",input.group());}
 @Transactional public Map<String,String> remove(String value){lock();String target=email(value);jdbc.update("DELETE FROM booking_priority WHERE email=?",target);return Map.of("email",target,"group","REGULAR");}
 public record ImportResult(int totalRows,int imported,int updated,int skipped,int failed,List<PriorityWorkbook.RowError> errors){}
 @Transactional public ImportResult upload(PriorityWorkbook.Parsed input,long admin){
  lock();int imported=0,updated=0,skipped=0;var errors=new ArrayList<>(input.errors());var seen=new HashMap<String,String>();
  for(var row:input.entries()){
   var previous=seen.putIfAbsent(row.email(),row.group());
   if(previous!=null&&!previous.equals(row.group())){errors.add(new PriorityWorkbook.RowError(row.row(),row.email(),"Email bị lặp với nhóm ưu tiên khác trong tệp."));continue;}
   String result=saveEntry(new PriorityContracts.Entry(row.email(),row.group()),admin);
   switch(result){case "IMPORTED"->imported++;case "UPDATED"->updated++;default->skipped++;}
  }
  errors.sort(Comparator.comparingInt(PriorityWorkbook.RowError::row));return new ImportResult(input.totalRows(),imported,updated,skipped,errors.size(),errors);
 }
}

