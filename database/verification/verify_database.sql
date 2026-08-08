USE transport_simulator_db;

SELECT COUNT(*) AS operator_account_count
FROM operator_accounts;

SELECT COUNT(*) AS passenger_account_count
FROM passenger_accounts;

SELECT COUNT(*) AS passenger_session_count
FROM passenger_sessions;

SELECT COUNT(*) AS passenger_account_token_count
FROM passenger_account_tokens;

SELECT COUNT(*) AS passenger_account_status_change_count
FROM passenger_account_status_changes;

SELECT COUNT(*) AS compensatory_ticket_issuance_count
FROM compensatory_ticket_issuances;

SELECT COUNT(*) AS ticket_count
FROM tickets;

SELECT COUNT(*) AS ticket_support_count
FROM ticket_supports;

SELECT COUNT(*) AS ticket_qr_credential_count
FROM ticket_qr_credentials;

SELECT COUNT(*) AS ticket_operation_count
FROM ticket_operations;

SELECT COUNT(*) AS incident_count
FROM incidents;

SELECT COUNT(*) AS incident_status_change_count
FROM incident_status_changes;

SELECT COUNT(*) AS incident_comment_count
FROM incident_comments;

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
   OR account_status NOT IN ('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED', 'DISABLED')
   OR failed_login_attempts < 0;

SELECT sessions.id, sessions.installation_id, sessions.platform
FROM passenger_sessions sessions
LEFT JOIN passenger_accounts passengers ON passengers.id = sessions.passenger_account_id
WHERE passengers.id IS NULL
   OR sessions.public_id NOT REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
   OR sessions.platform <> 'ANDROID'
   OR sessions.access_token_expires_at > sessions.refresh_token_expires_at
   OR (sessions.revoked_at IS NULL AND sessions.revocation_reason IS NOT NULL)
   OR (sessions.revoked_at IS NOT NULL
       AND (sessions.revocation_reason IS NULL OR CHAR_LENGTH(TRIM(sessions.revocation_reason)) = 0));

SELECT tokens.id, tokens.token_type, tokens.expires_at, tokens.used_at
FROM passenger_account_tokens tokens
LEFT JOIN passenger_accounts passengers ON passengers.id = tokens.passenger_account_id
WHERE passengers.id IS NULL
   OR tokens.token_type NOT IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')
   OR tokens.used_at > tokens.expires_at;

SELECT status_changes.id
FROM passenger_account_status_changes status_changes
LEFT JOIN passenger_accounts passengers
    ON passengers.id = status_changes.passenger_account_id
LEFT JOIN operator_accounts operators
    ON operators.id = status_changes.changed_by_operator_id
WHERE passengers.id IS NULL
   OR operators.id IS NULL
   OR status_changes.previous_status = status_changes.new_status
   OR status_changes.previous_status NOT IN ('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED', 'DISABLED')
   OR status_changes.new_status NOT IN ('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED', 'DISABLED');

SELECT issuances.id, issuances.code, products.product_type, devices.code AS device_code,
       devices.device_type, issuances.issuance_status
FROM compensatory_ticket_issuances issuances
LEFT JOIN ticket_products products ON products.id = issuances.product_id
LEFT JOIN devices ON devices.id = issuances.target_device_id
LEFT JOIN operator_accounts operators ON operators.id = issuances.requested_by_operator_id
LEFT JOIN tickets ON tickets.id = issuances.issued_ticket_id
WHERE products.id IS NULL
   OR devices.id IS NULL
   OR devices.device_type <> 'TICKET_MACHINE'
   OR operators.id IS NULL
   OR (issuances.issued_ticket_id IS NOT NULL AND tickets.id IS NULL)
   OR issuances.charged_amount <> 0
   OR ((issuances.origin_station_id IS NULL) <> (issuances.destination_station_id IS NULL))
   OR (issuances.origin_station_id IS NOT NULL
       AND issuances.origin_station_id = issuances.destination_station_id)
   OR ((issuances.origin_station_id IS NULL) <> (issuances.station_count IS NULL))
   OR (products.product_type = 'SINGLE_TRIP'
       AND (issuances.origin_station_id IS NULL OR issuances.destination_station_id IS NULL
            OR issuances.station_count IS NULL))
   OR (products.product_type = 'MULTI_TRIP' AND issuances.selected_trips IS NULL)
   OR (products.product_type = 'TIME_PASS' AND issuances.selected_days IS NULL)
   OR (products.product_type = 'SMART_BALANCE' AND issuances.recharge_amount IS NULL);

