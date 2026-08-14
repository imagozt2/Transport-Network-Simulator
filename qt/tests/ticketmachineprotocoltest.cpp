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
        {QStringLiteral("schemaVersion"), 1},
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
    void completesACompensatoryIssuanceContract();
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
    QTest::newRow("time-pass")
        << TicketIssuanceRequest{QStringLiteral("TIME_PASS"), {}, {}, 7, 0.0, 14.0}
        << QStringLiteral("quantity") << QVariant(7);
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
        request, QStringLiteral("RMM-TM-ST001-01"), QStringLiteral("purchase-1"),
        QStringLiteral("message-1"), Now));
    const auto payload = envelope.value(QStringLiteral("payload")).toObject();
    const auto configuration = payload.value(QStringLiteral("configuration")).toObject();

    QCOMPARE(envelope.value(QStringLiteral("type")).toString(),
             QStringLiteral("ticket.purchase-requested"));
    QCOMPARE(envelope.value(QStringLiteral("deviceCode")).toString(),
             QStringLiteral("RMM-TM-ST001-01"));
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
        QStringLiteral("RMM-TM-ST001-01"), QStringLiteral("TICKET_PURCHASE_FAILED"),
        QStringLiteral("purchase-1"), {}, QStringLiteral("MQTT_TIMEOUT"),
        QStringLiteral("event-1"), Now));
    QCOMPARE(event.value(QStringLiteral("payload")).toObject()
                 .value(QStringLiteral("severity")).toString(), QStringLiteral("ERROR"));
    QCOMPARE(event.value(QStringLiteral("correlationId")).toString(),
             QStringLiteral("purchase-1"));

    const auto acknowledgement = object(rmm::ticketmachine::buildCommandAcknowledgement(
        QStringLiteral("RMM-TM-ST001-01"), QStringLiteral("command-1"),
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
    const QString deviceCode = QStringLiteral("RMM-TM-ST001-01");
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
    QCOMPARE(issued.qrValue, QStringLiteral("signed-qr"));
    QCOMPARE(issued.qrPng, QByteArray("png-data"));
    QCOMPARE(issued.linkingCode, QStringLiteral("LINK1234"));

    const auto qrGeneratedEvent = object(rmm::ticketmachine::buildOperationEvent(
        deviceCode, QStringLiteral("QR_TICKET_GENERATED"), purchaseReference,
        issued.ticketCode, QStringLiteral("QR_PRESENTED"),
        QStringLiteral("7f941a86-b297-4b14-8058-272798622c45"), Now));
    QCOMPARE(qrGeneratedEvent.value(QStringLiteral("type")).toString(),
             QStringLiteral("device.operation-event"));
    QCOMPARE(qrGeneratedEvent.value(QStringLiteral("correlationId")).toString(),
             purchaseReference);
    const auto qrDetails = qrGeneratedEvent.value(QStringLiteral("payload")).toObject()
                               .value(QStringLiteral("details")).toObject();
    QCOMPARE(qrDetails.value(QStringLiteral("ticketCode")).toString(), issued.ticketCode);
    QCOMPARE(qrDetails.value(QStringLiteral("resultCode")).toString(),
             QStringLiteral("QR_PRESENTED"));

    const auto completedEvent = object(rmm::ticketmachine::buildOperationEvent(
        deviceCode, QStringLiteral("TICKET_PURCHASE_COMPLETED"), purchaseReference,
        issued.ticketCode, QStringLiteral("TICKET_PRESENTED"),
        QStringLiteral("64be0d67-1a3f-4b38-88ac-d9fa2e4ddd1a"), Now.addSecs(30)));
    const auto completedPayload = completedEvent.value(QStringLiteral("payload")).toObject();
    const auto completedDetails = completedPayload.value(QStringLiteral("details")).toObject();
    QCOMPARE(completedEvent.value(QStringLiteral("correlationId")).toString(),
             purchaseReference);
    QCOMPARE(completedPayload.value(QStringLiteral("eventCode")).toString(),
             QStringLiteral("TICKET_PURCHASE_COMPLETED"));
    QCOMPARE(completedPayload.value(QStringLiteral("severity")).toString(),
             QStringLiteral("INFO"));
    QCOMPARE(completedDetails.value(QStringLiteral("ticketCode")).toString(),
             issued.ticketCode);
    QCOMPARE(completedDetails.value(QStringLiteral("resultCode")).toString(),
             QStringLiteral("TICKET_PRESENTED"));
}

void TicketMachineProtocolTest::completesACompensatoryIssuanceContract()
{
    const QString deviceCode = QStringLiteral("RMM-TM-ST001-01");
    const auto command = rmm::ticketmachine::parseIssueCommand(
        issueCommand({}, QStringLiteral("COMPENSATORY"), Now.addSecs(120)), {}, Now);

    QCOMPARE(command.result, IssueCommandResult::Compensatory);
    QVERIFY(command.compensatory);
    QCOMPARE(command.commandId, QStringLiteral("command-1"));
    QCOMPARE(command.issuanceCode, QStringLiteral("issuance-1"));
    QCOMPARE(command.ticketCode, QStringLiteral("RMM-TICKET-001"));
    QCOMPARE(command.qrPng, QByteArray("png-data"));

    const auto received = object(rmm::ticketmachine::buildCommandAcknowledgement(
        deviceCode, command.commandId, command.issuanceCode,
        QStringLiteral("RECEIVED"), QStringLiteral("COMMAND_STORED"),
        QStringLiteral("2a688584-455b-4fbf-a702-35a18a82a81c"), Now));
    const auto receivedPayload = received.value(QStringLiteral("payload")).toObject();
    QCOMPARE(received.value(QStringLiteral("type")).toString(),
             QStringLiteral("ticket.issue-acknowledged"));
    QCOMPARE(receivedPayload.value(QStringLiteral("commandId")).toString(), command.commandId);
    QCOMPARE(receivedPayload.value(QStringLiteral("issuanceCode")).toString(),
             command.issuanceCode);
    QCOMPARE(receivedPayload.value(QStringLiteral("status")).toString(),
             QStringLiteral("RECEIVED"));

    const auto completed = object(rmm::ticketmachine::buildCommandAcknowledgement(
        deviceCode, command.commandId, command.issuanceCode,
        QStringLiteral("COMPLETED"), QStringLiteral("TICKET_PRESENTED"),
        QStringLiteral("cc7096fc-f4e0-4368-b237-178396a9ccfb"), Now.addSecs(5)));
    const auto completedPayload = completed.value(QStringLiteral("payload")).toObject();
    QCOMPARE(completedPayload.value(QStringLiteral("status")).toString(),
             QStringLiteral("COMPLETED"));
    QCOMPARE(completedPayload.value(QStringLiteral("resultCode")).toString(),
             QStringLiteral("TICKET_PRESENTED"));
    QCOMPARE(completedPayload.value(QStringLiteral("issuanceCode")).toString(),
             command.issuanceCode);
}

QTEST_APPLESS_MAIN(TicketMachineProtocolTest)

#include "ticketmachineprotocoltest.moc"
