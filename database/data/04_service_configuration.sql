USE transport_simulator_db;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO service_calendars (
    code, name, day_type, service_start_time, service_end_time, valid_from, valid_until, active
) VALUES
('WEEKDAY_STANDARD', 'Servicio laborable', 'WEEKDAY', '05:00:00', '00:30:00', '2026-01-01', NULL, TRUE),
('SATURDAY_STANDARD', 'Servicio de sábado', 'SATURDAY', '06:00:00', '01:00:00', '2026-01-01', NULL, TRUE),
('SUNDAY_HOLIDAY_STANDARD', 'Servicio de domingo y festivo', 'SUNDAY_HOLIDAY', '06:30:00', '00:30:00', '2026-01-01', NULL, TRUE)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), day_type = VALUES(day_type),
    service_start_time = VALUES(service_start_time), service_end_time = VALUES(service_end_time),
    valid_from = VALUES(valid_from), valid_until = VALUES(valid_until), active = VALUES(active);

DROP TEMPORARY TABLE IF EXISTS seed_service_periods;
CREATE TEMPORARY TABLE seed_service_periods (
    calendar_code VARCHAR(30) NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    period_type VARCHAR(30) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    period_order INT NOT NULL,
    base_headway_seconds INT NOT NULL,
    PRIMARY KEY (calendar_code, code),
    UNIQUE (calendar_code, period_order)
);

INSERT INTO seed_service_periods VALUES
('WEEKDAY_STANDARD','START','Inicio progresivo','SERVICE_START','05:00:00','06:30:00',1,480),
('WEEKDAY_STANDARD','MORNING_OFF_PEAK','Valle de primera hora','OFF_PEAK','06:30:00','07:30:00',2,360),
('WEEKDAY_STANDARD','MORNING_PEAK','Punta de mañana','PEAK','07:30:00','09:30:00',3,180),
('WEEKDAY_STANDARD','DAYTIME','Servicio diurno','REGULAR','09:30:00','17:00:00',4,300),
('WEEKDAY_STANDARD','EVENING_PEAK','Punta de tarde','PEAK','17:00:00','20:00:00',5,210),
('WEEKDAY_STANDARD','EVENING_OFF_PEAK','Valle nocturno','OFF_PEAK','20:00:00','23:00:00',6,420),
('WEEKDAY_STANDARD','END','Retirada progresiva','SERVICE_END','23:00:00','00:30:00',7,600),
('SATURDAY_STANDARD','START','Inicio progresivo','SERVICE_START','06:00:00','08:00:00',1,600),
('SATURDAY_STANDARD','DAYTIME','Servicio diurno','REGULAR','08:00:00','22:00:00',2,360),
('SATURDAY_STANDARD','EVENING_OFF_PEAK','Valle nocturno','OFF_PEAK','22:00:00','00:00:00',3,480),
('SATURDAY_STANDARD','END','Retirada progresiva','SERVICE_END','00:00:00','01:00:00',4,720),
('SUNDAY_HOLIDAY_STANDARD','START','Inicio progresivo','SERVICE_START','06:30:00','09:00:00',1,720),
('SUNDAY_HOLIDAY_STANDARD','DAYTIME','Servicio diurno','REGULAR','09:00:00','21:00:00',2,420),
('SUNDAY_HOLIDAY_STANDARD','EVENING_OFF_PEAK','Valle nocturno','OFF_PEAK','21:00:00','23:30:00',3,540),
('SUNDAY_HOLIDAY_STANDARD','END','Retirada progresiva','SERVICE_END','23:30:00','00:30:00',4,720);

INSERT INTO service_periods (
    service_calendar_id, code, name, period_type, start_time, end_time, period_order, active
)
SELECT calendars.id, seed.code, seed.name, seed.period_type,
       seed.start_time, seed.end_time, seed.period_order, TRUE
FROM seed_service_periods seed
JOIN service_calendars calendars ON calendars.code = seed.calendar_code
ON DUPLICATE KEY UPDATE
    name = VALUES(name), period_type = VALUES(period_type),
    start_time = VALUES(start_time), end_time = VALUES(end_time),
    period_order = VALUES(period_order), active = VALUES(active);

