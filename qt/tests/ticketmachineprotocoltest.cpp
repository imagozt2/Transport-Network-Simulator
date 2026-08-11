#include "ticketmachineprotocol.h"

#include <QJsonDocument>
#include <QJsonObject>
#include <QTest>

using rmm::ticketmachine::IssueCommandResult;

namespace {

const QDateTime Now = QDateTime::fromString(
    QStringLiteral("2026-08-11T12:00:00.000Z"), Qt::ISODateWithMs);

QJsonObject object(const QByteArray &json)
{
    return QJsonDocument::fromJson(json).object();
}

QByteArray issueCommand(
    const QString &purchaseReference,
    const QString &issuanceKind,
    const QDateTime &expiresAt,
    bool validTicket = true)
{
    const QJsonObject ticket{
        {QStringLiteral("ticketCode"), validTicket ? QStringLiteral("RMM-TICKET-001") : QString()},
        {QStringLiteral("qrValue"), validTicket ? QStringLiteral("signed-qr") : QString()},
        {QStringLiteral("qrPngBase64"), validTicket
            ? QString::fromLatin1(QByteArray("png-data").toBase64()) : QString()},
        {QStringLiteral("linkingCode"), QStringLiteral("LINK1234")},
    };
    const QJsonObject payload{
        {QStringLiteral("commandId"), QStringLiteral("command-1")},
        {QStringLiteral("issuanceCode"), QStringLiteral("issuance-1")},
        {QStringLiteral("purchaseReference"), purchaseReference},
        {QStringLiteral("issuanceKind"), issuanceKind},
        {QStringLiteral("expiresAt"), expiresAt.toUTC().toString(Qt::ISODateWithMs)},
        {QStringLiteral("ticket"), ticket},
    };
    return QJsonDocument(QJsonObject{
        {QStringLiteral("type"), QStringLiteral("ticket.issue-command")},
        {QStringLiteral("payload"), payload},
    }).toJson(QJsonDocument::Compact);
}

} // namespace

class TicketMachineProtocolTest final : public QObject
{
    Q_OBJECT

private slots:
    void buildsPurchaseConfigurations_data();
    void buildsPurchaseConfigurations();
    void acceptsMatchingRegularIssuance();
    void acceptsCompensatoryIssuanceWithoutPurchase();
    void rejectsInvalidAndExpiredIssuances();
    void buildsOperationalEventsAndAcknowledgements();
    void completesARegularPurchaseContract();
};

void TicketMachineProtocolTest::buildsPurchaseConfigurations_data()
{
    QTest::addColumn<TicketIssuanceRequest>("request");
    QTest::addColumn<QString>("field");
    QTest::addColumn<QVariant>("value");

    QTest::newRow("single-trip")
        << TicketIssuanceRequest{QStringLiteral("SINGLE_TRIP"), QStringLiteral("ST001"),
                                 QStringLiteral("ST010"), 0, 0.0, 1.0}
        << QStringLiteral("originStationCode") << QVariant(QStringLiteral("ST001"));
    QTest::newRow("multi-trip")
        << TicketIssuanceRequest{QStringLiteral("MULTI_TRIP"), {}, {}, 10, 0.0, 10.0}
        << QStringLiteral("quantity") << QVariant(10);
    QTest::newRow("smart-balance")
        << TicketIssuanceRequest{QStringLiteral("SMART_BALANCE"), {}, {}, 0, 20.0, 20.0}
        << QStringLiteral("rechargeAmount") << QVariant(20.0);
}

