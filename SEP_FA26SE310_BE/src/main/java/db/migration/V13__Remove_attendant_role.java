package db.migration;
import org.flywaydb.core.api.migration.*;
import java.sql.*;
import java.util.*;
public class V13__Remove_attendant_role extends BaseJavaMigration {
 @Override public void migrate(Context context)throws Exception{
  var c=context.getConnection();String ids="SELECT id FROM app_user WHERE role='BUS_ATTENDANT'";
  for(String ref:List.of("booking:user_id","booking_plan:user_id","trip_wishlist:user_id","trip:driver_id","vehicle:driver_id","trip_recurrence:driver_id","trip_recurrence:created_by","booking_priority:updated_by")){
   var p=ref.split(":");try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+p[0]+" WHERE "+p[1]+" IN ("+ids+")")){r.next();if(r.getInt(1)>0)throw new SQLException("Reassign non-attendant references in "+p[0]+" before retiring accounts.");}
  }
  try(var s=c.createStatement()){
   s.executeUpdate("DELETE FROM crew_event WHERE user_id IN ("+ids+")");
   s.executeUpdate("DELETE FROM trip_report WHERE reporter_id IN ("+ids+")");
   s.executeUpdate("UPDATE trip_report SET resolved_by=NULL WHERE resolved_by IN ("+ids+")");
   s.executeUpdate("DELETE FROM auth_session WHERE user_id IN ("+ids+")");
   s.executeUpdate("UPDATE email_access SET imported_by=NULL WHERE imported_by IN ("+ids+")");
   s.executeUpdate("DELETE FROM booking_priority WHERE email IN (SELECT LOWER(email) FROM app_user WHERE role='BUS_ATTENDANT')");
   s.executeUpdate("DELETE FROM email_access WHERE role='BUS_ATTENDANT' OR email IN (SELECT LOWER(email) FROM app_user WHERE role='BUS_ATTENDANT')");
   s.executeUpdate("DROP INDEX IF EXISTS idx_trip_attendant");
   s.executeUpdate("ALTER TABLE vehicle DROP COLUMN bus_attendant_id");
   s.executeUpdate("ALTER TABLE trip DROP COLUMN bus_attendant_id");
   s.executeUpdate("ALTER TABLE trip DROP COLUMN bus_attendant");
   s.executeUpdate("ALTER TABLE trip_recurrence DROP COLUMN bus_attendant_id");
   s.executeUpdate("ALTER TABLE trip_recurrence DROP COLUMN bus_attendant");
   s.executeUpdate("DELETE FROM app_user WHERE role='BUS_ATTENDANT'");
  }
  roleCheck(c,"app_user","role IN ('ADMIN','STUDENT','LECTURER','DRIVER')");
  roleCheck(c,"email_access","role IN ('STUDENT','LECTURER','DRIVER')");
 }
 private void roleCheck(Connection c,String table,String clause)throws Exception{
  var names=new ArrayList<String>();
  try(var q=c.prepareStatement("""
   SELECT tc.constraint_name FROM information_schema.table_constraints tc
   JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
   WHERE tc.table_schema=? AND tc.table_name=? AND tc.constraint_type='CHECK'
    AND UPPER(cc.check_clause) LIKE '%BUS_ATTENDANT%'
   """)){q.setString(1,c.getSchema());q.setString(2,table);try(var r=q.executeQuery()){while(r.next())names.add(r.getString(1));}}
  try(var s=c.createStatement()){
   for(var name:names)s.executeUpdate("ALTER TABLE "+table+" DROP CONSTRAINT \""+name.replace("\"","\"\"")+"\"");
   s.executeUpdate("ALTER TABLE "+table+" ADD CONSTRAINT chk_"+table+"_current_role CHECK ("+clause+")");
  }
 }
}