#include "validatorconfiguration.h"

#include <QHash>
#include <QRegularExpression>

namespace {

QString normalized(
    const QProcessEnvironment &environment,
    const QString &name,
    const QString &defaultValue)
{
    const QString value = environment.value(name, defaultValue).trimmed();
    return value.isEmpty() ? defaultValue : value;
}

QString stationNameForCode(const QString &code)
{
    static const QHash<QString, QString> stations {
        {QStringLiteral("ST001"), QStringLiteral("Aeropuerto")},
        {QStringLiteral("ST002"), QStringLiteral("HUB Industrial Norte")},
        {QStringLiteral("ST003"), QStringLiteral("Ensanche Nuevo")},
        {QStringLiteral("ST004"), QStringLiteral("Ramón y Cajal")},
        {QStringLiteral("ST005"), QStringLiteral("Gueto Sur")},
        {QStringLiteral("ST006"), QStringLiteral("Miguel de Cervantes")},
        {QStringLiteral("ST007"), QStringLiteral("Gueto Oeste")},
        {QStringLiteral("ST008"), QStringLiteral("Gueto Este")},
        {QStringLiteral("ST009"), QStringLiteral("Alfonso X")},
        {QStringLiteral("ST010"), QStringLiteral("Gueto Norte")},
        {QStringLiteral("ST011"), QStringLiteral("Espartales")},
        {QStringLiteral("ST012"), QStringLiteral("El Muro del Gueto")},
        {QStringLiteral("ST013"), QStringLiteral("Las Salinas")},
        {QStringLiteral("ST014"), QStringLiteral("Museo Marítimo")},
        {QStringLiteral("ST015"), QStringLiteral("Paseo Marítimo")},
        {QStringLiteral("ST016"), QStringLiteral("Teatro Nacional")},
        {QStringLiteral("ST017"), QStringLiteral("Estadio Olímpico")},
        {QStringLiteral("ST018"), QStringLiteral("Ribera Sur")},
        {QStringLiteral("ST019"), QStringLiteral("Las Torres")},
        {QStringLiteral("ST020"), QStringLiteral("La Galería")},
        {QStringLiteral("ST021"), QStringLiteral("Puerta Medieval")},
        {QStringLiteral("ST022"), QStringLiteral("El Reposo")},
        {QStringLiteral("ST023"), QStringLiteral("Las Fuentes")},
        {QStringLiteral("ST024"), QStringLiteral("San Vicente")},
        {QStringLiteral("ST025"), QStringLiteral("Santa Rita")},
        {QStringLiteral("ST026"), QStringLiteral("Ribera Norte")},
        {QStringLiteral("ST027"), QStringLiteral("Plaza de la Merced")},
        {QStringLiteral("ST028"), QStringLiteral("Vía Aurea")},
        {QStringLiteral("ST029"), QStringLiteral("Los Lavaderos")},
        {QStringLiteral("ST030"), QStringLiteral("Plaza de la Mina")},
        {QStringLiteral("ST031"), QStringLiteral("Muralla Ibérica")},
        {QStringLiteral("ST032"), QStringLiteral("San Pedro Apóstol")},
        {QStringLiteral("ST033"), QStringLiteral("San Jorge")},
        {QStringLiteral("ST034"), QStringLiteral("Herrería")},
        {QStringLiteral("ST035"), QStringLiteral("Los Conventos")},
        {QStringLiteral("ST036"), QStringLiteral("Complejo Hospitalario")},
        {QStringLiteral("ST037"), QStringLiteral("Puerto Fluvial")},
        {QStringLiteral("ST038"), QStringLiteral("Acueducto")},
        {QStringLiteral("ST039"), QStringLiteral("Puerta de Santiago")},
        {QStringLiteral("ST040"), QStringLiteral("Parque de la Cultura")},
        {QStringLiteral("ST041"), QStringLiteral("El Arrabal")},
        {QStringLiteral("ST042"), QStringLiteral("Los Pozos")},
        {QStringLiteral("ST043"), QStringLiteral("Cuatro Caminos")},
        {QStringLiteral("ST044"), QStringLiteral("Pazos Reales")},
        {QStringLiteral("ST045"), QStringLiteral("Los Molinos")},
        {QStringLiteral("ST046"), QStringLiteral("El Espigón")},
        {QStringLiteral("ST047"), QStringLiteral("Zona Universitaria")},
        {QStringLiteral("ST048"), QStringLiteral("Puerto Olímpico")},
        {QStringLiteral("ST049"), QStringLiteral("HUB Industrial Este")},
        {QStringLiteral("ST050"), QStringLiteral("HUB Industrial Oeste")}
    };
    return stations.value(code);
}

} // namespace

