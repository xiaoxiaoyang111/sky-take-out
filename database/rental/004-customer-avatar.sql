-- Separate additive table; existing customer records and credentials stay intact.
CREATE TABLE IF NOT EXISTS rental_customer_avatar (
 customer_id BIGINT NOT NULL PRIMARY KEY,
 avatar_url VARCHAR(255) NOT NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CONSTRAINT fk_rental_customer_avatar_customer FOREIGN KEY (customer_id) REFERENCES rental_customer(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
