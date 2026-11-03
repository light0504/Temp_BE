package vn.fbus.wishlist;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.fbus.common.*;
import vn.fbus.planning.*;
import java.time.*;
import java.util.*;
import static vn.fbus.common.ApiException.require;

@Service
public class WishlistService {
    private final JdbcTemplate jdbc; private final Database db; private final TravelSchedule schedule; private final Clock clock;
    public WishlistService(JdbcTemplate jdbc,Database db,TravelSchedule schedule,Clock clock) { this.jdbc=jdbc;this.db=db;this.schedule=schedule;this.clock=clock; }
    private static final String VIEW="""
        SELECT w.*,r.name AS route_name,bs.name AS boarding_stop,als.name AS alighting_stop,u.full_name,u.email
        FROM trip_wishlist w JOIN route r ON r.id=w.route_id JOIN stop bs ON bs.id=w.boarding_stop_id
        JOIN stop als ON als.id=w.alighting_stop_id JOIN app_user u ON u.id=w.user_id
        """;
    private Map<String,Object> map(java.sql.ResultSet rs,boolean admin) throws java.sql.SQLException {
        var row=new LinkedHashMap<String,Object>();
        row.put("wishlistId",rs.getLong("id"));row.put("routeId",rs.getLong("route_id"));row.put("routeName",rs.getString("route_name"));
        row.put("boardingStopId",rs.getLong("boarding_stop_id"));row.put("boardingStop",rs.getString("boarding_stop"));
        row.put("alightingStopId",rs.getLong("alighting_stop_id"));row.put("alightingStop",rs.getString("alighting_stop"));
        row.put("date",rs.getObject("travel_date",LocalDate.class));row.put("slot",rs.getInt("slot"));row.put("status",rs.getString("status"));
        row.put("createdAt",rs.getObject("created_at",LocalDateTime.class));
        if(admin) {row.put("userId",rs.getLong("user_id"));row.put("fullName",rs.getString("full_name"));row.put("email",rs.getString("email"));}
        return row;
    }
    public List<Map<String,Object>> my(long user) { return jdbc.query(VIEW+" WHERE w.user_id=? ORDER BY w.travel_date DESC,w.slot,w.id",(rs,n)->map(rs,false),user); }
    public List<Map<String,Object>> admin(LocalDate date,int slot) {
        require(slot>=1 && slot<=6,400,"Slot phải từ 1 đến 6.");
        return jdbc.query(VIEW+" WHERE w.travel_date=? AND w.slot=? AND w.status='ACTIVE' ORDER BY w.created_at,w.id",(rs,n)->map(rs,true),date,slot);
    }
    private Map<String,Object> one(long user,long id) { return jdbc.query(VIEW+" WHERE w.id=? AND w.user_id=?",(rs,n)->map(rs,false),id,user).stream().findFirst().orElseThrow(()->new ApiException(404,"Không tìm thấy wishlist.")); }
    public record JoinOutcome(Long wishlistId,boolean created,String note) {}
    @Transactional
    public Map<String,Object> join(long user,ScheduleContracts.WishlistInput input) {
        var outcome=attempt(user,input);
        require(outcome.note().isEmpty(),409,outcome.note());return one(user,outcome.wishlistId());
    }
    @Transactional
    public JoinOutcome joinForPlan(long user,ScheduleContracts.WishlistInput input) { return attempt(user,input); }
    private JoinOutcome attempt(long user,ScheduleContracts.WishlistInput input) {
        require(!input.date().isBefore(LocalDate.now(clock)),400,"Không đăng ký wishlist cho ngày đã qua.");
        schedule.lockUser(user); schedule.interval(input.routeId(),input.boardingStopId(),input.alightingStopId());
        var previous=jdbc.queryForList("SELECT id FROM trip_wishlist WHERE user_id=? AND route_id=? AND travel_date=? AND slot=?",Long.class,user,input.routeId(),input.date(),input.slot());
        if(!previous.isEmpty()) {
            var old=one(user,previous.getFirst());
            if(old.get("status").equals("ACTIVE")) {
                if(!((Long)old.get("boardingStopId")).equals(input.boardingStopId()) || !((Long)old.get("alightingStopId")).equals(input.alightingStopId()))return new JoinOutcome(null,false,"Đã có wishlist cho chặng khác trong ngày/slot này. Rút wishlist cũ trước khi đổi chặng.");
                return new JoinOutcome(previous.getFirst(),false,"");
            }
        }
        var rows=schedule.trips(input.routeId(),input.slot(),input.date(),input.date(),true);
        var candidates=schedule.candidates(rows,input.date(),input.boardingStopId(),input.alightingStopId());
        if(rows.stream().anyMatch(trip -> schedule.booked(user,trip.id())))return new JoinOutcome(null,false,"Bạn đã có chỗ trong ngày/slot này.");
        if(candidates.stream().anyMatch(c -> c.available()>0))return new JoinOutcome(null,false,"Slot vẫn còn ghế trống. Vui lòng tìm chuyến để đặt chỗ.");
        long id;
        if(previous.isEmpty()) id=db.insert("INSERT INTO trip_wishlist(user_id,route_id,boarding_stop_id,alighting_stop_id,travel_date,slot,created_at) VALUES (?,?,?,?,?,?,?)",user,input.routeId(),input.boardingStopId(),input.alightingStopId(),input.date(),input.slot(),LocalDateTime.now(clock));
        else {id=previous.getFirst();jdbc.update("UPDATE trip_wishlist SET status='ACTIVE',boarding_stop_id=?,alighting_stop_id=?,created_at=? WHERE id=?",input.boardingStopId(),input.alightingStopId(),LocalDateTime.now(clock),id);}
        return new JoinOutcome(id,true,"");
    }
    @Transactional
    public Map<String,Object> cancel(long user,long id) {
        schedule.lockUser(user);one(user,id);jdbc.update("UPDATE trip_wishlist SET status='CANCELLED' WHERE id=? AND user_id=? AND status='ACTIVE'",id,user);return one(user,id);
    }
}