DROP TEMPORARY TABLE IF EXISTS seed_line_headway_adjustments;
CREATE TEMPORARY TABLE seed_line_headway_adjustments (
    line_code VARCHAR(20) PRIMARY KEY,
    adjustment_seconds INT NOT NULL
);

INSERT INTO seed_line_headway_adjustments VALUES
('L1',0),('L2',15),('L3',45),('L4',15),('L5',30),('L6',60);

INSERT INTO line_service_levels (line_id, service_period_id, headway_seconds, active)
SELECT transport_line.id, periods.id,
       seed_periods.base_headway_seconds + adjustments.adjustment_seconds, TRUE
FROM seed_service_periods seed_periods
JOIN service_calendars calendars ON calendars.code = seed_periods.calendar_code
JOIN service_periods periods
    ON periods.service_calendar_id = calendars.id AND periods.code = seed_periods.code
CROSS JOIN seed_line_headway_adjustments adjustments
JOIN transport_lines transport_line ON transport_line.code = adjustments.line_code
WHERE TRUE
ON DUPLICATE KEY UPDATE
    headway_seconds = VALUES(headway_seconds), active = VALUES(active);

UPDATE line_stations
SET travel_seconds_to_next = NULL, dwell_seconds = 20
WHERE id > 0;

UPDATE line_stations current_stop
JOIN line_stations next_stop
    ON next_stop.line_id = current_stop.line_id
    AND next_stop.station_order = current_stop.station_order + 1
JOIN station_connections connections
    ON (
        connections.origin_station_id = current_stop.station_id
        AND connections.destination_station_id = next_stop.station_id
    ) OR (
        connections.bidirectional = TRUE
        AND connections.origin_station_id = next_stop.station_id
        AND connections.destination_station_id = current_stop.station_id
    )
SET current_stop.travel_seconds_to_next = connections.estimated_minutes * 60
WHERE current_stop.id > 0
  AND current_stop.active = TRUE
  AND next_stop.active = TRUE
  AND connections.active = TRUE;

DROP TEMPORARY TABLE IF EXISTS seed_line_depots;
CREATE TEMPORARY TABLE seed_line_depots (
    line_code VARCHAR(20) NOT NULL,
    depot_code VARCHAR(30) NOT NULL,
    dispatch_terminal_code VARCHAR(20) NOT NULL,
    dispatch_priority INT NOT NULL,
    PRIMARY KEY (line_code, depot_code)
);

INSERT INTO seed_line_depots VALUES
('L1','DEP-LF-A','ST030',1),('L1','DEP-CC-A','ST045',2),
('L2','DEP-LF-B','ST027',1),('L2','DEP-AIR-A','ST001',2),
('L3','DEP-PO','ST048',1),('L3','DEP-HUB-E','ST049',2),
('L4','DEP-MI','ST031',1),('L4','DEP-AIR-B','ST001',2),
('L5','DEP-CC-B','ST047',1),('L5','DEP-HUB-W','ST050',2),
('L6','DEP-ESP','ST046',1),('L6','DEP-MC','ST013',2);

INSERT INTO line_depots (
    line_id, depot_id, dispatch_terminal_station_id,
    dispatch_priority, dispatch_enabled, reception_enabled, active
)
SELECT transport_line.id, depots.id, terminals.id, seed.dispatch_priority, TRUE, TRUE, TRUE
FROM seed_line_depots seed
JOIN transport_lines transport_line ON transport_line.code = seed.line_code
JOIN depots ON depots.code = seed.depot_code
JOIN stations terminals ON terminals.code = seed.dispatch_terminal_code
WHERE TRUE
ON DUPLICATE KEY UPDATE
    dispatch_priority = VALUES(dispatch_priority),
    dispatch_terminal_station_id = VALUES(dispatch_terminal_station_id),
    dispatch_enabled = VALUES(dispatch_enabled),
    reception_enabled = VALUES(reception_enabled), active = VALUES(active);

DROP TEMPORARY TABLE seed_line_depots;
DROP TEMPORARY TABLE seed_line_headway_adjustments;
DROP TEMPORARY TABLE seed_service_periods;
