#include "ticketmachineconfiguration.h"

#include <QRegularExpression>

TicketMachineConfiguration TicketMachineConfiguration::fromEnvironment(
    const QProcessEnvironment &environment)
{
    TicketMachineConfiguration configuration;
    configuration.deviceCode = environment.value(
        QStringLiteral("RMM_TICKET_MACHINE_DEVICE_CODE"),
        QStringLiteral("RMM-TM-ST046-01")).trimmed().toUpper();

    static const QRegularExpression inventoryCode(
        QStringLiteral("^RMM-TM-(ST[0-9]{3})-[0-9]{2}$"));
    const QRegularExpressionMatch identityMatch = inventoryCode.match(configuration.deviceCode);
    if (!identityMatch.hasMatch()) {
        configuration.valid = false;
        configuration.error = QStringLiteral(
            "RMM_TICKET_MACHINE_DEVICE_CODE must identify an RMM-TM-* inventory device");
    } else {
        configuration.stationCode = identityMatch.captured(1);
    }
    return configuration;
}
