USE transport_simulator_db;

ALTER TABLE line_depots
    ADD COLUMN dispatch_terminal_station_id BIGINT NULL AFTER depot_id;

UPDATE line_depots line_depot
JOIN transport_lines line ON line.id = line_depot.line_id
JOIN depots depot ON depot.id = line_depot.depot_id
JOIN (
    SELECT 'L1' line_code, 'DEP-LF-A' depot_code, 'ST030' terminal_code UNION ALL
    SELECT 'L1', 'DEP-CC-A', 'ST045' UNION ALL
    SELECT 'L2', 'DEP-LF-B', 'ST027' UNION ALL
    SELECT 'L2', 'DEP-AIR-A', 'ST001' UNION ALL
    SELECT 'L3', 'DEP-PO', 'ST048' UNION ALL
    SELECT 'L3', 'DEP-HUB-E', 'ST049' UNION ALL
    SELECT 'L4', 'DEP-MI', 'ST031' UNION ALL
    SELECT 'L4', 'DEP-AIR-B', 'ST001' UNION ALL
    SELECT 'L5', 'DEP-CC-B', 'ST047' UNION ALL
    SELECT 'L5', 'DEP-HUB-W', 'ST050' UNION ALL
    SELECT 'L6', 'DEP-ESP', 'ST046' UNION ALL
    SELECT 'L6', 'DEP-MC', 'ST013'
) mapping ON mapping.line_code = line.code AND mapping.depot_code = depot.code
JOIN stations terminal ON terminal.code = mapping.terminal_code
SET line_depot.dispatch_terminal_station_id = terminal.id
WHERE line_depot.id > 0;

ALTER TABLE line_depots
    MODIFY dispatch_terminal_station_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_line_depots_dispatch_terminal
        FOREIGN KEY (dispatch_terminal_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT;
