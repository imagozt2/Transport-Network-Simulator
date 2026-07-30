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
    account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
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
        account_status IN ('ACTIVE', 'BLOCKED', 'DISABLED')
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
        previous_status IN ('ACTIVE', 'BLOCKED', 'DISABLED')
    ),
    CONSTRAINT chk_passenger_status_changes_new CHECK (
        new_status IN ('ACTIVE', 'BLOCKED', 'DISABLED')
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
    passenger_user_id BIGINT NULL,
    imported_to_android BOOLEAN NOT NULL DEFAULT FALSE,
    android_imported_at DATETIME NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_recharged_at DATETIME NULL,
    last_used_at DATETIME NULL,
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
    CONSTRAINT chk_tickets_station_count CHECK (station_count IS NULL OR station_count > 0),
    CONSTRAINT chk_tickets_route_price CHECK (route_price_amount IS NULL OR route_price_amount >= 0),
    CONSTRAINT chk_tickets_trip_balances CHECK (
        (purchased_trips IS NULL AND remaining_trips IS NULL)
        OR (purchased_trips IS NOT NULL AND remaining_trips IS NOT NULL
            AND purchased_trips >= 0 AND remaining_trips BETWEEN 0 AND purchased_trips)
    ),
    CONSTRAINT chk_tickets_days CHECK (purchased_days IS NULL OR purchased_days > 0),
    CONSTRAINT chk_tickets_balance CHECK (balance_amount >= 0),
    CONSTRAINT chk_tickets_validity CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

CREATE INDEX idx_tickets_product ON tickets (product_id);
CREATE INDEX idx_tickets_status_active ON tickets (status, active);
CREATE INDEX idx_tickets_origin ON tickets (origin_station_id);
CREATE INDEX idx_tickets_destination ON tickets (destination_station_id);
CREATE INDEX idx_tickets_passenger ON tickets (passenger_user_id);
CREATE INDEX idx_tickets_issued_at ON tickets (issued_at);

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
CREATE INDEX idx_purchases_external_reference ON purchases (external_reference);

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
    CONSTRAINT fk_operational_logs_validation FOREIGN KEY (validation_id) REFERENCES ticket_validations (id)
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
CREATE INDEX idx_operational_logs_validation ON operational_logs (validation_id);
CREATE INDEX idx_operational_logs_operator ON operational_logs (staff_user_id);
CREATE UNIQUE INDEX uk_operational_logs_origin_external_reference
    ON operational_logs (log_origin, external_reference);
CREATE INDEX idx_operational_logs_received_at ON operational_logs (received_at);
