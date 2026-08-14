#pragma once

#include <QByteArray>
#include <QDateTime>
#include <QMetaType>
#include <QString>
#include <QVariantMap>

struct TicketIssuanceRequest
{
    QString productCode;
    QString originStationCode;
    QString destinationStationCode;
    int quantity = 0;
    double rechargeAmount = 0.0;
    double paidAmount = 0.0;
};

struct TicketRechargeRequest
{
    QString qrValue;
    QString originStationCode;
    QString destinationStationCode;
    int trips = 0;
    int days = 0;
    double balanceAmount = 0.0;
    double paidAmount = 0.0;
    QString productType;
};

struct TicketRechargeResult
{
    bool valid = false;
    QString rechargeReference;
    QString rechargeCode;
    QString ticketCode;
    QString productType;
    QString ticketStatus;
    QString currency;
    int remainingTrips = 0;
    QString validUntil;
    double balanceAmount = 0.0;
    double totalAmount = 0.0;
};

Q_DECLARE_METATYPE(TicketIssuanceRequest)

namespace rmm::ticketmachine {

enum class IssueCommandResult
{
    Ignored,
    Invalid,
    Regular,
    Compensatory
};

struct IssueCommand
{
    IssueCommandResult result = IssueCommandResult::Ignored;
    bool compensatory = false;
    QString commandId;
    QString issuanceCode;
    QString purchaseReference;
    QString ticketCode;
    QByteArray qrPng;
    QString qrValue;
    QString linkingCode;
};

QByteArray buildPurchaseRequest(
    const TicketIssuanceRequest &request,
    const QString &deviceCode,
    const QString &purchaseReference,
    const QString &messageId,
    const QDateTime &now);

QByteArray buildRechargeRequest(
    const TicketRechargeRequest &request,
    const QString &deviceCode,
    const QString &rechargeReference,
    const QString &messageId,
    const QDateTime &now);

TicketRechargeResult parseRechargeResponse(
    const QByteArray &message,
    const QString &awaitedReference);

IssueCommand parseIssueCommand(
    const QByteArray &message,
    const QString &awaitedReference,
    const QDateTime &now);

QByteArray buildOperationEvent(
    const QString &deviceCode,
    const QString &eventCode,
    const QString &purchaseReference,
    const QString &ticketCode,
    const QString &resultCode,
    const QString &messageId,
    const QDateTime &now,
    const QVariantMap &extraDetails = {});

QByteArray buildCommandAcknowledgement(
    const QString &deviceCode,
    const QString &commandId,
    const QString &issuanceCode,
    const QString &status,
    const QString &resultCode,
    const QString &messageId,
    const QDateTime &now);

} // namespace rmm::ticketmachine
