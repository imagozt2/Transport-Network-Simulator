#pragma once

#include <QProcessEnvironment>
#include <QString>

struct TicketMachineConfiguration
{
    QString deviceCode;
    QString stationCode;
    bool valid = true;
    QString error;

    static TicketMachineConfiguration fromEnvironment(
        const QProcessEnvironment &environment);
};
