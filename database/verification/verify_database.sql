USE transport_simulator_db;

SELECT 'stations' AS entity, COUNT(*) AS actual, 50 AS expected FROM stations
UNION ALL SELECT 'transport_lines', COUNT(*), 6 FROM transport_lines
UNION ALL SELECT 'line_stations', COUNT(*), 88 FROM line_stations
UNION ALL SELECT 'station_connections', COUNT(*), 82 FROM station_connections
UNION ALL SELECT 'devices', COUNT(*), 622 FROM devices
UNION ALL SELECT 'train_models', COUNT(*), 4 FROM train_models
UNION ALL SELECT 'depots', COUNT(*), 12 FROM depots
UNION ALL SELECT 'trains', COUNT(*), 242 FROM trains
UNION ALL SELECT 'line_service_settings', COUNT(*), 6 FROM line_service_settings
UNION ALL SELECT 'ticket_products', COUNT(*), 4 FROM ticket_products;

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
