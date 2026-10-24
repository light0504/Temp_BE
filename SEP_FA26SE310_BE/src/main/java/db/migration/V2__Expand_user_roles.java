package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.util.ArrayList;

public class V2__Expand_user_roles extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var constraints = new ArrayList<String>();
        try (var query = connection.prepareStatement("""
            SELECT tc.constraint_name FROM information_schema.table_constraints tc
            JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
            WHERE tc.table_schema=? AND tc.table_name='app_user' AND tc.constraint_type='CHECK'
              AND LOWER(cc.check_clause) LIKE '%role%' AND LOWER(cc.check_clause) NOT LIKE '%is not null%'
            """)) {
            query.setString(1, connection.getSchema());
            try (var rows = query.executeQuery()) { while (rows.next()) constraints.add(rows.getString(1)); }
        }
        try (var statement = connection.createStatement()) {
            for (String name : constraints) statement.executeUpdate("ALTER TABLE app_user DROP CONSTRAINT \""+name.replace("\"","\"\"")+"\"");
            statement.executeUpdate("ALTER TABLE app_user ADD CONSTRAINT chk_user_role CHECK (role IN ('ADMIN','STUDENT','DRIVER','BUS_ATTENDANT','LECTURER'))");
        }
    }
}
