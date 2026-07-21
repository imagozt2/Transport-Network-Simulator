USE transport_simulator_db;

UPDATE line_stations
SET dwell_seconds = 20
WHERE id > 0;
