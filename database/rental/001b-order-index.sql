-- Upgrade installations initialized before the equipment occupancy index was added.
SET @rental_index_exists = (SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'rental_order' AND index_name = 'idx_rental_order_equipment_dates');
SET @rental_index_sql = IF(@rental_index_exists = 0,
 'ALTER TABLE rental_order ADD INDEX idx_rental_order_equipment_dates (equipment_id,status,start_date,end_date)',
 'SELECT 1');
PREPARE rental_index_statement FROM @rental_index_sql;
EXECUTE rental_index_statement;
DEALLOCATE PREPARE rental_index_statement;
