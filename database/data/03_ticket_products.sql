USE transport_simulator_db;

INSERT INTO ticket_products (
    code, name, description, product_type,
    base_price, price_per_station, price_per_trip, price_per_day,
    min_trips, max_trips, min_days, max_days,
    min_recharge_amount, max_recharge_amount,
    requires_origin_destination, uses_trip_balance, uses_day_validity,
    uses_money_balance, rechargeable, active
) VALUES
(
    'SINGLE_TRIP', 'Billete sencillo',
    'Billete de un solo viaje cuyo precio depende de las estaciones de origen y destino.',
    'SINGLE_TRIP', 0.50, 0.05, 0, 0,
    NULL, NULL, NULL, NULL, NULL, NULL,
    TRUE, FALSE, FALSE, FALSE, TRUE, TRUE
),
(
    'MULTI_TRIP', 'Billete múltiple',
    'Billete recargable con una cantidad configurable de entre 2 y 30 viajes.',
    'MULTI_TRIP', 0, 0, 1.00, 0,
    2, 30, NULL, NULL, NULL, NULL,
    FALSE, TRUE, FALSE, FALSE, TRUE, TRUE
),
(
    'TIME_PASS', 'Billete temporal',
    'Billete recargable con una duración configurable de entre 2 y 30 días.',
    'TIME_PASS', 0, 0, 0, 2.00,
    NULL, NULL, 2, 30, NULL, NULL,
    FALSE, FALSE, TRUE, FALSE, TRUE, TRUE
),
(
    'SMART_BALANCE', 'Billete inteligente',
    'Billete recargable con saldo cuyo coste se calcula al validar la salida.',
    'SMART_BALANCE', 0.25, 0.05, 0, 0,
    NULL, NULL, NULL, NULL, 1.00, 100.00,
    FALSE, FALSE, FALSE, TRUE, TRUE, TRUE
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description), product_type = VALUES(product_type),
    base_price = VALUES(base_price), price_per_station = VALUES(price_per_station),
    price_per_trip = VALUES(price_per_trip), price_per_day = VALUES(price_per_day),
    min_trips = VALUES(min_trips), max_trips = VALUES(max_trips),
    min_days = VALUES(min_days), max_days = VALUES(max_days),
    min_recharge_amount = VALUES(min_recharge_amount), max_recharge_amount = VALUES(max_recharge_amount),
    requires_origin_destination = VALUES(requires_origin_destination),
    uses_trip_balance = VALUES(uses_trip_balance), uses_day_validity = VALUES(uses_day_validity),
    uses_money_balance = VALUES(uses_money_balance), rechargeable = VALUES(rechargeable), active = VALUES(active);
