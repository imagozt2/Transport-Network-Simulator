USE transport_simulator_db;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @encoding_issues = 0;

SELECT COUNT(*) INTO @database_charset_issues
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'transport_simulator_db'
  AND DEFAULT_CHARACTER_SET_NAME <> 'utf8mb4';

SET @encoding_issues = @encoding_issues + @database_charset_issues;

SELECT COUNT(*) INTO @column_charset_issues
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'transport_simulator_db'
  AND DATA_TYPE IN ('char', 'varchar', 'text', 'tinytext', 'mediumtext', 'longtext')
  AND CHARACTER_SET_NAME NOT IN ('utf8mb4', 'ascii');

SET @encoding_issues = @encoding_issues + @column_charset_issues;

SELECT COUNT(*) INTO @canonical_station_issues
FROM (
    SELECT 'ST004' AS code, 'Ramón y Cajal' AS expected_name
    UNION ALL SELECT 'ST014', 'Museo Marítimo'
    UNION ALL SELECT 'ST017', 'Estadio Olímpico'
    UNION ALL SELECT 'ST020', 'La Galería'
    UNION ALL SELECT 'ST028', 'Vía Aurea'
    UNION ALL SELECT 'ST031', 'Muralla Ibérica'
    UNION ALL SELECT 'ST032', 'San Pedro Apóstol'
    UNION ALL SELECT 'ST034', 'Herrería'
    UNION ALL SELECT 'ST046', 'El Espigón'
) expected
LEFT JOIN stations ON stations.code = expected.code
WHERE stations.id IS NULL
   OR BINARY stations.name <> BINARY expected.expected_name;

SET @encoding_issues = @encoding_issues + @canonical_station_issues;

SELECT COUNT(*) INTO @canonical_line_issues
FROM transport_lines
WHERE code IN ('L1', 'L2', 'L3', 'L4', 'L5', 'L6')
  AND BINARY name <> BINARY CONCAT('Línea ', SUBSTRING(code, 2));

SET @encoding_issues = @encoding_issues + @canonical_line_issues;

SELECT COUNT(*) INTO @mojibake_issues
FROM (
    SELECT name AS checked_text FROM stations
    UNION ALL SELECT name FROM transport_lines
    UNION ALL SELECT color FROM transport_lines
    UNION ALL SELECT name FROM devices
    UNION ALL SELECT name FROM depots
    UNION ALL SELECT name FROM service_calendars
    UNION ALL SELECT name FROM service_periods
    UNION ALL SELECT name FROM ticket_products
    UNION ALL SELECT description FROM ticket_products
) texts
WHERE LOCATE(UNHEX('C383'), CONVERT(checked_text USING binary)) > 0
   OR LOCATE(UNHEX('C382'), CONVERT(checked_text USING binary)) > 0
   OR LOCATE(UNHEX('C3A2'), CONVERT(checked_text USING binary)) > 0
   OR LOCATE(UNHEX('EFBFBD'), CONVERT(checked_text USING binary)) > 0;

SET @encoding_issues = @encoding_issues + @mojibake_issues;

SELECT @encoding_issues AS encoding_issue_count;