void TicketMachineProtocolTest::buildsPurchaseConfigurations()
{
    QFETCH(TicketIssuanceRequest, request);
    QFETCH(QString, field);
    QFETCH(QVariant, value);

    const auto envelope = object(rmm::ticketmachine::buildPurchaseRequest(
        request, QStringLiteral("RMM-SALE-ST001-01"), QStringLiteral("purchase-1"),
        QStringLiteral("message-1"), Now));
    const auto payload = envelope.value(QStringLiteral("payload")).toObject();
    const auto configuration = payload.value(QStringLiteral("configuration")).toObject();

    QCOMPARE(envelope.value(QStringLiteral("type")).toString(),
             QStringLiteral("ticket.purchase-requested"));
    QCOMPARE(envelope.value(QStringLiteral("deviceCode")).toString(),
             QStringLiteral("RMM-SALE-ST001-01"));
    QCOMPARE(payload.value(QStringLiteral("purchaseReference")).toString(),
             QStringLiteral("purchase-1"));
    QCOMPARE(configuration.value(field).toVariant(), value);
}

void TicketMachineProtocolTest::acceptsMatchingRegularIssuance()
{
    const auto command = rmm::ticketmachine::parseIssueCommand(
        issueCommand(QStringLiteral("purchase-1"), QStringLiteral("PURCHASE"), Now.addSecs(60)),
        QStringLiteral("purchase-1"), Now);

    QCOMPARE(command.result, IssueCommandResult::Regular);
    QCOMPARE(command.ticketCode, QStringLiteral("RMM-TICKET-001"));
    QCOMPARE(command.qrPng, QByteArray("png-data"));
    QCOMPARE(command.linkingCode, QStringLiteral("LINK1234"));
}

void TicketMachineProtocolTest::acceptsCompensatoryIssuanceWithoutPurchase()
{
    const auto command = rmm::ticketmachine::parseIssueCommand(
        issueCommand({}, QStringLiteral("COMPENSATORY"), Now.addSecs(60)), {}, Now);

    QCOMPARE(command.result, IssueCommandResult::Compensatory);
    QVERIFY(command.compensatory);
    QCOMPARE(command.issuanceCode, QStringLiteral("issuance-1"));
}

void TicketMachineProtocolTest::rejectsInvalidAndExpiredIssuances()
{
    const auto invalid = rmm::ticketmachine::parseIssueCommand(
        issueCommand({}, QStringLiteral("COMPENSATORY"), Now.addSecs(60), false), {}, Now);
    QCOMPARE(invalid.result, IssueCommandResult::Invalid);
    QVERIFY(invalid.compensatory);

    const auto expired = rmm::ticketmachine::parseIssueCommand(
        issueCommand(QStringLiteral("purchase-1"), QStringLiteral("PURCHASE"), Now.addSecs(-1)),
        QStringLiteral("purchase-1"), Now);
    QCOMPARE(expired.result, IssueCommandResult::Ignored);

    const auto unrelated = rmm::ticketmachine::parseIssueCommand(
        issueCommand(QStringLiteral("purchase-2"), QStringLiteral("PURCHASE"), Now.addSecs(60)),
        QStringLiteral("purchase-1"), Now);
    QCOMPARE(unrelated.result, IssueCommandResult::Ignored);
}

void TicketMachineProtocolTest::buildsOperationalEventsAndAcknowledgements()
{
    const auto event = object(rmm::ticketmachine::buildOperationEvent(
        QStringLiteral("RMM-SALE-ST001-01"), QStringLiteral("TICKET_PURCHASE_FAILED"),
        QStringLiteral("purchase-1"), {}, QStringLiteral("MQTT_TIMEOUT"),
        QStringLiteral("event-1"), Now));
    QCOMPARE(event.value(QStringLiteral("payload")).toObject()
                 .value(QStringLiteral("severity")).toString(), QStringLiteral("ERROR"));
    QCOMPARE(event.value(QStringLiteral("correlationId")).toString(),
             QStringLiteral("purchase-1"));

    const auto acknowledgement = object(rmm::ticketmachine::buildCommandAcknowledgement(
        QStringLiteral("RMM-SALE-ST001-01"), QStringLiteral("command-1"),
        QStringLiteral("issuance-1"), QStringLiteral("COMPLETED"),
        QStringLiteral("TICKET_PRESENTED"), QStringLiteral("ack-1"), Now));
    const auto payload = acknowledgement.value(QStringLiteral("payload")).toObject();
    QCOMPARE(acknowledgement.value(QStringLiteral("type")).toString(),
             QStringLiteral("ticket.issue-acknowledged"));
    QCOMPARE(payload.value(QStringLiteral("status")).toString(), QStringLiteral("COMPLETED"));
    QCOMPARE(payload.value(QStringLiteral("resultCode")).toString(),
             QStringLiteral("TICKET_PRESENTED"));
}

