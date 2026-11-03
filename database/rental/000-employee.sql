-- Compatibility table required for staff login on a fresh rental database.
-- CREATE IF NOT EXISTS preserves an existing takeaway employee table.
CREATE TABLE IF NOT EXISTS employee (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(32) NOT NULL,
 username VARCHAR(32) NOT NULL,
 password VARCHAR(100) NOT NULL,
 phone VARCHAR(11) NOT NULL DEFAULT '',
 sex VARCHAR(2) NOT NULL DEFAULT '',
 id_number VARCHAR(18) NOT NULL DEFAULT '',
 status INT NOT NULL DEFAULT 1,
 create_time DATETIME,
 update_time DATETIME,
 create_user BIGINT,
 update_user BIGINT,
 UNIQUE KEY uk_employee_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
