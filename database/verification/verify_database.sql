USE transport_simulator_db;

SELECT COUNT(*) AS operator_account_count
FROM operator_accounts;

SELECT COUNT(*) AS passenger_account_count
FROM passenger_accounts;

SELECT COUNT(*) AS passenger_account_status_change_count
FROM passenger_account_status_changes;

SELECT 'stations' AS entity, COUNT(*) AS actual, 50 AS expected FROM stations
UNION ALL SELECT 'transport_lines', COUNT(*), 6 FROM transport_lines
UNION ALL SELECT 'line_stations', COUNT(*), 88 FROM line_stations
UNION ALL SELECT 'station_connections', COUNT(*), 82 FROM station_connections
UNION ALL SELECT 'devices', COUNT(*), 622 FROM devices
UNION ALL SELECT 'train_models', COUNT(*), 4 FROM train_models
UNION ALL SELECT 'depots', COUNT(*), 12 FROM depots
UNION ALL SELECT 'trains', COUNT(*), 242 FROM trains
UNION ALL SELECT 'regular_service_trains', COUNT(*), 230 FROM trains WHERE fleet_role = 'REGULAR_SERVICE'
UNION ALL SELECT 'reserve_trains', COUNT(*), 5 FROM trains WHERE fleet_role = 'RESERVE'
UNION ALL SELECT 'historic_trains', COUNT(*), 7 FROM trains WHERE fleet_role = 'HISTORIC'
UNION ALL SELECT 'service_calendars', COUNT(*), 3 FROM service_calendars
UNION ALL SELECT 'service_periods', COUNT(*), 15 FROM service_periods
UNION ALL SELECT 'line_service_levels', COUNT(*), 90 FROM line_service_levels
UNION ALL SELECT 'line_depots', COUNT(*), 12 FROM line_depots
UNION ALL SELECT 'ticket_products', COUNT(*), 4 FROM ticket_products;

SELECT id, username, email, operator_role, account_status
FROM operator_accounts
WHERE CHAR_LENGTH(TRIM(username)) < 3
   OR email NOT LIKE '%_@_%._%'
   OR CHAR_LENGTH(password_hash) < 20
   OR operator_role NOT IN ('OPERATOR', 'ADMINISTRATOR')
   OR account_status NOT IN ('ACTIVE', 'DISABLED', 'LOCKED')
   OR failed_login_attempts < 0;

SELECT id, public_id, email, account_status
FROM passenger_accounts
WHERE public_id NOT REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
   OR email NOT LIKE '%_@_%._%'
   OR CHAR_LENGTH(password_hash) < 20
   OR CHAR_LENGTH(TRIM(first_name)) = 0
   OR CHAR_LENGTH(TRIM(last_name)) = 0
   OR account_status NOT IN ('ACTIVE', 'BLOCKED', 'DISABLED')
   OR failed_login_attempts < 0;

SELECT status_changes.id
FROM passenger_account_status_changes status_changes
LEFT JOIN passenger_accounts passengers
    ON passengers.id = status_changes.passenger_account_id
LEFT JOIN operator_accounts operators
    ON operators.id = status_changes.changed_by_operator_id
WHERE passengers.id IS NULL
   OR operators.id IS NULL
   OR status_changes.previous_status = status_changes.new_status
   OR status_changes.previous_status NOT IN ('ACTIVE', 'BLOCKED', 'DISABLED')
   OR status_changes.new_status NOT IN ('ACTIVE', 'BLOCKED', 'DISABLED');

SELECT transport_line.code, COUNT(line_stations.id) AS station_count
FROM transport_lines transport_line
LEFT JOIN line_stations ON line_stations.line_id = transport_line.id
GROUP BY transport_line.id, transport_line.code
ORDER BY transport_line.code;

SELECT connections.id
FROM station_connections connections
LEFT JOIN stations origins ON origins.id = connections.origin_station_id
LEFT JOIN stations destinations ON destinations.id = connections.destination_station_id
WHERE origins.id IS NULL OR destinations.id IS NULL;

SELECT line_stations.id
FROM line_stations
LEFT JOIN transport_lines transport_line ON transport_line.id = line_stations.line_id
LEFT JOIN stations ON stations.id = line_stations.station_id
WHERE transport_line.id IS NULL OR stations.id IS NULL;

SELECT depots.code, depots.capacity, COUNT(trains.id) AS assigned_trains
FROM depots
LEFT JOIN trains ON trains.home_depot_id = depots.id
GROUP BY depots.id, depots.code, depots.capacity
HAVING assigned_trains > depots.capacity;

SELECT fleet_role, COUNT(*) AS train_count
FROM trains
GROUP BY fleet_role
ORDER BY fleet_role;

SELECT trains.code, models.series, trains.fleet_role
FROM trains
JOIN train_models models ON models.id = trains.train_model_id
WHERE (trains.fleet_role = 'REGULAR_SERVICE' AND models.series <> '9000')
   OR (trains.fleet_role <> 'REGULAR_SERVICE' AND trains.status = 'IN_SERVICE');

SELECT trains.code, trains.fleet_role, trains.dispatch_order
FROM trains
WHERE (trains.fleet_role = 'REGULAR_SERVICE' AND trains.dispatch_order IS NULL)
   OR (trains.fleet_role IN ('RESERVE', 'HISTORIC') AND trains.dispatch_order IS NOT NULL);

SELECT trains.code, trains.status, depots.code AS current_depot
FROM trains
LEFT JOIN depots ON depots.id = trains.current_depot_id
WHERE trains.status = 'DEPOT' AND depots.id IS NULL;

SELECT transport_line.code, stops.station_order
FROM line_stations stops
JOIN transport_lines transport_line ON transport_line.id = stops.line_id
JOIN (
    SELECT line_id, MAX(station_order) AS last_stop
    FROM line_stations
    WHERE active = TRUE
    GROUP BY line_id
) routes ON routes.line_id = stops.line_id
WHERE stops.active = TRUE
  AND stops.station_order < routes.last_stop
  AND stops.travel_seconds_to_next IS NULL;

SELECT transport_line.code, calendars.code AS calendar_code, COUNT(levels.id) AS configured_periods
FROM transport_lines transport_line
CROSS JOIN service_calendars calendars
LEFT JOIN service_periods periods ON periods.service_calendar_id = calendars.id AND periods.active = TRUE
LEFT JOIN line_service_levels levels
    ON levels.line_id = transport_line.id
    AND levels.service_period_id = periods.id
    AND levels.active = TRUE
WHERE transport_line.active = TRUE AND calendars.active = TRUE
GROUP BY transport_line.id, transport_line.code, calendars.id, calendars.code
HAVING configured_periods <> COUNT(periods.id);

SELECT transport_line.code
FROM transport_lines transport_line
LEFT JOIN line_depots ON line_depots.line_id = transport_line.id
    AND line_depots.active = TRUE AND line_depots.dispatch_enabled = TRUE
WHERE transport_line.active = TRUE
GROUP BY transport_line.id, transport_line.code
HAVING COUNT(line_depots.id) = 0;
