#pragma once

#include <QProcessEnvironment>
#include <QString>

enum class ValidatorMode
{
    Entry,
    Exit
};

struct ValidatorConfiguration
{
    QString deviceCode;
    QString stationCode;
    QString stationName;
    ValidatorMode mode = ValidatorMode::Entry;
    bool valid = true;
    QString error;

    [[nodiscard]] static ValidatorConfiguration fromEnvironment(
        const QProcessEnvironment &environment = QProcessEnvironment::systemEnvironment());
    [[nodiscard]] QString modeCode() const;
    [[nodiscard]] bool isEntry() const;
};