SELECT tickets.id, tickets.code, tickets.product_type, tickets.status
FROM tickets
LEFT JOIN ticket_products products ON products.id = tickets.product_id
LEFT JOIN passenger_accounts passengers ON passengers.id = tickets.passenger_user_id
WHERE products.id IS NULL
   OR tickets.product_type <> products.product_type
   OR tickets.status NOT IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'BLOCKED', 'CANCELLED')
   OR tickets.balance_amount < 0
   OR tickets.lock_version < 0
   OR (tickets.passenger_user_id IS NOT NULL AND passengers.id IS NULL);

SELECT supports.id, supports.code, supports.support_type, supports.support_status
FROM ticket_supports supports
LEFT JOIN tickets ON tickets.id = supports.ticket_id
LEFT JOIN devices ON devices.id = supports.issued_by_device_id
LEFT JOIN passenger_accounts passengers ON passengers.id = supports.passenger_account_id
LEFT JOIN ticket_supports replacements ON replacements.id = supports.replaced_by_support_id
WHERE tickets.id IS NULL
   OR supports.support_type NOT IN ('PHYSICAL', 'DIGITAL')
   OR supports.support_status NOT IN ('ACTIVE', 'BLOCKED', 'REVOKED', 'SUPERSEDED')
   OR (supports.issued_by_device_id IS NOT NULL AND devices.id IS NULL)
   OR (supports.passenger_account_id IS NOT NULL AND passengers.id IS NULL)
   OR (supports.replaced_by_support_id IS NOT NULL AND replacements.id IS NULL)
   OR (supports.support_status = 'SUPERSEDED' AND replacements.id IS NULL)
   OR (supports.support_status <> 'SUPERSEDED' AND replacements.id IS NOT NULL)
   OR (supports.support_type = 'DIGITAL' AND supports.passenger_account_id IS NULL)
   OR (supports.support_type = 'PHYSICAL' AND supports.serial_number IS NULL)
   OR (supports.passenger_account_id IS NOT NULL
       AND tickets.passenger_user_id IS NOT NULL
       AND supports.passenger_account_id <> tickets.passenger_user_id);

SELECT credentials.id, credentials.credential_id, credentials.credential_status
FROM ticket_qr_credentials credentials
LEFT JOIN tickets ON tickets.id = credentials.ticket_id
LEFT JOIN ticket_supports supports ON supports.id = credentials.support_id
LEFT JOIN ticket_qr_credentials replacements
    ON replacements.id = credentials.superseded_by_credential_id
WHERE tickets.id IS NULL
   OR supports.id IS NULL
   OR supports.ticket_id <> credentials.ticket_id
   OR credentials.credential_status NOT IN ('ACTIVE', 'REVOKED', 'SUPERSEDED', 'EXPIRED')
   OR (credentials.superseded_by_credential_id IS NOT NULL AND replacements.id IS NULL)
   OR (credentials.credential_status = 'SUPERSEDED' AND replacements.id IS NULL)
   OR (credentials.credential_status <> 'SUPERSEDED' AND replacements.id IS NOT NULL)
   OR (credentials.credential_status = 'REVOKED' AND credentials.revoked_at IS NULL)
   OR (credentials.credential_status <> 'REVOKED' AND credentials.revoked_at IS NOT NULL);

SELECT claims.id, claims.validation_reference, claims.claim_status
FROM ticket_qr_use_claims claims
LEFT JOIN ticket_qr_credentials credentials ON credentials.id = claims.credential_id
WHERE credentials.id IS NULL
   OR claims.validation_type NOT IN ('ENTRY', 'EXIT')
   OR claims.claim_status NOT IN ('RECEIVED', 'COMPLETED')
   OR (claims.claim_status = 'RECEIVED' AND claims.completed_at IS NOT NULL)
   OR (claims.claim_status = 'COMPLETED' AND claims.completed_at IS NULL)
   OR claims.completed_at < claims.received_at;

