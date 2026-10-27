-- Fictional demo catalog/rates, no accounts/passwords. Repeat execution does not overwrite existing rows.
INSERT IGNORE INTO rental_category(id,name,sort,status) VALUES
(1,'相机',10,1),(2,'镜头',20,1),(3,'灯光',30,1),(4,'配件',40,1);
INSERT IGNORE INTO rental_equipment(id,category_id,name,brand,description,image,daily_rate,deposit,status) VALUES
(1,1,'全画幅微单 · 创作套机','SONY','适合校园短片、活动记录与日常创作。套装含机身、标准变焦镜头、电池及充电器；领取时核验具体配置。','/illustrations/camera.svg',129.00,2000.00,1),
(2,2,'标准变焦镜头 · 24–70','SIGMA','覆盖人像与活动拍摄的常用焦段。下单前请核对机身卡口；门店可协助确认兼容性。','/illustrations/lens.svg',79.00,1500.00,1),
(3,3,'双色温补光灯 · 轻量套装','GODOX','适合室内访谈、产品摄影和直播布光，附灯架与柔光附件。','/illustrations/light.svg',49.00,500.00,1),
(4,4,'便携三脚架 · 稳定支撑','BENRO','适合校园活动、固定机位和延时摄影，轻便易携；请在现场确认设备承重。','/illustrations/tripod.svg',29.00,300.00,1),
(5,1,'便携微单 · 旅行套机','CANON','轻装出发的拍摄组合，适合社团出游与日常影像记录。','/illustrations/camera.svg',99.00,1500.00,1),
(6,2,'人像定焦镜头 · 50mm','SONY','适合人物特写与生活记录。租赁前确认卡口和使用需求。','/illustrations/lens.svg',39.00,800.00,1);
INSERT IGNORE INTO rental_asset(id,equipment_id,asset_code,status) VALUES
(1,1,'GY-CAM-001','READY'),(2,1,'GY-CAM-002','READY'),(3,2,'GY-LENS-001','READY'),
(4,3,'GY-LIGHT-001','READY'),(5,3,'GY-LIGHT-002','READY'),(6,4,'GY-TRIPOD-001','READY'),
(7,5,'GY-CAM-003','READY'),(8,6,'GY-LENS-002','READY');
