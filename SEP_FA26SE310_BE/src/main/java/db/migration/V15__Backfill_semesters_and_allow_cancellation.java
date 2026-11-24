package db.migration;
import org.flywaydb.core.api.migration.*;
import java.time.*;
import java.util.*;
public class V15__Backfill_semesters_and_allow_cancellation extends BaseJavaMigration {
 @Override public void migrate(Context context)throws Exception{
  var c=context.getConnection();var years=new TreeSet<Integer>();
  years.add(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear());
  for(String field:List.of("trip:departure_at","trip_wishlist:travel_date","booking_plan:start_date","trip_recurrence:start_date")){
   var parts=field.split(":");try(var s=c.createStatement();var r=s.executeQuery("SELECT DISTINCT EXTRACT(YEAR FROM "+parts[1]+") FROM "+parts[0])){while(r.next())years.add(r.getInt(1));}
  }
  for(int year:years)for(int i=0;i<3;i++){
   var start=LocalDate.of(year,1+i*4,1);var end=start.plusMonths(4).minusDays(1);String term=List.of("SPRING","SUMMER","FALL").get(i);
   try(var q=c.prepareStatement("INSERT INTO semester(term,academic_year,name,start_date,end_date) VALUES (?,?,?,?,?)")){
    q.setString(1,term);q.setInt(2,year);q.setString(3,term.charAt(0)+term.substring(1).toLowerCase(Locale.ROOT)+" "+year);
    q.setObject(4,start);q.setObject(5,end);q.executeUpdate();
   }
  }
  try(var s=c.createStatement()){
   s.executeUpdate("UPDATE trip SET semester_id=(SELECT id FROM semester s WHERE CAST(trip.departure_at AS DATE) BETWEEN s.start_date AND s.end_date)");
   s.executeUpdate("UPDATE trip_wishlist SET semester_id=(SELECT id FROM semester s WHERE trip_wishlist.travel_date BETWEEN s.start_date AND s.end_date)");
   s.executeUpdate("UPDATE booking_plan SET semester_id=(SELECT id FROM semester s WHERE booking_plan.start_date BETWEEN s.start_date AND s.end_date)");
   s.executeUpdate("UPDATE trip_recurrence SET semester_id=(SELECT id FROM semester s WHERE trip_recurrence.start_date BETWEEN s.start_date AND s.end_date)");
   s.executeUpdate("ALTER TABLE trip ALTER COLUMN semester_id SET NOT NULL");
   s.executeUpdate("ALTER TABLE trip_wishlist ALTER COLUMN semester_id SET NOT NULL");
   s.executeUpdate("ALTER TABLE booking_plan ALTER COLUMN semester_id SET NOT NULL");
   s.executeUpdate("ALTER TABLE trip_recurrence ALTER COLUMN semester_id SET NOT NULL");
  }
  var checks=new ArrayList<String>();
  try(var q=c.prepareStatement("""
   SELECT tc.constraint_name FROM information_schema.table_constraints tc
   JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
   WHERE tc.table_schema=? AND tc.table_name='trip' AND tc.constraint_type='CHECK'
    AND UPPER(cc.check_clause) LIKE '%SCHEDULED%'
   """)){q.setString(1,c.getSchema());try(var r=q.executeQuery()){while(r.next())checks.add(r.getString(1));}}
  try(var s=c.createStatement()){
   for(String name:checks)s.executeUpdate("ALTER TABLE trip DROP CONSTRAINT \""+name.replace("\"","\"\"")+"\"");
   s.executeUpdate("ALTER TABLE trip ADD CONSTRAINT chk_trip_lifecycle CHECK (status IN ('SCHEDULED','PAUSED','CANCELLED'))");
  }
 }
}