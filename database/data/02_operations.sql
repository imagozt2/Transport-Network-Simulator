USE transport_simulator_db;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS seed_numbers;
CREATE TEMPORARY TABLE seed_numbers (n INT PRIMARY KEY);
INSERT INTO seed_numbers (n) VALUES
(1),(2),(3),(4),(5),(6),(7),(8),(9),(10),
(11),(12),(13),(14),(15),(16),(17),(18),(19),(20),
(21),(22),(23),(24),(25),(26),(27),(28),(29),(30),
(31),(32),(33),(34),(35),(36),(37),(38),(39),(40),
(41),(42),(43),(44),(45),(46),(47),(48),(49),(50);

INSERT INTO devices (code, name, device_type, station_id, status, active)
SELECT
    CONCAT('RMM-TM-', stations.code, '-', LPAD(numbers.n, 2, '0')),
    CONCAT('Máquina de compra ', LPAD(numbers.n, 2, '0'), ' - ', stations.name),
    'TICKET_MACHINE', stations.id, 'OFFLINE', TRUE
FROM stations
JOIN seed_numbers numbers ON numbers.n <= CASE
    WHEN stations.name = 'Aeropuerto' THEN 8
    WHEN stations.name IN ('Plaza de la Merced', 'La Galería') THEN 6
    WHEN stations.name IN ('Plaza de la Mina', 'El Reposo', 'Puerta de Santiago',
        'HUB Industrial Norte', 'Puerto Fluvial', 'Gueto Sur') THEN 4
    ELSE 2 END
ON DUPLICATE KEY UPDATE
    name = VALUES(name), station_id = VALUES(station_id), status = VALUES(status), active = VALUES(active);

INSERT INTO devices (code, name, device_type, station_id, status, active)
SELECT
    CONCAT('RMM-EN-', stations.code, '-', LPAD(numbers.n, 2, '0')),
    CONCAT('Validador de entrada ', LPAD(numbers.n, 2, '0'), ' - ', stations.name),
    'ENTRY_VALIDATOR', stations.id, 'OFFLINE', TRUE
FROM stations
JOIN seed_numbers numbers ON numbers.n <= CASE
    WHEN stations.name = 'Aeropuerto' THEN 12
    WHEN stations.name IN ('Plaza de la Mina', 'El Reposo', 'Puerta de Santiago',
        'HUB Industrial Norte', 'Puerto Fluvial', 'Gueto Sur', 'Plaza de la Merced',
        'La Galería', 'Zona Universitaria', 'Estadio Olímpico') THEN 8
    ELSE 4 END
ON DUPLICATE KEY UPDATE
    name = VALUES(name), station_id = VALUES(station_id), status = VALUES(status), active = VALUES(active);

INSERT INTO devices (code, name, device_type, station_id, status, active)
SELECT
    CONCAT('RMM-EX-', stations.code, '-', LPAD(numbers.n, 2, '0')),
    CONCAT('Validador de salida ', LPAD(numbers.n, 2, '0'), ' - ', stations.name),
    'EXIT_VALIDATOR', stations.id, 'OFFLINE', TRUE
FROM stations
JOIN seed_numbers numbers ON numbers.n <= CASE
    WHEN stations.name = 'Aeropuerto' THEN 12
    WHEN stations.name IN ('Plaza de la Mina', 'El Reposo', 'Puerta de Santiago',
        'HUB Industrial Norte', 'Puerto Fluvial', 'Gueto Sur', 'Plaza de la Merced',
        'La Galería', 'Zona Universitaria', 'Estadio Olímpico') THEN 8
    ELSE 4 END
ON DUPLICATE KEY UPDATE
    name = VALUES(name), station_id = VALUES(station_id), status = VALUES(status), active = VALUES(active);

INSERT INTO device_mqtt_identities (
    device_id, instance_id, mqtt_client_id, authentication_mode,
    identity_status, valid_from
)
SELECT devices.id, UUID(), devices.code, 'PASSWORD', 'ACTIVE', UTC_TIMESTAMP()
FROM devices
ON DUPLICATE KEY UPDATE
    mqtt_client_id = VALUES(mqtt_client_id);

INSERT INTO train_models (
    manufacturer, model_name, series, car_count, capacity_passengers, max_speed_kmh, active
) VALUES
('Alstom', 'Metropolis', '9000', 5, 760, 100, TRUE),
('Alstom', 'Metropolis', '7000', 5, 720, 90, TRUE),
('Alstom', 'Metropolis', '6000', 5, 680, 90, TRUE),
('RMM', 'Clásico', '3000 Histórica', 4, 520, 75, TRUE)
ON DUPLICATE KEY UPDATE
    car_count = VALUES(car_count), capacity_passengers = VALUES(capacity_passengers),
    max_speed_kmh = VALUES(max_speed_kmh), active = VALUES(active);

DROP TEMPORARY TABLE IF EXISTS seed_depots;
CREATE TEMPORARY TABLE seed_depots (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    station_code VARCHAR(20) NOT NULL,
    capacity INT NOT NULL,
    track_count INT NOT NULL,
    trains_per_track INT NOT NULL
);

