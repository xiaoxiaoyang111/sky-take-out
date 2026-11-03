-- Additive member features; existing customers, equipment and orders are preserved.
CREATE TABLE IF NOT EXISTS rental_favorite (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 customer_id BIGINT NOT NULL,
 equipment_id BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_rental_favorite_customer_equipment (customer_id,equipment_id),
 CONSTRAINT fk_rental_favorite_customer FOREIGN KEY (customer_id) REFERENCES rental_customer(id) ON DELETE CASCADE,
 CONSTRAINT fk_rental_favorite_equipment FOREIGN KEY (equipment_id) REFERENCES rental_equipment(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rental_review (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 customer_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 rating TINYINT NOT NULL,
 content VARCHAR(500) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_rental_review_order (order_id),
 KEY idx_rental_review_customer (customer_id,id),
 CONSTRAINT fk_rental_review_customer FOREIGN KEY (customer_id) REFERENCES rental_customer(id) ON DELETE CASCADE,
 CONSTRAINT fk_rental_review_order FOREIGN KEY (order_id) REFERENCES rental_order(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
