package vn.fbus.wishlist;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import vn.fbus.common.ApiResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static vn.fbus.common.ApiException.require;

@RestController @RequestMapping("/api/admin")
public class DemandController {
    private final JdbcTemplate jdbc;private final WishlistService wishlists;
    public DemandController(JdbcTemplate jdbc,WishlistService wishlists){this.jdbc=jdbc;this.wishlists=wishlists;}
    @GetMapping("/wishlists") public ApiResponse<?> details(@RequestParam LocalDate date,@RequestParam int slot,@RequestParam(required=false) Long semesterId){return ApiResponse.ok(wishlists.admin(date,slot).stream().filter(row->semesterId==null||semesterId.equals(row.get("semesterId"))).toList());}
    @GetMapping("/demand") public ApiResponse<?> demand(@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false) Long semesterId){
        require(!to.isBefore(from) && ChronoUnit.DAYS.between(from,to)<93,400,"Chọn khoảng ngày tối đa 93 ngày.");
        var rows=new LinkedHashMap<String,Map<String,Object>>();
        for(var date=from;!date.isAfter(to);date=date.plusDays(1))for(int slot=1;slot<=6;slot++) {
            var row=new LinkedHashMap<String,Object>();row.put("date",date.toString());row.put("slot",slot);row.put("tripCount",0L);row.put("passengerCount",0L);row.put("bookingCount",0L);row.put("wishlistCount",0L);rows.put(date+"/"+slot,row);
        }
        jdbc.query("SELECT CAST(departure_at AS DATE) AS demand_date,slot,COUNT(*) AS total FROM trip WHERE departure_at>=? AND departure_at<? AND (CAST(? AS BIGINT) IS NULL OR semester_id=?) GROUP BY CAST(departure_at AS DATE),slot",rs->{rows.get(rs.getObject("demand_date",LocalDate.class)+"/"+rs.getInt("slot")).put("tripCount",rs.getLong("total"));},from.atStartOfDay(),to.plusDays(1).atStartOfDay(),semesterId,semesterId);
        jdbc.query("SELECT CAST(t.departure_at AS DATE) AS demand_date,t.slot,COUNT(*) AS total,COUNT(DISTINCT b.user_id) + COUNT(DISTINCT b.walk_in_student_code) AS people FROM booking b JOIN trip t ON t.id=b.trip_id WHERE t.departure_at>=? AND t.departure_at<? AND b.status IN ('RESERVED','CHECKED_IN') AND (CAST(? AS BIGINT) IS NULL OR t.semester_id=?) GROUP BY CAST(t.departure_at AS DATE),t.slot",rs->{var row=rows.get(rs.getObject("demand_date",LocalDate.class)+"/"+rs.getInt("slot"));row.put("passengerCount",rs.getLong("people"));row.put("bookingCount",rs.getLong("total"));},from.atStartOfDay(),to.plusDays(1).atStartOfDay(),semesterId,semesterId);
        jdbc.query("SELECT travel_date,slot,COUNT(*) AS total FROM trip_wishlist WHERE travel_date>=? AND travel_date<=? AND status='ACTIVE' AND (CAST(? AS BIGINT) IS NULL OR semester_id=?) GROUP BY travel_date,slot",rs->{rows.get(rs.getObject("travel_date",LocalDate.class)+"/"+rs.getInt("slot")).put("wishlistCount",rs.getLong("total"));},from,to,semesterId,semesterId);
        return ApiResponse.ok(rows.values());
    }
}
