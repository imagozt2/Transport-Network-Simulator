USE transport_simulator_db;

CREATE TABLE operator_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(150) NOT NULL,
    operator_role VARCHAR(30) NOT NULL DEFAULT 'OPERATOR',
    account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    last_login_at DATETIME NULL,
    password_changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_operator_accounts_username UNIQUE (username),
    CONSTRAINT uk_operator_accounts_email UNIQUE (email),
    CONSTRAINT chk_operator_accounts_username CHECK (CHAR_LENGTH(TRIM(username)) >= 3),
    CONSTRAINT chk_operator_accounts_email CHECK (email LIKE '%_@_%._%'),
    CONSTRAINT chk_operator_accounts_password_hash CHECK (CHAR_LENGTH(password_hash) >= 20),
    CONSTRAINT chk_operator_accounts_role CHECK (
        operator_role IN ('OPERATOR', 'ADMINISTRATOR')
    ),
    CONSTRAINT chk_operator_accounts_status CHECK (
        account_status IN ('ACTIVE', 'DISABLED', 'LOCKED')
    ),
    CONSTRAINT chk_operator_accounts_failed_attempts CHECK (failed_login_attempts >= 0)
);

CREATE INDEX idx_operator_accounts_role_status
    ON operator_accounts (operator_role, account_status);
CREATE INDEX idx_operator_accounts_locked_until
    ON operator_accounts (locked_until);

CREATE TABLE passenger_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(150) NOT NULL,
    account_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    preferred_locale VARCHAR(10) NOT NULL DEFAULT 'es-ES',
    accepted_terms_version VARCHAR(30) NULL,
    accepted_terms_at DATETIME NULL,
    email_verified_at DATETIME NULL,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    last_login_at DATETIME NULL,
    password_changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_passenger_accounts_public_id UNIQUE (public_id),
    CONSTRAINT uk_passenger_accounts_email UNIQUE (email),
    CONSTRAINT chk_passenger_accounts_public_id CHECK (
        public_id REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
    ),
    CONSTRAINT chk_passenger_accounts_email CHECK (email LIKE '%_@_%._%'),
    CONSTRAINT chk_passenger_accounts_password_hash CHECK (CHAR_LENGTH(password_hash) >= 20),
    CONSTRAINT chk_passenger_accounts_first_name CHECK (
        CHAR_LENGTH(TRIM(first_name)) > 0
    ),
    CONSTRAINT chk_passenger_accounts_last_name CHECK (
        CHAR_LENGTH(TRIM(last_name)) > 0
    ),
    CONSTRAINT chk_passenger_accounts_status CHECK (
        account_status IN ('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED', 'DISABLED')
    ),
    CONSTRAINT chk_passenger_accounts_locale CHECK (
        preferred_locale REGEXP '^[a-z]{2}-[A-Z]{2}$'
    ),
    CONSTRAINT chk_passenger_accounts_terms CHECK (
        (accepted_terms_version IS NULL AND accepted_terms_at IS NULL)
        OR (CHAR_LENGTH(TRIM(accepted_terms_version)) > 0 AND accepted_terms_at IS NOT NULL)
    ),
    CONSTRAINT chk_passenger_accounts_failed_attempts CHECK (failed_login_attempts >= 0)
);

CREATE INDEX idx_passenger_accounts_status_created
    ON passenger_accounts (account_status, created_at);
CREATE INDEX idx_passenger_accounts_email_verified
    ON passenger_accounts (email_verified_at);
CREATE INDEX idx_passenger_accounts_locked_until
    ON passenger_accounts (locked_until);
CREATE INDEX idx_passenger_accounts_name
    ON passenger_accounts (last_name, first_name);

