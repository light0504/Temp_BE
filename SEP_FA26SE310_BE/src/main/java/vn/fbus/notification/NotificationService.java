package vn.fbus.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service
public class NotificationService {
    public record Notice(long notificationId,String message,LocalDateTime createdAt,long tripId,long semesterId,LocalDateTime readAt) {}
    public record Unread(long unreadCount) {}
    public record Version(@Positive long notificationId,@NotNull LocalDateTime createdAt) {}
    public record ReadBatch(@NotNull @Size(min=1,max=100) List<@NotNull @Valid Version> notifications) {}
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public NotificationService(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}

    public List<Notice> list(long user,Long semester) {
        return jdbc.query("""
            SELECT n.*,t.semester_id FROM user_notification n JOIN trip t ON t.id=n.trip_id
            WHERE n.user_id=? AND (CAST(? AS BIGINT) IS NULL OR t.semester_id=?)
            ORDER BY CASE WHEN n.read_at IS NULL THEN 0 ELSE 1 END,n.created_at DESC,n.id DESC LIMIT 100
            """,(r,n)->new Notice(r.getLong("id"),r.getString("message"),r.getObject("created_at",LocalDateTime.class),
                r.getLong("trip_id"),r.getLong("semester_id"),r.getObject("read_at",LocalDateTime.class)),user,semester,semester);
    }
    public Unread unread(long user) {
        return new Unread(jdbc.queryForObject("SELECT COUNT(*) FROM user_notification WHERE user_id=? AND read_at IS NULL",Long.class,user));
    }
    @Transactional
    public Unread markRead(long user,ReadBatch batch) {
        var now=LocalDateTime.now(clock);
        for(var item:batch.notifications()) {
            // An allocation notice can be replaced by a cancellation notice for the same trip.
            // Only acknowledge the exact version the passenger actually saw.
            jdbc.update("UPDATE user_notification SET read_at=? WHERE id=? AND user_id=? AND created_at=? AND read_at IS NULL",
                now,item.notificationId(),user,item.createdAt());
        }
        return unread(user);
    }
}