INSERT INTO seed_depots VALUES
('DEP-LF-A','Cochera de Las Fuentes - Sector A','ST023',20,4,5),
('DEP-LF-B','Cochera de Las Fuentes - Sector B','ST023',20,4,5),
('DEP-CC-A','Cochera de Cuatro Caminos - Sector A','ST043',20,4,5),
('DEP-CC-B','Cochera de Cuatro Caminos - Sector B','ST043',20,4,5),
('DEP-AIR-A','Cochera de Aeropuerto - Sector A','ST001',30,6,5),
('DEP-AIR-B','Cochera de Aeropuerto - Sector B','ST001',30,6,5),
('DEP-HUB-E','Cochera de HUB Industrial Este','ST049',20,4,5),
('DEP-HUB-W','Cochera de HUB Industrial Oeste','ST050',20,4,5),
('DEP-PO','Cochera de Puerto Olímpico','ST048',20,4,5),
('DEP-ESP','Cochera de El Espigón','ST046',20,4,5),
('DEP-MC','Cochera de Miguel de Cervantes','ST006',20,4,5),
('DEP-MI','Cochera de Muralla Ibérica','ST031',20,4,5);

INSERT INTO depots (code, name, station_id, capacity, track_count, trains_per_track, active)
SELECT seed.code, seed.name, stations.id, seed.capacity, seed.track_count, seed.trains_per_track, TRUE
FROM seed_depots seed
JOIN stations ON stations.code = seed.station_code
ON DUPLICATE KEY UPDATE
    name = VALUES(name), station_id = VALUES(station_id), capacity = VALUES(capacity),
    track_count = VALUES(track_count), trains_per_track = VALUES(trains_per_track), active = VALUES(active);

DROP TEMPORARY TABLE IF EXISTS seed_train_distribution;
CREATE TEMPORARY TABLE seed_train_distribution (
    line_code VARCHAR(20) NOT NULL,
    depot_code VARCHAR(30) NOT NULL,
    model_series VARCHAR(100) NOT NULL,
    train_count INT NOT NULL,
    code_prefix VARCHAR(40) NOT NULL,
    fleet_role VARCHAR(30) NOT NULL
);

INSERT INTO seed_train_distribution VALUES
('L1','DEP-LF-A','9000',19,'RMM-L1-9000-LFA','REGULAR_SERVICE'),
('L1','DEP-CC-A','9000',19,'RMM-L1-9000-CCA','REGULAR_SERVICE'),
('L2','DEP-LF-B','9000',20,'RMM-L2-9000-LFB','REGULAR_SERVICE'),
('L2','DEP-AIR-A','9000',26,'RMM-L2-9000-AIRA','REGULAR_SERVICE'),
('L3','DEP-PO','9000',16,'RMM-L3-9000-PO','REGULAR_SERVICE'),
('L3','DEP-HUB-E','9000',16,'RMM-L3-9000-HUBE','REGULAR_SERVICE'),
('L4','DEP-AIR-B','9000',21,'RMM-L4-9000-AIRB','REGULAR_SERVICE'),
('L4','DEP-MI','9000',20,'RMM-L4-9000-MI','REGULAR_SERVICE'),
('L5','DEP-HUB-W','9000',20,'RMM-L5-9000-HUBW','REGULAR_SERVICE'),
('L5','DEP-CC-B','9000',19,'RMM-L5-9000-CCB','REGULAR_SERVICE'),
('L6','DEP-ESP','9000',17,'RMM-L6-9000-ESP','REGULAR_SERVICE'),
('L6','DEP-MC','9000',17,'RMM-L6-9000-MC','REGULAR_SERVICE'),
('L1','DEP-LF-A','7000',1,'RMM-HIST-7000-LFA','RESERVE'),
('L1','DEP-CC-A','7000',1,'RMM-HIST-7000-CCA','RESERVE'),
('L2','DEP-AIR-A','7000',3,'RMM-HIST-7000-AIRA','RESERVE'),
('L3','DEP-PO','6000',1,'RMM-HIST-6000-PO','HISTORIC'),
('L3','DEP-HUB-E','6000',1,'RMM-HIST-6000-HUBE','HISTORIC'),
('L4','DEP-AIR-B','6000',3,'RMM-HIST-6000-AIRB','HISTORIC'),
('L5','DEP-CC-B','3000 Histórica',1,'RMM-HIST-3000-CCB','HISTORIC'),
('L6','DEP-ESP','3000 Histórica',1,'RMM-HIST-3000-ESP','HISTORIC');

INSERT INTO trains (
    code, train_model_id, home_depot_id, assigned_line_id, current_depot_id,
    fleet_role, dispatch_order, status, active
)
SELECT
    CONCAT(distribution.code_prefix, '-', LPAD(numbers.n, 3, '0')),
    models.id, depots.id, transport_line.id, depots.id, distribution.fleet_role,
    CASE WHEN distribution.fleet_role = 'REGULAR_SERVICE' THEN numbers.n ELSE NULL END,
    'DEPOT', TRUE
FROM seed_train_distribution distribution
JOIN seed_numbers numbers ON numbers.n <= distribution.train_count
JOIN train_models models ON models.series = distribution.model_series
JOIN depots ON depots.code = distribution.depot_code
JOIN transport_lines transport_line ON transport_line.code = distribution.line_code
ON DUPLICATE KEY UPDATE
    train_model_id = VALUES(train_model_id), home_depot_id = VALUES(home_depot_id),
    assigned_line_id = VALUES(assigned_line_id), current_depot_id = VALUES(current_depot_id),
    fleet_role = VALUES(fleet_role), dispatch_order = VALUES(dispatch_order),
    status = VALUES(status), active = VALUES(active);

DROP TEMPORARY TABLE seed_train_distribution;
DROP TEMPORARY TABLE seed_depots;
DROP TEMPORARY TABLE seed_numbers;