SELECT operations.id, operations.code, operations.operation_type
FROM ticket_operations operations
LEFT JOIN tickets ON tickets.id = operations.ticket_id
LEFT JOIN ticket_supports supports ON supports.id = operations.support_id
LEFT JOIN purchases ON purchases.id = operations.purchase_id
LEFT JOIN ticket_journeys journeys ON journeys.id = operations.journey_id
WHERE tickets.id IS NULL
   OR (operations.support_id IS NOT NULL AND supports.id IS NULL)
   OR (operations.purchase_id IS NOT NULL AND purchases.id IS NULL)
   OR (operations.journey_id IS NOT NULL AND journeys.id IS NULL)
   OR (supports.id IS NOT NULL AND supports.ticket_id <> operations.ticket_id)
   OR (purchases.id IS NOT NULL AND purchases.ticket_id <> operations.ticket_id)
   OR (journeys.id IS NOT NULL AND journeys.ticket_id <> operations.ticket_id)
   OR operations.operation_type NOT IN (
       'ISSUED', 'RECHARGED', 'ENTRY_ACCEPTED', 'EXIT_ACCEPTED',
       'BLOCKED', 'UNBLOCKED', 'CANCELLED', 'SUPPORT_LINKED', 'QR_REVOKED'
   );

SELECT incidents.id, incidents.code, incidents.incident_status, incidents.priority
FROM incidents
LEFT JOIN operator_accounts creators ON creators.id = incidents.created_by_operator_id
LEFT JOIN operator_accounts assignees ON assignees.id = incidents.assigned_to_operator_id
LEFT JOIN transport_lines lines ON lines.id = incidents.affected_line_id
LEFT JOIN stations ON stations.id = incidents.affected_station_id
LEFT JOIN trains ON trains.id = incidents.affected_train_id
LEFT JOIN devices ON devices.id = incidents.affected_device_id
LEFT JOIN depots ON depots.id = incidents.affected_depot_id
WHERE creators.id IS NULL
   OR (incidents.assigned_to_operator_id IS NOT NULL AND assignees.id IS NULL)
   OR (incidents.affected_line_id IS NOT NULL AND lines.id IS NULL)
   OR (incidents.affected_station_id IS NOT NULL AND stations.id IS NULL)
   OR (incidents.affected_train_id IS NOT NULL AND trains.id IS NULL)
   OR (incidents.affected_device_id IS NOT NULL AND devices.id IS NULL)
   OR (incidents.affected_depot_id IS NOT NULL AND depots.id IS NULL)
   OR incidents.incident_category NOT IN ('SERVICE', 'DEVICE', 'INFRASTRUCTURE', 'TICKETING', 'SECURITY', 'OTHER')
   OR incidents.priority NOT IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
   OR incidents.incident_status NOT IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')
   OR (incidents.assigned_at IS NOT NULL AND incidents.assigned_at < incidents.opened_at)
   OR (incidents.resolved_at IS NOT NULL AND incidents.resolved_at < incidents.opened_at)
   OR (incidents.closed_at IS NOT NULL AND incidents.closed_at < incidents.opened_at);

SELECT changes.id
FROM incident_status_changes changes
LEFT JOIN incidents ON incidents.id = changes.incident_id
LEFT JOIN operator_accounts operators ON operators.id = changes.changed_by_operator_id
WHERE incidents.id IS NULL
   OR operators.id IS NULL
   OR (changes.previous_status IS NOT NULL AND changes.previous_status = changes.new_status)
   OR (changes.previous_status IS NOT NULL AND changes.previous_status NOT IN (
       'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED'
   ))
   OR changes.new_status NOT IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED');

SELECT comments.id
FROM incident_comments comments
LEFT JOIN incidents ON incidents.id = comments.incident_id
LEFT JOIN operator_accounts operators ON operators.id = comments.author_operator_id
WHERE incidents.id IS NULL
   OR operators.id IS NULL
   OR CHAR_LENGTH(TRIM(comments.comment_text)) = 0;

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
