package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.ArrayList;
import java.util.List;

public class V11__Relative_booking_opening_and_week_recurrence extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var constraints = new ArrayList<String>();
        try (var query = connection.prepareStatement("""
            SELECT tc.constraint_name FROM information_schema.table_constraints tc
            JOIN information_schema.check_constraints cc
              ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
            WHERE tc.table_schema=? AND tc.table_name='trip_recurrence' AND tc.constraint_type='CHECK'
              AND LOWER(cc.check_clause) LIKE '%end_type%' AND LOWER(cc.check_clause) LIKE '%count%'
            """)) {
            query.setString(1, connection.getSchema());
            try (var rows = query.executeQuery()) { while (rows.next()) constraints.add(rows.getString(1)); }
        }
        try (var statement = connection.createStatement()) {
            for (String name : constraints) {
                statement.executeUpdate("ALTER TABLE trip_recurrence DROP CONSTRAINT " + statement.enquoteIdentifier(name, true));
            }
            statement.executeUpdate("ALTER TABLE trip_recurrence ADD CONSTRAINT chk_recurrence_end_type CHECK (end_type IN ('NEVER','UNTIL','COUNT','WEEKS'))");
            statement.executeUpdate("ALTER TABLE trip_recurrence ADD COLUMN week_limit INTEGER CHECK (week_limit BETWEEN 1 AND 1000)");
            for (String table : List.of("trip", "trip_recurrence")) {
                for (String column : List.of("staff_booking_lead_hours", "priority_student_booking_lead_hours", "regular_booking_lead_hours")) {
                    statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " INTEGER CHECK (" + column + " BETWEEN 1 AND 8783)");
                }
            }
        }
    }
}
