USE transport_simulator_db;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO stations (code, name, active) VALUES
('ST001', 'Aeropuerto', TRUE),
('ST002', 'HUB Industrial Norte', TRUE),
('ST003', 'Ensanche Nuevo', TRUE),
('ST004', 'Ramón y Cajal', TRUE),
('ST005', 'Gueto Sur', TRUE),
('ST006', 'Miguel de Cervantes', TRUE),
('ST007', 'Gueto Oeste', TRUE),
('ST008', 'Gueto Este', TRUE),
('ST009', 'Alfonso X', TRUE),
('ST010', 'Gueto Norte', TRUE),
('ST011', 'Espartales', TRUE),
('ST012', 'El Muro del Gueto', TRUE),
('ST013', 'Las Salinas', TRUE),
('ST014', 'Museo Marítimo', TRUE),
('ST015', 'Paseo Marítimo', TRUE),
('ST016', 'Teatro Nacional', TRUE),
('ST017', 'Estadio Olímpico', TRUE),
('ST018', 'Ribera Sur', TRUE),
('ST019', 'Las Torres', TRUE),
('ST020', 'La Galería', TRUE),
('ST021', 'Puerta Medieval', TRUE),
('ST022', 'El Reposo', TRUE),
('ST023', 'Las Fuentes', TRUE),
('ST024', 'San Vicente', TRUE),
('ST025', 'Santa Rita', TRUE),
('ST026', 'Ribera Norte', TRUE),
('ST027', 'Plaza de la Merced', TRUE),
('ST028', 'Vía Aurea', TRUE),
('ST029', 'Los Lavaderos', TRUE),
('ST030', 'Plaza de la Mina', TRUE),
('ST031', 'Muralla Ibérica', TRUE),
('ST032', 'San Pedro Apóstol', TRUE),
('ST033', 'San Jorge', TRUE),
('ST034', 'Herrería', TRUE),
('ST035', 'Los Conventos', TRUE),
('ST036', 'Complejo Hospitalario', TRUE),
('ST037', 'Puerto Fluvial', TRUE),
('ST038', 'Acueducto', TRUE),
('ST039', 'Puerta de Santiago', TRUE),
('ST040', 'Parque de la Cultura', TRUE),
('ST041', 'El Arrabal', TRUE),
('ST042', 'Los Pozos', TRUE),
('ST043', 'Cuatro Caminos', TRUE),
('ST044', 'Pazos Reales', TRUE),
('ST045', 'Los Molinos', TRUE),
('ST046', 'El Espigón', TRUE),
('ST047', 'Zona Universitaria', TRUE),
('ST048', 'Puerto Olímpico', TRUE),
('ST049', 'HUB Industrial Este', TRUE),
('ST050', 'HUB Industrial Oeste', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name), active = VALUES(active);

INSERT INTO transport_lines (code, name, color, active) VALUES
('L1', 'Línea 1', 'Roja', TRUE),
('L2', 'Línea 2', 'Verde', TRUE),
('L3', 'Línea 3', 'Amarilla', TRUE),
('L4', 'Línea 4', 'Lila', TRUE),
('L5', 'Línea 5', 'Azul', TRUE),
('L6', 'Línea 6', 'Naranja', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name), color = VALUES(color), active = VALUES(active);

DROP TEMPORARY TABLE IF EXISTS seed_line_stations;
CREATE TEMPORARY TABLE seed_line_stations (
    line_code VARCHAR(20) NOT NULL,
    station_code VARCHAR(20) NOT NULL,
    station_order INT NOT NULL,
    PRIMARY KEY (line_code, station_code),
    UNIQUE (line_code, station_order)
);

INSERT INTO seed_line_stations VALUES
('L1','ST030',1),('L1','ST023',2),('L1','ST017',3),('L1','ST016',4),('L1','ST015',5),
('L1','ST020',6),('L1','ST028',7),('L1','ST033',8),('L1','ST038',9),('L1','ST037',10),
('L1','ST010',11),('L1','ST009',12),('L1','ST011',13),('L1','ST043',14),('L1','ST045',15),
('L2','ST027',1),('L2','ST038',2),('L2','ST039',3),('L2','ST035',4),('L2','ST036',5),
('L2','ST031',6),('L2','ST023',7),('L2','ST022',8),('L2','ST021',9),('L2','ST020',10),
('L2','ST019',11),('L2','ST018',12),('L2','ST005',13),('L2','ST003',14),('L2','ST001',15),
('L3','ST048',1),('L3','ST017',2),('L3','ST022',3),('L3','ST029',4),('L3','ST028',5),
('L3','ST027',6),('L3','ST026',7),('L3','ST008',8),('L3','ST007',9),('L3','ST004',10),
('L3','ST003',11),('L3','ST002',12),('L3','ST049',13),
('L4','ST031',1),('L4','ST030',2),('L4','ST029',3),('L4','ST034',4),('L4','ST035',5),
('L4','ST042',6),('L4','ST041',7),('L4','ST037',8),('L4','ST032',9),('L4','ST027',10),
('L4','ST024',11),('L4','ST019',12),('L4','ST014',13),('L4','ST013',14),('L4','ST002',15),
('L4','ST001',16),
('L5','ST047',1),('L5','ST016',2),('L5','ST022',3),('L5','ST030',4),('L5','ST034',5),
('L5','ST039',6),('L5','ST042',7),('L5','ST044',8),('L5','ST043',9),('L5','ST040',10),
('L5','ST037',11),('L5','ST008',12),('L5','ST005',13),('L5','ST002',14),('L5','ST050',15),
('L6','ST046',1),('L6','ST020',2),('L6','ST025',3),('L6','ST027',4),('L6','ST033',5),
('L6','ST039',6),('L6','ST041',7),('L6','ST040',8),('L6','ST012',9),('L6','ST009',10),
('L6','ST006',11),('L6','ST007',12),('L6','ST005',13),('L6','ST013',14);