ValidatorConfiguration ValidatorConfiguration::fromEnvironment(
    const QProcessEnvironment &environment)
{
    ValidatorConfiguration configuration;
    const QString mode = normalized(
        environment, QStringLiteral("RMM_VALIDATOR_MODE"), QStringLiteral("ENTRY")).toUpper();
    if (mode == QStringLiteral("ENTRY")) {
        configuration.mode = ValidatorMode::Entry;
    } else if (mode == QStringLiteral("EXIT")) {
        configuration.mode = ValidatorMode::Exit;
    } else {
        configuration.valid = false;
        configuration.error = QStringLiteral("RMM_VALIDATOR_MODE must be ENTRY or EXIT");
    }

    const QString devicePrefix = configuration.mode == ValidatorMode::Entry
        ? QStringLiteral("RMM-EN-") : QStringLiteral("RMM-EX-");
    const QString defaultDeviceCode = QStringLiteral("%1ST046-01").arg(devicePrefix);
    configuration.deviceCode = normalized(
        environment, QStringLiteral("RMM_VALIDATOR_DEVICE_CODE"), defaultDeviceCode).toUpper();

    const QRegularExpression identityPattern(
        QStringLiteral("^%1(ST[0-9]{3})-[0-9]{2}$")
            .arg(QRegularExpression::escape(devicePrefix)));
    const QRegularExpressionMatch identityMatch = identityPattern.match(configuration.deviceCode);
    if (configuration.valid && !identityMatch.hasMatch()) {
        configuration.valid = false;
        configuration.error = configuration.mode == ValidatorMode::Entry
            ? QStringLiteral("An ENTRY validator must use an RMM-EN-* device identity")
            : QStringLiteral("An EXIT validator must use an RMM-EX-* device identity");
    }

    if (configuration.valid) {
        configuration.stationCode = identityMatch.captured(1);
        configuration.stationName = stationNameForCode(configuration.stationCode);
        if (configuration.stationName.isEmpty()) {
            configuration.valid = false;
            configuration.error = QStringLiteral(
                "RMM_VALIDATOR_DEVICE_CODE references a station outside the RMM inventory");
        }
    }

    const QString configuredStationCode = environment
        .value(QStringLiteral("RMM_VALIDATOR_STATION_CODE")).trimmed().toUpper();
    const QString configuredStationName = environment
        .value(QStringLiteral("RMM_VALIDATOR_STATION_NAME")).trimmed();
    if (configuration.valid && !configuredStationCode.isEmpty()
        && configuredStationCode != configuration.stationCode) {
        configuration.valid = false;
        configuration.error = QStringLiteral(
            "RMM_VALIDATOR_STATION_CODE does not match the inventoried device identity");
    }
    if (configuration.valid && !configuredStationName.isEmpty()
        && configuredStationName != configuration.stationName) {
        configuration.valid = false;
        configuration.error = QStringLiteral(
            "RMM_VALIDATOR_STATION_NAME does not match the station inventory");
    }
    return configuration;
}

QString ValidatorConfiguration::modeCode() const
{
    return mode == ValidatorMode::Entry ? QStringLiteral("ENTRY") : QStringLiteral("EXIT");
}

bool ValidatorConfiguration::isEntry() const
{
    return mode == ValidatorMode::Entry;
}