void TicketMachineProtocolTest::completesARegularPurchaseContract()
{
    const QString deviceCode = QStringLiteral("RMM-SALE-ST001-01");
    const QString purchaseReference = QStringLiteral("9561ad31-6273-42d9-b76f-2dabb0b60955");
    const TicketIssuanceRequest request{
        QStringLiteral("SINGLE_TRIP"),
        QStringLiteral("ST001"),
        QStringLiteral("ST007"),
        0,
        0.0,
        0.85,
    };

    const auto purchaseEnvelope = object(rmm::ticketmachine::buildPurchaseRequest(
        request, deviceCode, purchaseReference,
        QStringLiteral("73bb91e8-b263-41e8-aa8f-b791480110b3"), Now));
    const auto purchasePayload = purchaseEnvelope.value(QStringLiteral("payload")).toObject();
    const auto configuration = purchasePayload.value(QStringLiteral("configuration")).toObject();

    QCOMPARE(purchaseEnvelope.value(QStringLiteral("schemaVersion")).toInt(), 1);
    QCOMPARE(purchaseEnvelope.value(QStringLiteral("type")).toString(),
             QStringLiteral("ticket.purchase-requested"));
    QCOMPARE(purchaseEnvelope.value(QStringLiteral("deviceCode")).toString(), deviceCode);
    QCOMPARE(purchasePayload.value(QStringLiteral("purchaseReference")).toString(),
             purchaseReference);
    QCOMPARE(purchasePayload.value(QStringLiteral("productCode")).toString(),
             QStringLiteral("SINGLE_TRIP"));
    QCOMPARE(purchasePayload.value(QStringLiteral("paymentMethod")).toString(),
             QStringLiteral("SIMULATED"));
    QCOMPARE(purchasePayload.value(QStringLiteral("currency")).toString(),
             QStringLiteral("EUR"));
    QCOMPARE(purchasePayload.value(QStringLiteral("paidAmount")).toDouble(), 0.85);
    QCOMPARE(configuration.value(QStringLiteral("originStationCode")).toString(),
             QStringLiteral("ST001"));
    QCOMPARE(configuration.value(QStringLiteral("destinationStationCode")).toString(),
             QStringLiteral("ST007"));

    const auto issued = rmm::ticketmachine::parseIssueCommand(
        issueCommand(purchaseReference, QStringLiteral("PURCHASE"), Now.addSecs(60)),
        purchaseReference, Now);
    QCOMPARE(issued.result, IssueCommandResult::Regular);
    QCOMPARE(issued.ticketCode, QStringLiteral("RMM-TICKET-001"));

    const auto operationEvent = object(rmm::ticketmachine::buildOperationEvent(
        deviceCode, QStringLiteral("QR_TICKET_GENERATED"), purchaseReference,
        issued.ticketCode, QStringLiteral("QR_PRESENTED"),
        QStringLiteral("7f941a86-b297-4b14-8058-272798622c45"), Now));
    QCOMPARE(operationEvent.value(QStringLiteral("correlationId")).toString(),
             purchaseReference);
    const auto details = operationEvent.value(QStringLiteral("payload")).toObject()
                             .value(QStringLiteral("details")).toObject();
    QCOMPARE(details.value(QStringLiteral("ticketCode")).toString(), issued.ticketCode);
    QCOMPARE(details.value(QStringLiteral("resultCode")).toString(),
             QStringLiteral("QR_PRESENTED"));
}

QTEST_APPLESS_MAIN(TicketMachineProtocolTest)

#include "ticketmachineprotocoltest.moc"