INSERT INTO line_stations (line_id, station_id, station_order, active)
SELECT transport_line.id, stations.id, seed.station_order, TRUE
FROM seed_line_stations seed
JOIN transport_lines transport_line ON transport_line.code = seed.line_code
JOIN stations ON stations.code = seed.station_code
ON DUPLICATE KEY UPDATE station_order = VALUES(station_order), active = VALUES(active);

DROP TEMPORARY TABLE IF EXISTS seed_station_connections;
CREATE TEMPORARY TABLE seed_station_connections (
    origin_code VARCHAR(20) NOT NULL,
    destination_code VARCHAR(20) NOT NULL,
    distance_km DECIMAL(8, 2) NOT NULL,
    estimated_minutes INT NOT NULL,
    PRIMARY KEY (origin_code, destination_code)
);

INSERT INTO seed_station_connections VALUES
('ST001','ST002',5.00,5),('ST001','ST003',5.00,5),('ST002','ST003',2.00,2),
('ST002','ST005',2.00,2),('ST002','ST013',3.00,3),('ST002','ST049',2.00,2),
('ST002','ST050',2.00,2),('ST003','ST004',2.00,2),('ST003','ST005',2.00,2),
('ST004','ST007',2.00,2),('ST005','ST007',2.00,2),('ST005','ST008',2.00,2),
('ST005','ST013',3.00,3),('ST005','ST018',3.00,3),('ST006','ST007',2.00,2),
('ST006','ST009',2.00,2),('ST007','ST008',2.00,2),('ST008','ST026',3.00,3),
('ST008','ST037',3.00,3),('ST009','ST010',2.00,2),('ST009','ST011',2.00,2),
('ST009','ST012',2.00,2),('ST010','ST037',3.00,3),('ST011','ST043',3.00,3),
('ST012','ST040',3.00,3),('ST013','ST014',2.00,2),('ST014','ST019',2.00,2),
('ST015','ST016',2.00,2),('ST015','ST020',2.00,2),('ST016','ST017',2.00,2),
('ST016','ST022',2.00,2),('ST016','ST047',2.00,2),('ST017','ST022',2.00,2),
('ST017','ST023',2.00,2),('ST017','ST048',2.00,2),('ST018','ST019',2.00,2),
('ST019','ST020',2.00,2),('ST019','ST024',1.00,1),('ST020','ST021',2.00,2),
('ST020','ST025',1.00,1),('ST020','ST028',2.00,2),('ST020','ST046',2.00,2),
('ST021','ST022',2.00,2),('ST022','ST023',2.00,2),('ST022','ST029',2.00,2),
('ST022','ST030',2.00,2),('ST023','ST030',2.00,2),('ST023','ST031',2.00,2),
('ST024','ST027',1.00,1),('ST025','ST027',1.00,1),('ST026','ST027',1.00,1),
('ST027','ST028',1.00,1),('ST027','ST032',1.00,1),('ST027','ST033',1.00,1),
('ST027','ST038',1.00,1),('ST028','ST029',2.00,2),('ST028','ST033',1.00,1),
('ST029','ST030',2.00,2),('ST029','ST034',2.00,2),('ST030','ST031',2.00,2),
('ST030','ST034',2.00,2),('ST031','ST036',4.00,4),('ST032','ST037',2.00,2),
('ST033','ST038',1.00,1),('ST033','ST039',1.00,1),('ST034','ST035',2.00,2),
('ST034','ST039',2.00,2),('ST035','ST036',4.00,4),('ST035','ST039',2.00,2),
('ST035','ST042',2.00,2),('ST037','ST038',2.00,2),('ST037','ST040',2.00,2),
('ST037','ST041',2.00,2),('ST038','ST039',2.00,2),('ST039','ST041',2.00,2),
('ST039','ST042',2.00,2),('ST040','ST041',2.00,2),('ST040','ST043',2.00,2),
('ST041','ST042',2.00,2),('ST042','ST044',2.00,2),('ST043','ST044',2.00,2),
('ST043','ST045',2.00,2);

INSERT INTO station_connections (
    origin_station_id, destination_station_id, distance_km, estimated_minutes, bidirectional, active
)
SELECT origins.id, destinations.id, seed.distance_km, seed.estimated_minutes, TRUE, TRUE
FROM seed_station_connections seed
JOIN stations origins ON origins.code = seed.origin_code
JOIN stations destinations ON destinations.code = seed.destination_code
ON DUPLICATE KEY UPDATE
    distance_km = VALUES(distance_km),
    estimated_minutes = VALUES(estimated_minutes),
    bidirectional = VALUES(bidirectional),
    active = VALUES(active);

DROP TEMPORARY TABLE seed_line_stations;
DROP TEMPORARY TABLE seed_station_connections;
