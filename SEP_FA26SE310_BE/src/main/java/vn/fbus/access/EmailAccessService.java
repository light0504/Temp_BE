package vn.fbus.access;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static vn.fbus.common.ApiException.require;

@Service
public class EmailAccessService {
    public record ImportResult(int totalRows, int imported, int skipped, int failed, List<EmailWorkbook.RowError> errors) {}
    private final JdbcTemplate jdbc;
    public EmailAccessService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Map<String,Object> list(int page, int size) {
        require(page>=0 && page<=100000 && size>=1 && size<=100,400,"Phân trang không hợp lệ.");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM email_access",Long.class);
        var content=jdbc.query("SELECT e.*,a.id AS user_id FROM email_access e LEFT JOIN app_user a ON LOWER(a.email)=e.email ORDER BY e.created_at DESC,e.email LIMIT ? OFFSET ?",(rs,n) -> {
            var row=new LinkedHashMap<String,Object>();
            row.put("email",rs.getString("email")); row.put("fullName",rs.getString("full_name")); row.put("role",rs.getString("role"));
            row.put("createdAt",rs.getObject("created_at",java.time.OffsetDateTime.class).toString()); row.put("userId",rs.getObject("user_id",Long.class));
            return row;
        },size,page*size);
        return Map.of("content",content,"page",Map.of("number",page,"size",size,"totalElements",total,"totalPages",(total+size-1)/size));
    }
    @Transactional
    public ImportResult save(EmailWorkbook.Parsed parsed, long adminId) {
        jdbc.queryForList("SELECT name FROM operation_lock WHERE name='email-access' FOR UPDATE");
        var known=new HashMap<String,String>();
        jdbc.query("SELECT email,role FROM email_access",(org.springframework.jdbc.core.RowCallbackHandler) rs -> known.put(rs.getString("email"),rs.getString("role")));
        var users=new HashMap<String,String>();
        jdbc.query("SELECT email,role FROM app_user",(org.springframework.jdbc.core.RowCallbackHandler) rs -> users.put(rs.getString("email").toLowerCase(Locale.ROOT),rs.getString("role")));
        var errors=new ArrayList<>(parsed.errors()); int imported=0, skipped=0;
        for (var entry : parsed.entries()) {
            String existing=known.get(entry.email()), userRole=users.get(entry.email());
            if (userRole!=null && !userRole.equals(entry.role()) || existing!=null && !existing.equals(entry.role())) {
                errors.add(new EmailWorkbook.RowError(entry.row(),entry.email(),"Email đã có vai trò khác; không thay đổi quyền.")); continue;
            }
            if (existing!=null) { skipped++; continue; }
            jdbc.update("INSERT INTO email_access(email,full_name,role,imported_by) VALUES (?,?,?,?)",entry.email(),entry.fullName(),entry.role(),adminId);
            known.put(entry.email(),entry.role()); imported++;
        }
        errors.sort(Comparator.comparingInt(EmailWorkbook.RowError::row));
        return new ImportResult(parsed.totalRows(),imported,skipped,errors.size(),errors);
    }
}
