#include "validatorconfiguration.h"

namespace {

QString normalized(
    const QProcessEnvironment &environment,
    const QString &name,
    const QString &defaultValue)
{
    const QString value = environment.value(name, defaultValue).trimmed();
    return value.isEmpty() ? defaultValue : value;
}

} // namespace

ValidatorConfiguration ValidatorConfiguration::fromEnvironment(
    const QProcessEnvironment &environment)
{
    ValidatorConfiguration configuration;
    configuration.stationCode = normalized(
        environment, QStringLiteral("RMM_VALIDATOR_STATION_CODE"), QStringLiteral("ST038"))
                                    .toUpper();
    configuration.stationName = normalized(
        environment, QStringLiteral("RMM_VALIDATOR_STATION_NAME"), QStringLiteral("Acueducto"));

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
    const QString defaultDeviceCode = QStringLiteral("%1%2-01")
        .arg(devicePrefix, configuration.stationCode);
    configuration.deviceCode = normalized(
        environment, QStringLiteral("RMM_VALIDATOR_DEVICE_CODE"), defaultDeviceCode).toUpper();
    if (configuration.valid && !configuration.deviceCode.startsWith(devicePrefix)) {
        configuration.valid = false;
        configuration.error = configuration.mode == ValidatorMode::Entry
            ? QStringLiteral("An ENTRY validator must use an RMM-EN-* device identity")
            : QStringLiteral("An EXIT validator must use an RMM-EX-* device identity");
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