CREATE TABLE passenger_mobile_devices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    passenger_account_id BIGINT NOT NULL,
    installation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    device_name VARCHAR(100) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    device_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    registered_at DATETIME NOT NULL,
    last_seen_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_passenger_mobile_devices_public_id UNIQUE (public_id),
    CONSTRAINT uk_passenger_mobile_devices_installation UNIQUE (installation_id),
    CONSTRAINT fk_passenger_mobile_devices_account FOREIGN KEY (passenger_account_id)
        REFERENCES passenger_accounts (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_passenger_mobile_devices_platform CHECK (platform = 'ANDROID'),
    CONSTRAINT chk_passenger_mobile_devices_status CHECK (device_status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT chk_passenger_mobile_devices_public_id CHECK (
        public_id REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
    ),
    CONSTRAINT chk_passenger_mobile_devices_lifecycle CHECK (
        (device_status = 'ACTIVE' AND revoked_at IS NULL)
        OR (device_status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX idx_passenger_mobile_devices_account_status
    ON passenger_mobile_devices (passenger_account_id, device_status, last_seen_at);

CREATE TABLE passenger_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mobile_device_id BIGINT NOT NULL,
    access_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    refresh_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    access_token_expires_at DATETIME NOT NULL,
    refresh_token_expires_at DATETIME NOT NULL,
    last_used_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    revocation_reason VARCHAR(100) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_passenger_sessions_access_token UNIQUE (access_token_hash),
    CONSTRAINT uk_passenger_sessions_refresh_token UNIQUE (refresh_token_hash),
    CONSTRAINT uk_passenger_sessions_public_id UNIQUE (public_id),
    CONSTRAINT fk_passenger_sessions_device FOREIGN KEY (mobile_device_id)
        REFERENCES passenger_mobile_devices (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_passenger_sessions_public_id CHECK (
        public_id REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
    ),
    CONSTRAINT chk_passenger_sessions_access_expiry CHECK (
        access_token_expires_at <= refresh_token_expires_at
    ),
    CONSTRAINT chk_passenger_sessions_hashes CHECK (
        access_token_hash REGEXP '^[0-9a-fA-F]{64}$'
        AND refresh_token_hash REGEXP '^[0-9a-fA-F]{64}$'
    ),
    CONSTRAINT chk_passenger_sessions_revocation CHECK (
        (revoked_at IS NULL AND revocation_reason IS NULL)
        OR (revoked_at IS NOT NULL AND CHAR_LENGTH(TRIM(revocation_reason)) > 0)
    )
);

CREATE INDEX idx_passenger_sessions_device_active
    ON passenger_sessions (mobile_device_id, revoked_at, refresh_token_expires_at);

CREATE TABLE passenger_account_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    passenger_account_id BIGINT NOT NULL,
    token_type VARCHAR(30) NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_passenger_account_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_passenger_account_tokens_account FOREIGN KEY (passenger_account_id)
        REFERENCES passenger_accounts (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_passenger_account_tokens_type CHECK (
        token_type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')
    ),
    CONSTRAINT chk_passenger_account_tokens_usage CHECK (
        used_at IS NULL OR used_at <= expires_at
    )
);

CREATE INDEX idx_passenger_account_tokens_account_type
    ON passenger_account_tokens (passenger_account_id, token_type, used_at, expires_at);

CREATE TABLE passenger_account_status_changes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    passenger_account_id BIGINT NOT NULL,
    changed_by_operator_id BIGINT NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_passenger_status_changes_account FOREIGN KEY (passenger_account_id)
        REFERENCES passenger_accounts (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_passenger_status_changes_operator FOREIGN KEY (changed_by_operator_id)
        REFERENCES operator_accounts (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_passenger_status_changes_previous CHECK (
        previous_status IN ('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED', 'DISABLED')
    ),
    CONSTRAINT chk_passenger_status_changes_new CHECK (
        new_status IN ('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED', 'DISABLED')
    ),
    CONSTRAINT chk_passenger_status_changes_distinct CHECK (
        previous_status <> new_status
    ),
    CONSTRAINT chk_passenger_status_changes_reason CHECK (
        reason IS NULL OR CHAR_LENGTH(TRIM(reason)) > 0
    )
);

CREATE INDEX idx_passenger_status_changes_account_created
    ON passenger_account_status_changes (passenger_account_id, created_at);
CREATE INDEX idx_passenger_status_changes_operator_created
    ON passenger_account_status_changes (changed_by_operator_id, created_at);

CREATE TABLE stations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_stations_code UNIQUE (code),
    CONSTRAINT uk_stations_name UNIQUE (name)
);

CREATE INDEX idx_stations_active ON stations (active);

CREATE TABLE transport_lines (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_transport_lines_code UNIQUE (code),
    CONSTRAINT uk_transport_lines_name UNIQUE (name)
);

CREATE INDEX idx_transport_lines_active ON transport_lines (active);

CREATE TABLE line_stations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    line_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    station_order INT NOT NULL,
    travel_seconds_to_next INT NULL,
    dwell_seconds INT NOT NULL DEFAULT 20,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_line_stations_line FOREIGN KEY (line_id) REFERENCES transport_lines (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_line_stations_station FOREIGN KEY (station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uk_line_stations_line_station UNIQUE (line_id, station_id),
    CONSTRAINT uk_line_stations_line_order UNIQUE (line_id, station_order),
    CONSTRAINT chk_line_stations_order CHECK (station_order > 0),
    CONSTRAINT chk_line_stations_travel_seconds CHECK (
        travel_seconds_to_next IS NULL OR travel_seconds_to_next > 0
    ),
    CONSTRAINT chk_line_stations_dwell_seconds CHECK (dwell_seconds >= 0)
);

CREATE INDEX idx_line_stations_station ON line_stations (station_id);

CREATE TABLE station_connections (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    origin_station_id BIGINT NOT NULL,
    destination_station_id BIGINT NOT NULL,
    distance_km DECIMAL(8, 2) NOT NULL,
    estimated_minutes INT NOT NULL,
    bidirectional BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_station_connections_origin FOREIGN KEY (origin_station_id) REFERENCES stations (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_station_connections_destination FOREIGN KEY (destination_station_id) REFERENCES stations (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_station_connections_pair UNIQUE (origin_station_id, destination_station_id),
    CONSTRAINT chk_station_connections_distinct CHECK (origin_station_id <> destination_station_id),
    CONSTRAINT chk_station_connections_distance CHECK (distance_km > 0),
    CONSTRAINT chk_station_connections_minutes CHECK (estimated_minutes > 0)
);

CREATE INDEX idx_station_connections_destination ON station_connections (destination_station_id);
CREATE INDEX idx_station_connections_active ON station_connections (active);

CREATE TABLE devices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    device_type VARCHAR(30) NOT NULL,
    station_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OFFLINE',
    last_connection_at DATETIME NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_devices_code UNIQUE (code),
    CONSTRAINT fk_devices_station FOREIGN KEY (station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE INDEX idx_devices_station ON devices (station_id);
CREATE INDEX idx_devices_type_status ON devices (device_type, status);
CREATE INDEX idx_devices_active ON devices (active);

CREATE TABLE device_mqtt_identities (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id BIGINT NOT NULL,
    instance_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mqtt_client_id VARCHAR(100) NOT NULL,
    authentication_mode VARCHAR(20) NOT NULL,
    identity_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    certificate_serial VARCHAR(128) NULL,
    valid_from DATETIME NOT NULL,
    valid_until DATETIME NULL,
    last_authenticated_at DATETIME NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_mqtt_identities_device UNIQUE (device_id),
    CONSTRAINT uk_device_mqtt_identities_instance UNIQUE (instance_id),
    CONSTRAINT uk_device_mqtt_identities_client UNIQUE (mqtt_client_id),
    CONSTRAINT uk_device_mqtt_identities_certificate UNIQUE (certificate_serial),
    CONSTRAINT fk_device_mqtt_identities_device FOREIGN KEY (device_id) REFERENCES devices (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_device_mqtt_identities_instance CHECK (
        instance_id REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
    ),
    CONSTRAINT chk_device_mqtt_identities_authentication CHECK (
        authentication_mode IN ('PASSWORD', 'MTLS')
    ),
    CONSTRAINT chk_device_mqtt_identities_status CHECK (
        identity_status IN ('ACTIVE', 'REVOKED', 'EXPIRED')
    ),
    CONSTRAINT chk_device_mqtt_identities_validity CHECK (
        valid_until IS NULL OR valid_until > valid_from
    ),
    CONSTRAINT chk_device_mqtt_identities_revocation CHECK (
        (identity_status = 'REVOKED' AND revoked_at IS NOT NULL)
        OR (identity_status <> 'REVOKED' AND revoked_at IS NULL)
    ),
    CONSTRAINT chk_device_mqtt_identities_certificate CHECK (
        (authentication_mode = 'PASSWORD' AND certificate_serial IS NULL)
        OR (authentication_mode = 'MTLS' AND CHAR_LENGTH(TRIM(certificate_serial)) > 0)
    )
);

CREATE INDEX idx_device_mqtt_identities_status_validity
    ON device_mqtt_identities (identity_status, valid_until);

CREATE TABLE device_mqtt_commands (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    command_id VARCHAR(80) NOT NULL,
    message_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    device_id BIGINT NOT NULL,
    command_type VARCHAR(50) NOT NULL,
    command_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    payload_json JSON NOT NULL,
    requested_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    published_at DATETIME NULL,
    publication_attempts INT NOT NULL DEFAULT 0,
    last_publication_error VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_mqtt_commands_command UNIQUE (command_id),
    CONSTRAINT uk_device_mqtt_commands_message UNIQUE (message_id),
    CONSTRAINT fk_device_mqtt_commands_device FOREIGN KEY (device_id) REFERENCES devices (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_device_mqtt_commands_message CHECK (
        message_id REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
    ),
    CONSTRAINT chk_device_mqtt_commands_type CHECK (
        command_type IN ('TICKET_ISSUE', 'CONFIGURATION_REFRESH', 'STATUS_REQUEST', 'RESTART')
    ),
    CONSTRAINT chk_device_mqtt_commands_status CHECK (
        command_status IN ('PENDING', 'PUBLISHED', 'PUBLISH_FAILED', 'RECEIVED', 'PROCESSING',
            'COMPLETED', 'FAILED', 'REJECTED', 'EXPIRED')
    ),
    CONSTRAINT chk_device_mqtt_commands_expiry CHECK (expires_at > requested_at),
    CONSTRAINT chk_device_mqtt_commands_attempts CHECK (publication_attempts >= 0),
    CONSTRAINT chk_device_mqtt_commands_publication CHECK (
        (command_status = 'PENDING' AND published_at IS NULL)
        OR command_status <> 'PENDING'
    )
);

CREATE INDEX idx_device_mqtt_commands_device_requested
    ON device_mqtt_commands (device_id, requested_at);
CREATE INDEX idx_device_mqtt_commands_status_expiry
    ON device_mqtt_commands (command_status, expires_at);

CREATE TABLE train_models (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    manufacturer VARCHAR(100) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    series VARCHAR(100) NOT NULL,
    car_count INT NOT NULL,
    capacity_passengers INT NOT NULL,
    max_speed_kmh INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_train_models_model_series UNIQUE (manufacturer, model_name, series),
    CONSTRAINT chk_train_models_car_count CHECK (car_count > 0),
    CONSTRAINT chk_train_models_capacity CHECK (capacity_passengers > 0),
    CONSTRAINT chk_train_models_speed CHECK (max_speed_kmh > 0)
);

CREATE INDEX idx_train_models_active ON train_models (active);

CREATE TABLE depots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    station_id BIGINT NOT NULL,
    capacity INT NOT NULL,
    track_count INT NOT NULL,
    trains_per_track INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_depots_code UNIQUE (code),
    CONSTRAINT uk_depots_name UNIQUE (name),
    CONSTRAINT fk_depots_station FOREIGN KEY (station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_depots_capacity CHECK (capacity > 0),
    CONSTRAINT chk_depots_track_count CHECK (track_count > 0),
    CONSTRAINT chk_depots_trains_per_track CHECK (trains_per_track > 0),
    CONSTRAINT chk_depots_capacity_layout CHECK (capacity = track_count * trains_per_track)
);

CREATE INDEX idx_depots_station ON depots (station_id);
CREATE INDEX idx_depots_active ON depots (active);

CREATE TABLE trains (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    train_model_id BIGINT NOT NULL,
    home_depot_id BIGINT NOT NULL,
    assigned_line_id BIGINT NOT NULL,
    current_line_id BIGINT NULL,
    current_station_id BIGINT NULL,
    next_station_id BIGINT NULL,
    current_depot_id BIGINT NULL,
    direction SMALLINT NULL,
    progress_percentage TINYINT UNSIGNED NOT NULL DEFAULT 0,
    last_position_update_at DATETIME NULL,
    service_started_at DATETIME NULL,
    service_ended_at DATETIME NULL,
    fleet_role VARCHAR(30) NOT NULL,
    dispatch_order INT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DEPOT',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_trains_code UNIQUE (code),
    CONSTRAINT fk_trains_model FOREIGN KEY (train_model_id) REFERENCES train_models (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_trains_home_depot FOREIGN KEY (home_depot_id) REFERENCES depots (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_trains_assigned_line FOREIGN KEY (assigned_line_id) REFERENCES transport_lines (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_trains_current_line FOREIGN KEY (current_line_id) REFERENCES transport_lines (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_trains_current_station FOREIGN KEY (current_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_trains_next_station FOREIGN KEY (next_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_trains_current_depot FOREIGN KEY (current_depot_id) REFERENCES depots (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_trains_direction CHECK (direction IS NULL OR direction IN (-1, 1)),
    CONSTRAINT chk_trains_progress CHECK (progress_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_trains_fleet_role CHECK (
        fleet_role IN ('REGULAR_SERVICE', 'RESERVE', 'HISTORIC')
    ),
    CONSTRAINT chk_trains_dispatch_order CHECK (
        (fleet_role = 'REGULAR_SERVICE' AND dispatch_order IS NOT NULL AND dispatch_order > 0)
        OR (fleet_role IN ('RESERVE', 'HISTORIC') AND dispatch_order IS NULL)
    ),
    CONSTRAINT chk_trains_status CHECK (
        status IN ('IN_SERVICE', 'DEPOT', 'MAINTENANCE', 'STOPPED', 'OUT_OF_SERVICE')
    ),
    CONSTRAINT uk_trains_depot_dispatch_order UNIQUE (home_depot_id, dispatch_order)
);

CREATE INDEX idx_trains_model ON trains (train_model_id);
CREATE INDEX idx_trains_home_depot ON trains (home_depot_id);
CREATE INDEX idx_trains_assigned_line ON trains (assigned_line_id);
CREATE INDEX idx_trains_current_line ON trains (current_line_id);
CREATE INDEX idx_trains_current_station ON trains (current_station_id);
CREATE INDEX idx_trains_next_station ON trains (next_station_id);
CREATE INDEX idx_trains_current_depot ON trains (current_depot_id);
CREATE INDEX idx_trains_status_active ON trains (status, active);
CREATE INDEX idx_trains_fleet_role_active ON trains (fleet_role, active);
CREATE INDEX idx_trains_line_role ON trains (assigned_line_id, fleet_role, active);

CREATE TABLE service_calendars (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    day_type VARCHAR(30) NOT NULL,
    service_start_time TIME NOT NULL,
    service_end_time TIME NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_service_calendars_code UNIQUE (code),
    CONSTRAINT chk_service_calendars_day_type CHECK (
        day_type IN ('WEEKDAY', 'SATURDAY', 'SUNDAY_HOLIDAY')
    ),
    CONSTRAINT chk_service_calendars_hours CHECK (service_start_time <> service_end_time),
    CONSTRAINT chk_service_calendars_validity CHECK (
        valid_until IS NULL OR valid_until >= valid_from
    )
);

CREATE INDEX idx_service_calendars_day_active
    ON service_calendars (day_type, active, valid_from, valid_until);

CREATE TABLE service_periods (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_calendar_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    period_type VARCHAR(30) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    period_order INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_periods_calendar FOREIGN KEY (service_calendar_id)
        REFERENCES service_calendars (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uk_service_periods_calendar_code UNIQUE (service_calendar_id, code),
    CONSTRAINT uk_service_periods_calendar_order UNIQUE (service_calendar_id, period_order),
    CONSTRAINT chk_service_periods_type CHECK (
        period_type IN ('SERVICE_START', 'OFF_PEAK', 'PEAK', 'REGULAR', 'SERVICE_END')
    ),
    CONSTRAINT chk_service_periods_hours CHECK (start_time <> end_time),
    CONSTRAINT chk_service_periods_order CHECK (period_order > 0)
);

CREATE INDEX idx_service_periods_calendar_active
    ON service_periods (service_calendar_id, active, start_time, end_time);

CREATE TABLE line_service_levels (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    line_id BIGINT NOT NULL,
    service_period_id BIGINT NOT NULL,
    headway_seconds INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_line_service_levels_line FOREIGN KEY (line_id) REFERENCES transport_lines (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_line_service_levels_period FOREIGN KEY (service_period_id)
        REFERENCES service_periods (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uk_line_service_levels_line_period UNIQUE (line_id, service_period_id),
    CONSTRAINT chk_line_service_levels_headway CHECK (headway_seconds > 0)
);

CREATE INDEX idx_line_service_levels_period_active
    ON line_service_levels (service_period_id, active);

CREATE TABLE line_depots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    line_id BIGINT NOT NULL,
    depot_id BIGINT NOT NULL,
    dispatch_terminal_station_id BIGINT NOT NULL,
    dispatch_priority INT NOT NULL DEFAULT 1,
    dispatch_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reception_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_line_depots_line FOREIGN KEY (line_id) REFERENCES transport_lines (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_line_depots_depot FOREIGN KEY (depot_id) REFERENCES depots (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_line_depots_dispatch_terminal FOREIGN KEY (dispatch_terminal_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uk_line_depots_line_depot UNIQUE (line_id, depot_id),
    CONSTRAINT chk_line_depots_priority CHECK (dispatch_priority > 0)
);

CREATE INDEX idx_line_depots_depot_active ON line_depots (depot_id, active);

CREATE TABLE ticket_products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    product_type VARCHAR(40) NOT NULL,
    base_price DECIMAL(10, 2) NOT NULL DEFAULT 0,
    price_per_station DECIMAL(10, 2) NOT NULL DEFAULT 0,
    price_per_trip DECIMAL(10, 2) NOT NULL DEFAULT 0,
    price_per_day DECIMAL(10, 2) NOT NULL DEFAULT 0,
    min_trips INT NULL,
    max_trips INT NULL,
    min_days INT NULL,
    max_days INT NULL,
    min_recharge_amount DECIMAL(10, 2) NULL,
    max_recharge_amount DECIMAL(10, 2) NULL,
    requires_origin_destination BOOLEAN NOT NULL DEFAULT FALSE,
    uses_trip_balance BOOLEAN NOT NULL DEFAULT FALSE,
    uses_day_validity BOOLEAN NOT NULL DEFAULT FALSE,
    uses_money_balance BOOLEAN NOT NULL DEFAULT FALSE,
    rechargeable BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_ticket_products_code UNIQUE (code),
    CONSTRAINT chk_ticket_products_prices CHECK (
        base_price >= 0 AND price_per_station >= 0 AND price_per_trip >= 0 AND price_per_day >= 0
    ),
    CONSTRAINT chk_ticket_products_trip_range CHECK (
        (min_trips IS NULL AND max_trips IS NULL)
        OR (min_trips IS NOT NULL AND max_trips IS NOT NULL AND min_trips > 0 AND max_trips >= min_trips)
    ),
    CONSTRAINT chk_ticket_products_day_range CHECK (
        (min_days IS NULL AND max_days IS NULL)
        OR (min_days IS NOT NULL AND max_days IS NOT NULL AND min_days > 0 AND max_days >= min_days)
    ),
    CONSTRAINT chk_ticket_products_recharge_range CHECK (
        (min_recharge_amount IS NULL AND max_recharge_amount IS NULL)
        OR (min_recharge_amount IS NOT NULL AND max_recharge_amount IS NOT NULL
            AND min_recharge_amount > 0 AND max_recharge_amount >= min_recharge_amount)
    )
);

CREATE INDEX idx_ticket_products_type_active ON ticket_products (product_type, active);

CREATE TABLE tickets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    qr_token VARCHAR(255) NOT NULL,
    product_id BIGINT NOT NULL,
    product_type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    origin_station_id BIGINT NULL,
    destination_station_id BIGINT NULL,
    station_count INT NULL,
    route_price_amount DECIMAL(10, 2) NULL,
    purchased_trips INT NULL,
    remaining_trips INT NULL,
    purchased_days INT NULL,
    valid_from DATETIME NULL,
    valid_until DATETIME NULL,
    balance_amount DECIMAL(10, 2) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'EUR',
    passenger_user_id BIGINT NULL,
    imported_to_android BOOLEAN NOT NULL DEFAULT FALSE,
    android_imported_at DATETIME NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_recharged_at DATETIME NULL,
    last_used_at DATETIME NULL,
    status_changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status_before_block VARCHAR(40) NULL,
    blocked_at DATETIME NULL,
    blocked_reason VARCHAR(500) NULL,
    exhausted_at DATETIME NULL,
    expired_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    cancellation_reason VARCHAR(500) NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_tickets_code UNIQUE (code),
    CONSTRAINT uk_tickets_qr_token UNIQUE (qr_token),
    CONSTRAINT fk_tickets_product FOREIGN KEY (product_id) REFERENCES ticket_products (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_origin FOREIGN KEY (origin_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_destination FOREIGN KEY (destination_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_passenger FOREIGN KEY (passenger_user_id) REFERENCES passenger_accounts (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_tickets_product_type CHECK (
        product_type IN ('SINGLE_TRIP', 'MULTI_TRIP', 'TIME_PASS', 'SMART_BALANCE')
    ),
    CONSTRAINT chk_tickets_status CHECK (
        status IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'BLOCKED', 'CANCELLED')
    ),
    CONSTRAINT chk_tickets_status_before_block CHECK (
        status_before_block IS NULL
        OR status_before_block IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED')
    ),
    CONSTRAINT chk_tickets_station_count CHECK (station_count IS NULL OR station_count > 0),
    CONSTRAINT chk_tickets_route_price CHECK (route_price_amount IS NULL OR route_price_amount >= 0),
    CONSTRAINT chk_tickets_trip_balances CHECK (
        (purchased_trips IS NULL AND remaining_trips IS NULL)
        OR (purchased_trips IS NOT NULL AND remaining_trips IS NOT NULL
            AND purchased_trips >= 0 AND remaining_trips BETWEEN 0 AND purchased_trips)
    ),
    CONSTRAINT chk_tickets_days CHECK (purchased_days IS NULL OR purchased_days > 0),
    CONSTRAINT chk_tickets_balance CHECK (balance_amount >= 0),
    CONSTRAINT chk_tickets_validity CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from),
    CONSTRAINT chk_tickets_currency CHECK (currency REGEXP '^[A-Z]{3}$'),
    CONSTRAINT chk_tickets_lock_version CHECK (lock_version >= 0),
    CONSTRAINT chk_tickets_block_reason CHECK (
        blocked_reason IS NULL OR CHAR_LENGTH(TRIM(blocked_reason)) > 0
    ),
    CONSTRAINT chk_tickets_cancellation_reason CHECK (
        cancellation_reason IS NULL OR CHAR_LENGTH(TRIM(cancellation_reason)) > 0
    )
);

CREATE INDEX idx_tickets_product ON tickets (product_id);
CREATE INDEX idx_tickets_status_active ON tickets (status, active);
CREATE INDEX idx_tickets_origin ON tickets (origin_station_id);
CREATE INDEX idx_tickets_destination ON tickets (destination_station_id);
CREATE INDEX idx_tickets_passenger ON tickets (passenger_user_id);
CREATE INDEX idx_tickets_issued_at ON tickets (issued_at);
CREATE INDEX idx_tickets_status_changed_at ON tickets (status, status_changed_at);

CREATE TABLE ticket_supports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    ticket_id BIGINT NOT NULL,
    support_type VARCHAR(20) NOT NULL,
    support_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    serial_number VARCHAR(120) NULL,
    issued_by_device_id BIGINT NULL,
    passenger_account_id BIGINT NULL,
    linking_code_hash VARCHAR(255) NULL,
    linking_code_expires_at DATETIME NULL,
    linked_at DATETIME NULL,
    activated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at DATETIME NULL,
    deactivation_reason VARCHAR(500) NULL,
    replaced_by_support_id BIGINT NULL,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_ticket_supports_code UNIQUE (code),
    CONSTRAINT uk_ticket_supports_serial UNIQUE (serial_number),
    CONSTRAINT fk_ticket_supports_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_supports_device FOREIGN KEY (issued_by_device_id) REFERENCES devices (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_supports_passenger FOREIGN KEY (passenger_account_id) REFERENCES passenger_accounts (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_supports_replacement FOREIGN KEY (replaced_by_support_id) REFERENCES ticket_supports (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_ticket_supports_type CHECK (support_type IN ('PHYSICAL', 'DIGITAL')),
    CONSTRAINT chk_ticket_supports_status CHECK (
        support_status IN ('ACTIVE', 'BLOCKED', 'REVOKED', 'SUPERSEDED')
    ),
    CONSTRAINT chk_ticket_supports_linking_hash CHECK (
        linking_code_hash IS NULL OR CHAR_LENGTH(linking_code_hash) >= 20
    ),
    CONSTRAINT chk_ticket_supports_linking_expiry CHECK (
        linking_code_expires_at IS NULL OR linking_code_expires_at >= issued_at
    ),
    CONSTRAINT chk_ticket_supports_deactivated_at CHECK (
        deactivated_at IS NULL OR deactivated_at >= activated_at
    ),
    CONSTRAINT chk_ticket_supports_deactivation_reason CHECK (
        deactivation_reason IS NULL OR CHAR_LENGTH(TRIM(deactivation_reason)) > 0
    )
);

CREATE INDEX idx_ticket_supports_ticket_status
    ON ticket_supports (ticket_id, support_status);
CREATE INDEX idx_ticket_supports_passenger_status
    ON ticket_supports (passenger_account_id, support_status);
CREATE INDEX idx_ticket_supports_device_issued
    ON ticket_supports (issued_by_device_id, issued_at);
CREATE INDEX idx_ticket_supports_linking_expiry
    ON ticket_supports (linking_code_expires_at);

CREATE TABLE ticket_qr_credentials (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    credential_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ticket_id BIGINT NOT NULL,
    support_id BIGINT NOT NULL,
    credential_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    wrapper_version INT NOT NULL DEFAULT 1,
    signing_key_id VARCHAR(100) NOT NULL,
    token_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NULL,
    revoked_at DATETIME NULL,
    revocation_reason VARCHAR(500) NULL,
    superseded_by_credential_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_ticket_qr_credentials_public_id UNIQUE (credential_id),
    CONSTRAINT uk_ticket_qr_credentials_fingerprint UNIQUE (token_fingerprint),
    CONSTRAINT fk_ticket_qr_credentials_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_qr_credentials_support FOREIGN KEY (support_id) REFERENCES ticket_supports (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_qr_credentials_superseded_by FOREIGN KEY (superseded_by_credential_id)
        REFERENCES ticket_qr_credentials (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_ticket_qr_credentials_public_id CHECK (
        credential_id REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
    ),
    CONSTRAINT chk_ticket_qr_credentials_status CHECK (
        credential_status IN ('ACTIVE', 'REVOKED', 'SUPERSEDED', 'EXPIRED')
    ),
    CONSTRAINT chk_ticket_qr_credentials_wrapper_version CHECK (wrapper_version > 0),
    CONSTRAINT chk_ticket_qr_credentials_fingerprint CHECK (
        token_fingerprint REGEXP '^[0-9a-fA-F]{64}$'
    ),
    CONSTRAINT chk_ticket_qr_credentials_expiry CHECK (
        expires_at IS NULL OR expires_at >= issued_at
    ),
    CONSTRAINT chk_ticket_qr_credentials_revocation_reason CHECK (
        revocation_reason IS NULL OR CHAR_LENGTH(TRIM(revocation_reason)) > 0
    )
);

CREATE INDEX idx_ticket_qr_credentials_ticket_status
    ON ticket_qr_credentials (ticket_id, credential_status);
CREATE INDEX idx_ticket_qr_credentials_support_status
    ON ticket_qr_credentials (support_id, credential_status);
CREATE INDEX idx_ticket_qr_credentials_key_status
    ON ticket_qr_credentials (signing_key_id, credential_status);
CREATE INDEX idx_ticket_qr_credentials_expires_at
    ON ticket_qr_credentials (expires_at);

CREATE TABLE ticket_qr_use_claims (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    validation_reference VARCHAR(150) NOT NULL,
    credential_id BIGINT NOT NULL,
    validation_type VARCHAR(20) NOT NULL,
    device_code VARCHAR(50) NOT NULL,
    station_code VARCHAR(20) NOT NULL,
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_ticket_qr_use_claims_reference UNIQUE (validation_reference),
    CONSTRAINT fk_ticket_qr_use_claims_credential FOREIGN KEY (credential_id)
        REFERENCES ticket_qr_credentials (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_ticket_qr_use_claims_type CHECK (validation_type IN ('ENTRY', 'EXIT')),
    CONSTRAINT chk_ticket_qr_use_claims_status CHECK (claim_status IN ('RECEIVED', 'COMPLETED')),
    CONSTRAINT chk_ticket_qr_use_claims_fingerprint CHECK (
        request_fingerprint REGEXP '^[0-9a-fA-F]{64}$'
    ),
    CONSTRAINT chk_ticket_qr_use_claims_completed CHECK (
        (claim_status = 'RECEIVED' AND completed_at IS NULL)
        OR (claim_status = 'COMPLETED' AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_ticket_qr_use_claims_credential_received
    ON ticket_qr_use_claims (credential_id, received_at);
CREATE INDEX idx_ticket_qr_use_claims_status_received
    ON ticket_qr_use_claims (claim_status, received_at);

CREATE TABLE purchases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    purchase_type VARCHAR(40) NOT NULL,
    ticket_id BIGINT NULL,
    product_id BIGINT NOT NULL,
    purchase_origin VARCHAR(40) NOT NULL,
    purchase_status VARCHAR(40) NOT NULL,
    payment_method VARCHAR(40) NOT NULL,
    device_id BIGINT NULL,
    station_id BIGINT NULL,
    origin_station_id BIGINT NULL,
    destination_station_id BIGINT NULL,
    station_count INT NULL,
    selected_trips INT NULL,
    selected_days INT NULL,
    recharge_amount DECIMAL(10, 2) NULL,
    subtotal_amount DECIMAL(10, 2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'EUR',
    external_reference VARCHAR(150) NULL,
    payment_reference VARCHAR(150) NULL,
    passenger_user_id BIGINT NULL,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchases_code UNIQUE (code),
    CONSTRAINT fk_purchases_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_purchases_product FOREIGN KEY (product_id) REFERENCES ticket_products (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_purchases_device FOREIGN KEY (device_id) REFERENCES devices (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_purchases_station FOREIGN KEY (station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_purchases_origin FOREIGN KEY (origin_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_purchases_destination FOREIGN KEY (destination_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_purchases_counts CHECK (
        (station_count IS NULL OR station_count > 0)
        AND (selected_trips IS NULL OR selected_trips > 0)
        AND (selected_days IS NULL OR selected_days > 0)
    ),
    CONSTRAINT chk_purchases_amounts CHECK (
        (recharge_amount IS NULL OR recharge_amount > 0)
        AND subtotal_amount >= 0 AND total_amount >= 0
    )
);

CREATE INDEX idx_purchases_ticket ON purchases (ticket_id);
CREATE INDEX idx_purchases_product ON purchases (product_id);
CREATE INDEX idx_purchases_status_requested ON purchases (purchase_status, requested_at);
CREATE INDEX idx_purchases_device ON purchases (device_id);
CREATE INDEX idx_purchases_station ON purchases (station_id);
CREATE INDEX idx_purchases_passenger ON purchases (passenger_user_id);
CREATE UNIQUE INDEX uk_purchases_external_reference ON purchases (external_reference);

CREATE TABLE compensatory_ticket_issuances (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    product_id BIGINT NOT NULL,
    target_device_id BIGINT NOT NULL,
    requested_by_operator_id BIGINT NOT NULL,
    issued_ticket_id BIGINT NULL,
    issuance_status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    reason VARCHAR(500) NOT NULL,
    origin_station_id BIGINT NULL,
    destination_station_id BIGINT NULL,
    station_count INT NULL,
    selected_trips INT NULL,
    selected_days INT NULL,
    recharge_amount DECIMAL(10, 2) NULL,
    charged_amount DECIMAL(10, 2) NOT NULL DEFAULT 0,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    failed_at DATETIME NULL,
    failure_reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_compensatory_ticket_issuances_code UNIQUE (code),
    CONSTRAINT uk_compensatory_ticket_issuances_ticket UNIQUE (issued_ticket_id),
    CONSTRAINT fk_compensatory_issuances_product FOREIGN KEY (product_id) REFERENCES ticket_products (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_compensatory_issuances_device FOREIGN KEY (target_device_id) REFERENCES devices (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_compensatory_issuances_operator FOREIGN KEY (requested_by_operator_id)
        REFERENCES operator_accounts (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_compensatory_issuances_ticket FOREIGN KEY (issued_ticket_id) REFERENCES tickets (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_compensatory_issuances_origin FOREIGN KEY (origin_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_compensatory_issuances_destination FOREIGN KEY (destination_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_compensatory_issuances_status CHECK (
        issuance_status IN ('REQUESTED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_compensatory_issuances_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT chk_compensatory_issuances_station_count CHECK (
        station_count IS NULL OR station_count > 0
    ),
    CONSTRAINT chk_compensatory_issuances_trips CHECK (selected_trips IS NULL OR selected_trips > 0),
    CONSTRAINT chk_compensatory_issuances_days CHECK (selected_days IS NULL OR selected_days > 0),
    CONSTRAINT chk_compensatory_issuances_recharge CHECK (recharge_amount IS NULL OR recharge_amount > 0),
    CONSTRAINT chk_compensatory_issuances_free CHECK (charged_amount = 0),
    CONSTRAINT chk_compensatory_issuances_completed_at CHECK (
        completed_at IS NULL OR completed_at >= requested_at
    ),
    CONSTRAINT chk_compensatory_issuances_failed_at CHECK (failed_at IS NULL OR failed_at >= requested_at)
);

CREATE INDEX idx_compensatory_issuances_status_requested
    ON compensatory_ticket_issuances (issuance_status, requested_at);
CREATE INDEX idx_compensatory_issuances_product ON compensatory_ticket_issuances (product_id);
CREATE INDEX idx_compensatory_issuances_device ON compensatory_ticket_issuances (target_device_id);
CREATE INDEX idx_compensatory_issuances_operator ON compensatory_ticket_issuances (requested_by_operator_id);

CREATE TABLE ticket_journeys (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    ticket_id BIGINT NOT NULL,
    entry_validation_id BIGINT NULL,
    exit_validation_id BIGINT NULL,
    entry_station_id BIGINT NOT NULL,
    exit_station_id BIGINT NULL,
    status VARCHAR(40) NOT NULL,
    station_count INT NULL,
    fare_amount DECIMAL(10, 2) NULL,
    currency CHAR(3) NOT NULL DEFAULT 'EUR',
    opened_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME NULL,
    forced_closed_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_ticket_journeys_code UNIQUE (code),
    CONSTRAINT fk_ticket_journeys_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_journeys_entry_station FOREIGN KEY (entry_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_journeys_exit_station FOREIGN KEY (exit_station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_ticket_journeys_station_count CHECK (station_count IS NULL OR station_count > 0),
    CONSTRAINT chk_ticket_journeys_fare CHECK (fare_amount IS NULL OR fare_amount >= 0),
    CONSTRAINT chk_ticket_journeys_closed_at CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

CREATE INDEX idx_ticket_journeys_ticket ON ticket_journeys (ticket_id);
CREATE INDEX idx_ticket_journeys_entry_station ON ticket_journeys (entry_station_id);
CREATE INDEX idx_ticket_journeys_exit_station ON ticket_journeys (exit_station_id);
CREATE INDEX idx_ticket_journeys_status_opened ON ticket_journeys (status, opened_at);

CREATE TABLE ticket_validations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    ticket_id BIGINT NULL,
    journey_id BIGINT NULL,
    validation_type VARCHAR(40) NOT NULL,
    validation_status VARCHAR(40) NOT NULL,
    rejection_reason VARCHAR(80) NULL,
    qr_token VARCHAR(255) NULL,
    station_id BIGINT NULL,
    device_id BIGINT NULL,
    passenger_user_id BIGINT NULL,
    message VARCHAR(500) NULL,
    fare_amount DECIMAL(10, 2) NULL,
    balance_before DECIMAL(10, 2) NULL,
    balance_after DECIMAL(10, 2) NULL,
    remaining_trips_before INT NULL,
    remaining_trips_after INT NULL,
    valid_from DATETIME NULL,
    valid_until DATETIME NULL,
    external_reference VARCHAR(150) NULL,
    payload_json JSON NULL,
    validated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_ticket_validations_code UNIQUE (code),
    CONSTRAINT fk_ticket_validations_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_validations_journey FOREIGN KEY (journey_id) REFERENCES ticket_journeys (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_validations_station FOREIGN KEY (station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_validations_device FOREIGN KEY (device_id) REFERENCES devices (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_ticket_validations_fare CHECK (fare_amount IS NULL OR fare_amount >= 0),
    CONSTRAINT chk_ticket_validations_balances CHECK (
        (balance_before IS NULL OR balance_before >= 0) AND (balance_after IS NULL OR balance_after >= 0)
    ),
    CONSTRAINT chk_ticket_validations_trips CHECK (
        (remaining_trips_before IS NULL OR remaining_trips_before >= 0)
        AND (remaining_trips_after IS NULL OR remaining_trips_after >= 0)
    ),
    CONSTRAINT chk_ticket_validations_validity CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

ALTER TABLE ticket_journeys
    ADD CONSTRAINT fk_ticket_journeys_entry_validation FOREIGN KEY (entry_validation_id)
        REFERENCES ticket_validations (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    ADD CONSTRAINT fk_ticket_journeys_exit_validation FOREIGN KEY (exit_validation_id)
        REFERENCES ticket_validations (id) ON UPDATE CASCADE ON DELETE RESTRICT;

CREATE INDEX idx_ticket_journeys_entry_validation ON ticket_journeys (entry_validation_id);
CREATE INDEX idx_ticket_journeys_exit_validation ON ticket_journeys (exit_validation_id);
CREATE INDEX idx_ticket_validations_ticket ON ticket_validations (ticket_id);
CREATE INDEX idx_ticket_validations_journey ON ticket_validations (journey_id);
CREATE INDEX idx_ticket_validations_status_date ON ticket_validations (validation_status, validated_at);
CREATE INDEX idx_ticket_validations_station ON ticket_validations (station_id);
CREATE INDEX idx_ticket_validations_device ON ticket_validations (device_id);
CREATE INDEX idx_ticket_validations_qr_token ON ticket_validations (qr_token);
CREATE INDEX idx_ticket_validations_external_reference ON ticket_validations (external_reference);

CREATE TABLE ticket_operations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    ticket_id BIGINT NOT NULL,
    operation_type VARCHAR(40) NOT NULL,
    operation_source VARCHAR(40) NOT NULL,
    support_id BIGINT NULL,
    purchase_id BIGINT NULL,
    journey_id BIGINT NULL,
    station_id BIGINT NULL,
    device_id BIGINT NULL,
    passenger_account_id BIGINT NULL,
    external_reference VARCHAR(150) NULL,
    previous_status VARCHAR(40) NULL,
    resulting_status VARCHAR(40) NOT NULL,
    balance_before DECIMAL(10, 2) NULL,
    balance_after DECIMAL(10, 2) NULL,
    remaining_trips_before INT NULL,
    remaining_trips_after INT NULL,
    valid_from_before DATETIME NULL,
    valid_until_before DATETIME NULL,
    valid_from_after DATETIME NULL,
    valid_until_after DATETIME NULL,
    operation_amount DECIMAL(10, 2) NULL,
    currency CHAR(3) NOT NULL DEFAULT 'EUR',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_ticket_operations_code UNIQUE (code),
    CONSTRAINT fk_ticket_operations_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_operations_support FOREIGN KEY (support_id) REFERENCES ticket_supports (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_operations_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_operations_journey FOREIGN KEY (journey_id) REFERENCES ticket_journeys (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_operations_station FOREIGN KEY (station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_operations_device FOREIGN KEY (device_id) REFERENCES devices (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_ticket_operations_passenger FOREIGN KEY (passenger_account_id) REFERENCES passenger_accounts (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_ticket_operations_type CHECK (
        operation_type IN ('ISSUED', 'RECHARGED', 'ENTRY_ACCEPTED', 'EXIT_ACCEPTED',
            'BLOCKED', 'UNBLOCKED', 'CANCELLED', 'SUPPORT_LINKED', 'QR_REVOKED')
    ),
    CONSTRAINT chk_ticket_operations_source CHECK (
        operation_source IN ('SYSTEM', 'RMM_APP', 'TICKET_MACHINE', 'VALIDATOR', 'CONTROL_CENTER')
    ),
    CONSTRAINT chk_ticket_operations_status CHECK (
        resulting_status IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'BLOCKED', 'CANCELLED')
        AND (previous_status IS NULL
            OR previous_status IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'BLOCKED', 'CANCELLED'))
    ),
    CONSTRAINT chk_ticket_operations_balances CHECK (
        (balance_before IS NULL OR balance_before >= 0)
        AND (balance_after IS NULL OR balance_after >= 0)
    ),
    CONSTRAINT chk_ticket_operations_trips CHECK (
        (remaining_trips_before IS NULL OR remaining_trips_before >= 0)
        AND (remaining_trips_after IS NULL OR remaining_trips_after >= 0)
    ),
    CONSTRAINT chk_ticket_operations_amount CHECK (
        operation_amount IS NULL OR operation_amount >= 0
    ),
    CONSTRAINT chk_ticket_operations_currency CHECK (currency REGEXP '^[A-Z]{3}$')
);

CREATE INDEX idx_ticket_operations_ticket_occurred
    ON ticket_operations (ticket_id, occurred_at);
CREATE INDEX idx_ticket_operations_type_occurred
    ON ticket_operations (operation_type, occurred_at);
CREATE INDEX idx_ticket_operations_purchase ON ticket_operations (purchase_id);
CREATE INDEX idx_ticket_operations_journey ON ticket_operations (journey_id);
CREATE INDEX idx_ticket_operations_external_reference
    ON ticket_operations (external_reference);

CREATE TABLE incidents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    incident_category VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    incident_status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_by_operator_id BIGINT NOT NULL,
    assigned_to_operator_id BIGINT NULL,
    affected_line_id BIGINT NULL,
    affected_station_id BIGINT NULL,
    affected_train_id BIGINT NULL,
    affected_device_id BIGINT NULL,
    affected_depot_id BIGINT NULL,
    resolution_summary TEXT NULL,
    opened_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_at DATETIME NULL,
    resolved_at DATETIME NULL,
    closed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_incidents_code UNIQUE (code),
    CONSTRAINT fk_incidents_creator FOREIGN KEY (created_by_operator_id)
        REFERENCES operator_accounts (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_incidents_assignee FOREIGN KEY (assigned_to_operator_id)
        REFERENCES operator_accounts (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_incidents_line FOREIGN KEY (affected_line_id)
        REFERENCES transport_lines (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_incidents_station FOREIGN KEY (affected_station_id)
        REFERENCES stations (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_incidents_train FOREIGN KEY (affected_train_id)
        REFERENCES trains (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_incidents_device FOREIGN KEY (affected_device_id)
        REFERENCES devices (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_incidents_depot FOREIGN KEY (affected_depot_id)
        REFERENCES depots (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_incidents_code CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT chk_incidents_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT chk_incidents_description CHECK (CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT chk_incidents_category CHECK (
        incident_category IN ('SERVICE', 'DEVICE', 'INFRASTRUCTURE', 'TICKETING', 'SECURITY', 'OTHER')
    ),
    CONSTRAINT chk_incidents_priority CHECK (
        priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT chk_incidents_status CHECK (
        incident_status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')
    ),
    CONSTRAINT chk_incidents_resolution CHECK (
        resolution_summary IS NULL OR CHAR_LENGTH(TRIM(resolution_summary)) > 0
    ),
    CONSTRAINT chk_incidents_assigned_at CHECK (assigned_at IS NULL OR assigned_at >= opened_at),
    CONSTRAINT chk_incidents_resolved_at CHECK (resolved_at IS NULL OR resolved_at >= opened_at),
    CONSTRAINT chk_incidents_closed_at CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

CREATE INDEX idx_incidents_status_priority
    ON incidents (incident_status, priority);
CREATE INDEX idx_incidents_category_opened
    ON incidents (incident_category, opened_at);
CREATE INDEX idx_incidents_creator
    ON incidents (created_by_operator_id);
CREATE INDEX idx_incidents_assignee_status
    ON incidents (assigned_to_operator_id, incident_status);
CREATE INDEX idx_incidents_line ON incidents (affected_line_id);
CREATE INDEX idx_incidents_station ON incidents (affected_station_id);
CREATE INDEX idx_incidents_train ON incidents (affected_train_id);
CREATE INDEX idx_incidents_device ON incidents (affected_device_id);
CREATE INDEX idx_incidents_depot ON incidents (affected_depot_id);

CREATE TABLE incident_status_changes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    incident_id BIGINT NOT NULL,
    changed_by_operator_id BIGINT NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    change_note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_incident_status_changes_incident FOREIGN KEY (incident_id)
        REFERENCES incidents (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_incident_status_changes_operator FOREIGN KEY (changed_by_operator_id)
        REFERENCES operator_accounts (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_incident_changes_previous CHECK (
        previous_status IS NULL
        OR previous_status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')
    ),
    CONSTRAINT chk_incident_changes_new CHECK (
        new_status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')
    ),
    CONSTRAINT chk_incident_changes_distinct CHECK (
        previous_status IS NULL OR previous_status <> new_status
    ),
    CONSTRAINT chk_incident_changes_note CHECK (
        change_note IS NULL OR CHAR_LENGTH(TRIM(change_note)) > 0
    )
);

CREATE INDEX idx_incident_status_changes_incident_created
    ON incident_status_changes (incident_id, created_at);
CREATE INDEX idx_incident_status_changes_operator_created
    ON incident_status_changes (changed_by_operator_id, created_at);

CREATE TABLE incident_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    incident_id BIGINT NOT NULL,
    author_operator_id BIGINT NOT NULL,
    comment_text TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_incident_comments_incident FOREIGN KEY (incident_id)
        REFERENCES incidents (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_incident_comments_author FOREIGN KEY (author_operator_id)
        REFERENCES operator_accounts (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_incident_comments_text CHECK (CHAR_LENGTH(TRIM(comment_text)) > 0)
);

CREATE INDEX idx_incident_comments_incident_created
    ON incident_comments (incident_id, created_at);
CREATE INDEX idx_incident_comments_author_created
    ON incident_comments (author_operator_id, created_at);

CREATE TABLE operational_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    log_origin VARCHAR(50) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    severity VARCHAR(30) NOT NULL,
    message VARCHAR(500) NOT NULL,
    device_id BIGINT NULL,
    station_id BIGINT NULL,
    line_id BIGINT NULL,
    train_id BIGINT NULL,
    ticket_id BIGINT NULL,
    purchase_id BIGINT NULL,
    compensatory_issuance_id BIGINT NULL,
    validation_id BIGINT NULL,
    passenger_user_id BIGINT NULL,
    staff_user_id BIGINT NULL,
    incident_id BIGINT NULL,
    external_reference VARCHAR(150) NULL,
    payload_json JSON NULL,
    created_at DATETIME NOT NULL,
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_operational_logs_device FOREIGN KEY (device_id) REFERENCES devices (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_operational_logs_station FOREIGN KEY (station_id) REFERENCES stations (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_operational_logs_line FOREIGN KEY (line_id) REFERENCES transport_lines (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_operational_logs_train FOREIGN KEY (train_id) REFERENCES trains (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_operational_logs_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_operational_logs_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_operational_logs_compensatory_issuance FOREIGN KEY (compensatory_issuance_id)
        REFERENCES compensatory_ticket_issuances (id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_operational_logs_validation FOREIGN KEY (validation_id) REFERENCES ticket_validations (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_operational_logs_incident FOREIGN KEY (incident_id) REFERENCES incidents (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_operational_logs_operator FOREIGN KEY (staff_user_id) REFERENCES operator_accounts (id)
        ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_operational_logs_origin_event ON operational_logs (log_origin, event_type);
CREATE INDEX idx_operational_logs_severity_created ON operational_logs (severity, created_at);
CREATE INDEX idx_operational_logs_device ON operational_logs (device_id);
CREATE INDEX idx_operational_logs_station ON operational_logs (station_id);
CREATE INDEX idx_operational_logs_line ON operational_logs (line_id);
CREATE INDEX idx_operational_logs_train ON operational_logs (train_id);
CREATE INDEX idx_operational_logs_ticket ON operational_logs (ticket_id);
CREATE INDEX idx_operational_logs_purchase ON operational_logs (purchase_id);
CREATE INDEX idx_operational_logs_compensatory_issuance ON operational_logs (compensatory_issuance_id);
CREATE INDEX idx_operational_logs_validation ON operational_logs (validation_id);
CREATE INDEX idx_operational_logs_incident ON operational_logs (incident_id);
CREATE INDEX idx_operational_logs_operator ON operational_logs (staff_user_id);
CREATE UNIQUE INDEX uk_operational_logs_origin_external_reference
    ON operational_logs (log_origin, external_reference);
CREATE INDEX idx_operational_logs_received_at ON operational_logs (received_at);
