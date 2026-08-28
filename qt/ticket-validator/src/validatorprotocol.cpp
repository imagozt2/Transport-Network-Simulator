#include "validatorprotocol.h"

#include <QJsonDocument>
#include <QJsonObject>

namespace rmm::validator {

QByteArray buildValidationRequest(
    const QString &deviceCode,
    const QString &validationReference,
    const QString &direction,
    const QString &stationCode,
    const QString &qrValue,
    const QString &messageId,
    const QDateTime &now)
{
    const QString timestamp = now.toUTC().toString(Qt::ISODateWithMs);
    const QJsonObject payload{
        {QStringLiteral("validationReference"), validationReference},
        {QStringLiteral("direction"), direction},
        {QStringLiteral("stationCode"), stationCode},
        {QStringLiteral("qrValue"), qrValue},
    };
    const QJsonObject envelope{
        {QStringLiteral("schemaVersion"), 1},
        {QStringLiteral("messageId"), messageId},
        {QStringLiteral("correlationId"), QJsonValue::Null},
        {QStringLiteral("type"), QStringLiteral("ticket.validation-requested")},
        {QStringLiteral("deviceCode"), deviceCode},
        {QStringLiteral("occurredAt"), timestamp},
        {QStringLiteral("sentAt"), timestamp},
        {QStringLiteral("payload"), payload},
    };
    return QJsonDocument(envelope).toJson(QJsonDocument::Compact);
}

std::optional<ValidationResult> parseValidationResponse(
    const QByteArray &message,
    const QString &deviceCode,
    const QString &awaitedReference,
    QString *error)
{
    QJsonParseError parseError;
    const QJsonDocument document = QJsonDocument::fromJson(message, &parseError);
    if (parseError.error != QJsonParseError::NoError || !document.isObject()) {
        if (error != nullptr) *error = QObject::tr("La respuesta MQTT no contiene JSON válido");
        return std::nullopt;
    }

    const QJsonObject envelope = document.object();
    if (envelope.value(QStringLiteral("type")).toString()
            != QStringLiteral("ticket.validation-decided")
        || envelope.value(QStringLiteral("deviceCode")).toString() != deviceCode) {
        return std::nullopt;
    }

    auto result = ValidationResult::fromPayload(
        envelope.value(QStringLiteral("payload")).toObject(), error);
    if (!result.has_value() || result->validationReference != awaitedReference) {
        return std::nullopt;
    }
    return result;
}

} // namespace rmm::validator
