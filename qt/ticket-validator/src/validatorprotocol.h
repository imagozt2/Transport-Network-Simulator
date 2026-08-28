#pragma once

#include "validationresult.h"

#include <QByteArray>
#include <QDateTime>
#include <QString>

namespace rmm::validator {

QByteArray buildValidationRequest(
    const QString &deviceCode,
    const QString &validationReference,
    const QString &direction,
    const QString &stationCode,
    const QString &qrValue,
    const QString &messageId,
    const QDateTime &now);

std::optional<ValidationResult> parseValidationResponse(
    const QByteArray &message,
    const QString &deviceCode,
    const QString &awaitedReference,
    QString *error = nullptr);

} // namespace rmm::validator
