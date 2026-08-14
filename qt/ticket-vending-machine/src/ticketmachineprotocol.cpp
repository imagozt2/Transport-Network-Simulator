#include "ticketmachineprotocol.h"

#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonParseError>
#include <QJsonValue>

namespace {

QString timestamp(const QDateTime &value)
{
    return value.toUTC().toString(Qt::ISODateWithMs);
}

QJsonObject envelope(
    const QString &messageId,
    const QJsonValue &correlationId,
    const QString &type,
    const QString &deviceCode,
    const QDateTime &now,
    const QJsonObject &payload)
{
    const QString sentAt = timestamp(now);
    return {
        {QStringLiteral("schemaVersion"), 1},
        {QStringLiteral("messageId"), messageId},
        {QStringLiteral("correlationId"), correlationId},
        {QStringLiteral("type"), type},
        {QStringLiteral("deviceCode"), deviceCode},
        {QStringLiteral("occurredAt"), sentAt},
        {QStringLiteral("sentAt"), sentAt},
        {QStringLiteral("payload"), payload},
    };
}

} // namespace

namespace rmm::ticketmachine {

QByteArray buildPurchaseRequest(
    const TicketIssuanceRequest &request,
    const QString &deviceCode,
    const QString &purchaseReference,
    const QString &messageId,
    const QDateTime &now)
{
    QJsonObject configuration;
    if (!request.originStationCode.isEmpty()) {
        configuration.insert(QStringLiteral("originStationCode"), request.originStationCode);
        configuration.insert(QStringLiteral("destinationStationCode"), request.destinationStationCode);
    } else if (request.quantity > 0) {
        configuration.insert(QStringLiteral("quantity"), request.quantity);
    } else {
        configuration.insert(QStringLiteral("rechargeAmount"), request.rechargeAmount);
    }
    const QJsonObject payload{
        {QStringLiteral("purchaseReference"), purchaseReference},
        {QStringLiteral("productCode"), request.productCode},
        {QStringLiteral("paymentMethod"), QStringLiteral("SIMULATED")},
        {QStringLiteral("paidAmount"), request.paidAmount},
        {QStringLiteral("currency"), QStringLiteral("EUR")},
        {QStringLiteral("configuration"), configuration},
    };
    return QJsonDocument(envelope(
        messageId, QJsonValue::Null, QStringLiteral("ticket.purchase-requested"),
        deviceCode, now, payload)).toJson(QJsonDocument::Compact);
}

IssueCommand parseIssueCommand(
    const QByteArray &message,
    const QString &awaitedReference,
    const QDateTime &now)
{
    IssueCommand result;
    QJsonParseError parseError;
    const auto document = QJsonDocument::fromJson(message, &parseError);
    if (parseError.error != QJsonParseError::NoError || !document.isObject()) {
        return result;
    }
    const auto root = document.object();
    if (root.value(QStringLiteral("schemaVersion")).toInt() != 1
            || root.value(QStringLiteral("type")).toString()
                != QStringLiteral("ticket.issue-command")) {
        return result;
    }
    const auto payload = root.value(QStringLiteral("payload")).toObject();
    result.commandId = payload.value(QStringLiteral("commandId")).toString();
    result.issuanceCode = payload.value(QStringLiteral("issuanceCode")).toString();
    result.purchaseReference = payload.value(QStringLiteral("purchaseReference")).toString();
    const QString issuanceKind = payload.value(QStringLiteral("issuanceKind")).toString();
    result.compensatory = issuanceKind == QStringLiteral("COMPENSATORY");
    const QDateTime expiresAt = QDateTime::fromString(
        payload.value(QStringLiteral("expiresAt")).toString(), Qt::ISODate);
    if (result.commandId.isEmpty() || !expiresAt.isValid() || expiresAt < now.toUTC()
            || (!result.compensatory && issuanceKind != QStringLiteral("PURCHASE"))
            || (!result.compensatory && result.purchaseReference != awaitedReference)) {
        return {};
    }
    const auto ticket = payload.value(QStringLiteral("ticket")).toObject();
    result.ticketCode = ticket.value(QStringLiteral("ticketCode")).toString();
    result.qrPng = QByteArray::fromBase64(
        ticket.value(QStringLiteral("qrPngBase64")).toString().toLatin1());
    result.qrValue = ticket.value(QStringLiteral("qrValue")).toString();
    result.linkingCode = ticket.value(QStringLiteral("linkingCode")).toString();
    result.result = result.ticketCode.isEmpty() || result.qrValue.isEmpty() || result.qrPng.isEmpty()
        ? IssueCommandResult::Invalid
        : (result.compensatory ? IssueCommandResult::Compensatory : IssueCommandResult::Regular);
    return result;
}

QByteArray buildOperationEvent(
    const QString &deviceCode,
    const QString &eventCode,
    const QString &purchaseReference,
    const QString &ticketCode,
    const QString &resultCode,
    const QString &messageId,
    const QDateTime &now)
{
    QJsonObject details;
    if (!purchaseReference.isEmpty()) details.insert(QStringLiteral("purchaseReference"), purchaseReference);
    if (!ticketCode.isEmpty()) details.insert(QStringLiteral("ticketCode"), ticketCode);
    if (!resultCode.isEmpty()) details.insert(QStringLiteral("resultCode"), resultCode);
    const QJsonObject payload{
        {QStringLiteral("eventCode"), eventCode},
        {QStringLiteral("severity"), eventCode == QStringLiteral("TICKET_PURCHASE_FAILED")
            ? QStringLiteral("ERROR") : QStringLiteral("INFO")},
        {QStringLiteral("details"), details},
    };
    return QJsonDocument(envelope(
        messageId,
        purchaseReference.isEmpty() ? QJsonValue::Null : QJsonValue(purchaseReference),
        QStringLiteral("device.operation-event"), deviceCode, now, payload))
        .toJson(QJsonDocument::Compact);
}

QByteArray buildCommandAcknowledgement(
    const QString &deviceCode,
    const QString &commandId,
    const QString &issuanceCode,
    const QString &status,
    const QString &resultCode,
    const QString &messageId,
    const QDateTime &now)
{
    const QString completedAt = timestamp(now);
    const QJsonObject payload{
        {QStringLiteral("commandId"), commandId},
        {QStringLiteral("issuanceCode"), issuanceCode},
        {QStringLiteral("status"), status},
        {QStringLiteral("resultCode"), resultCode},
        {QStringLiteral("completedAt"), completedAt},
    };
    return QJsonDocument(envelope(
        messageId, commandId, QStringLiteral("ticket.issue-acknowledged"),
        deviceCode, now, payload)).toJson(QJsonDocument::Compact);
}

} // namespace rmm::ticketmachine
