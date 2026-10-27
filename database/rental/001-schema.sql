-- Additive, repeatable schema. Does not alter the original takeaway tables.
CREATE TABLE IF NOT EXISTS rental_customer (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(32) NOT NULL,
 password_hash VARCHAR(100) NOT NULL,
 display_name VARCHAR(32) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_rental_customer_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rental_category (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(50) NOT NULL,
 sort INT NOT NULL DEFAULT 0,
 status TINYINT NOT NULL DEFAULT 1,
 UNIQUE KEY uk_rental_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rental_equipment (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 category_id BIGINT NOT NULL,
 name VARCHAR(100) NOT NULL,
 brand VARCHAR(50) NOT NULL DEFAULT '',
 description VARCHAR(2000) NOT NULL DEFAULT '',
 image VARCHAR(500) NOT NULL DEFAULT '',
 daily_rate DECIMAL(10,2) NOT NULL,
 deposit DECIMAL(10,2) NOT NULL DEFAULT 0,
 status TINYINT NOT NULL DEFAULT 1,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_user BIGINT NULL,
 update_user BIGINT NULL,
 KEY idx_rental_equipment_category (category_id,status),
 CONSTRAINT fk_rental_equipment_category FOREIGN KEY (category_id) REFERENCES rental_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rental_asset (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 equipment_id BIGINT NOT NULL,
 asset_code VARCHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'READY',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_rental_asset_code (asset_code),
 KEY idx_rental_asset_equipment (equipment_id,status),
 CONSTRAINT fk_rental_asset_equipment FOREIGN KEY (equipment_id) REFERENCES rental_equipment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rental_order (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_no VARCHAR(64) NOT NULL,
 customer_id BIGINT NOT NULL,
 equipment_id BIGINT NOT NULL,
 asset_id BIGINT NOT NULL,
 equipment_name VARCHAR(100) NOT NULL,
 asset_code VARCHAR(64) NOT NULL,
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 rental_days INT NOT NULL,
 daily_rate DECIMAL(10,2) NOT NULL,
 rental_amount DECIMAL(12,2) NOT NULL,
 deposit_amount DECIMAL(10,2) NOT NULL,
 status VARCHAR(20) NOT NULL,
 request_id VARCHAR(64) NOT NULL,
 remark VARCHAR(500) NOT NULL DEFAULT '',
 return_note VARCHAR(500) NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 confirmed_at DATETIME NULL,
 picked_up_at DATETIME NULL,
 returned_at DATETIME NULL,
 UNIQUE KEY uk_rental_order_no (order_no),
 UNIQUE KEY uk_rental_order_request (customer_id,request_id),
 KEY idx_rental_order_asset_dates (asset_id,status,start_date,end_date),
 KEY idx_rental_order_equipment_dates (equipment_id,status,start_date,end_date),
 KEY idx_rental_order_user (customer_id,created_at,id),
 KEY idx_rental_order_status (status,created_at,id),
 CONSTRAINT fk_rental_order_customer FOREIGN KEY (customer_id) REFERENCES rental_customer(id),
 CONSTRAINT fk_rental_order_equipment FOREIGN KEY (equipment_id) REFERENCES rental_equipment(id),
 CONSTRAINT fk_rental_order_asset FOREIGN KEY (asset_id) REFERENCES rental_asset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rental_order_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 actor_role VARCHAR(10) NOT NULL,
 actor_id BIGINT NOT NULL,
 action VARCHAR(30) NOT NULL,
 note VARCHAR(500) NOT NULL DEFAULT '',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_rental_event_order (order_id,id),
 CONSTRAINT fk_rental_event_order FOREIGN KEY (order_id) REFERENCES rental_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
