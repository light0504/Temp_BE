CREATE TABLE email_access (
  email VARCHAR(254) PRIMARY KEY,
  full_name VARCHAR(150) NOT NULL DEFAULT '',
  role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT','LECTURER','DRIVER','BUS_ATTENDANT')),
  imported_by BIGINT REFERENCES app_user(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Preserve access for accounts that were explicitly created before this feature.
INSERT INTO email_access(email,full_name,role)
SELECT LOWER(TRIM(email)),full_name,role FROM app_user WHERE role <> 'ADMIN';
INSERT INTO operation_lock(name) VALUES ('email-access');
