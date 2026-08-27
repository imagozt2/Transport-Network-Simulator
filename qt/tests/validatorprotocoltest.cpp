#include "validationresult.h"
#include "validatorprotocol.h"

#include <QJsonDocument>
#include <QJsonObject>
#include <QtTest>

class ValidatorProtocolTest final : public QObject
{
    Q_OBJECT

private slots:
    void buildsEntryValidationRequest();
    void parsesAcceptedValidationResources();
    void explainsRejectedValidation();
    void explainsARepeatedEntryWithoutOpeningAnotherJourney();
    void ignoresAResponseForAnotherValidation();
    void completesEntryAndExitValidationSequence();
};

void ValidatorProtocolTest::buildsEntryValidationRequest()
{
    const QByteArray message = rmm::validator::buildValidationRequest(
        QStringLiteral("RMM-EN-ST038-01"), QStringLiteral("validation-1"),
        QStringLiteral("ENTRY"), QStringLiteral("ST038"), QStringLiteral("signed-qr"),
        QStringLiteral("message-1"), QDateTime::fromString(
            QStringLiteral("2026-08-11T10:00:00Z"), Qt::ISODate));
    const QJsonObject envelope = QJsonDocument::fromJson(message).object();
    const QJsonObject payload = envelope.value(QStringLiteral("payload")).toObject();

    QCOMPARE(envelope.value(QStringLiteral("type")).toString(),
             QStringLiteral("ticket.validation-requested"));
    QCOMPARE(envelope.value(QStringLiteral("deviceCode")).toString(),
             QStringLiteral("RMM-EN-ST038-01"));
    QCOMPARE(payload.value(QStringLiteral("direction")).toString(), QStringLiteral("ENTRY"));
    QCOMPARE(payload.value(QStringLiteral("stationCode")).toString(), QStringLiteral("ST038"));
    QCOMPARE(payload.value(QStringLiteral("qrValue")).toString(), QStringLiteral("signed-qr"));
}

void ValidatorProtocolTest::parsesAcceptedValidationResources()
{
    const QJsonObject payload{
        {QStringLiteral("validationReference"), QStringLiteral("validation-1")},
        {QStringLiteral("decision"), QStringLiteral("ACCEPTED")},
        {QStringLiteral("reasonCode"), QStringLiteral("VALID")},
        {QStringLiteral("remainingTrips"), 7},
        {QStringLiteral("consumedTrips"), 1},
    };
    const QJsonObject envelope{
        {QStringLiteral("type"), QStringLiteral("ticket.validation-decided")},
        {QStringLiteral("deviceCode"), QStringLiteral("RMM-EN-ST038-01")},
        {QStringLiteral("payload"), payload},
    };

    auto result = rmm::validator::parseValidationResponse(
        QJsonDocument(envelope).toJson(QJsonDocument::Compact),
        QStringLiteral("RMM-EN-ST038-01"), QStringLiteral("validation-1"));

    QVERIFY(result.has_value());
    QVERIFY(result->isAccepted());
    QVERIFY(result->remainingTrips == std::optional<int>(7));
    QVERIFY(result->consumedTrips == std::optional<int>(1));
}

void ValidatorProtocolTest::explainsRejectedValidation()
{
    const QJsonObject payload{
        {QStringLiteral("validationReference"), QStringLiteral("validation-2")},
        {QStringLiteral("decision"), QStringLiteral("REJECTED")},
        {QStringLiteral("reasonCode"), QStringLiteral("ENTRY_REQUIRED")},
    };
    const QJsonObject envelope{
        {QStringLiteral("type"), QStringLiteral("ticket.validation-decided")},
        {QStringLiteral("deviceCode"), QStringLiteral("RMM-EX-ST038-01")},
        {QStringLiteral("payload"), payload},
    };

    auto result = rmm::validator::parseValidationResponse(
        QJsonDocument(envelope).toJson(QJsonDocument::Compact),
        QStringLiteral("RMM-EX-ST038-01"), QStringLiteral("validation-2"));

    QVERIFY(result.has_value());
    QVERIFY(!result->isAccepted());
    QCOMPARE(result->detail(), QStringLiteral("No existe una validación de entrada"));
}

void ValidatorProtocolTest::explainsARepeatedEntryWithoutOpeningAnotherJourney()
{
    const QJsonObject payload{
        {QStringLiteral("validationReference"), QStringLiteral("validation-repeated-entry")},
        {QStringLiteral("decision"), QStringLiteral("REJECTED")},
        {QStringLiteral("reasonCode"), QStringLiteral("ENTRY_ALREADY_OPEN")},
    };
    const QJsonObject envelope{
        {QStringLiteral("type"), QStringLiteral("ticket.validation-decided")},
        {QStringLiteral("deviceCode"), QStringLiteral("RMM-EN-ST038-01")},
        {QStringLiteral("payload"), payload},
    };

    const auto result = rmm::validator::parseValidationResponse(
        QJsonDocument(envelope).toJson(QJsonDocument::Compact),
        QStringLiteral("RMM-EN-ST038-01"),
        QStringLiteral("validation-repeated-entry"));

    QVERIFY(result.has_value());
    QVERIFY(!result->isAccepted());
    QCOMPARE(result->reasonCode, QStringLiteral("ENTRY_ALREADY_OPEN"));
    QCOMPARE(result->detail(), QStringLiteral("El billete ya tiene un trayecto abierto"));
}

void ValidatorProtocolTest::ignoresAResponseForAnotherValidation()
{
    const QJsonObject payload{
        {QStringLiteral("validationReference"), QStringLiteral("another-validation")},
        {QStringLiteral("decision"), QStringLiteral("ACCEPTED")},
    };
    const QJsonObject envelope{
        {QStringLiteral("type"), QStringLiteral("ticket.validation-decided")},
        {QStringLiteral("deviceCode"), QStringLiteral("RMM-EN-ST038-01")},
        {QStringLiteral("payload"), payload},
    };

    QVERIFY(!rmm::validator::parseValidationResponse(
        QJsonDocument(envelope).toJson(QJsonDocument::Compact),
        QStringLiteral("RMM-EN-ST038-01"), QStringLiteral("validation-1")).has_value());
}

void ValidatorProtocolTest::completesEntryAndExitValidationSequence()
{
    const QDateTime entryAt = QDateTime::fromString(
        QStringLiteral("2026-08-11T08:00:00.000Z"), Qt::ISODateWithMs);
    const QString entryDevice = QStringLiteral("RMM-EN-ST001-01");
    const QString exitDevice = QStringLiteral("RMM-EX-ST010-01");
    const QString entryReference = QStringLiteral("276b0e6c-d583-4f64-b304-eabbf63d6aac");
    const QString exitReference = QStringLiteral("f4adeab9-2d24-4d67-b7ce-a77027302adf");

    const auto entryRequest = QJsonDocument::fromJson(rmm::validator::buildValidationRequest(
        entryDevice, entryReference, QStringLiteral("ENTRY"), QStringLiteral("ST001"),
        QStringLiteral("RMM:TICKET:1:signed"),
        QStringLiteral("023729ee-ea0a-4b9f-bd3b-5c5c63a9b9c0"), entryAt)).object();
    QCOMPARE(entryRequest.value(QStringLiteral("deviceCode")).toString(), entryDevice);
    QCOMPARE(entryRequest.value(QStringLiteral("payload")).toObject()
                 .value(QStringLiteral("direction")).toString(), QStringLiteral("ENTRY"));

    const QJsonObject entryDecision{
        {QStringLiteral("type"), QStringLiteral("ticket.validation-decided")},
        {QStringLiteral("deviceCode"), entryDevice},
        {QStringLiteral("payload"), QJsonObject{
            {QStringLiteral("validationReference"), entryReference},
            {QStringLiteral("decision"), QStringLiteral("ACCEPTED")},
            {QStringLiteral("reasonCode"), QStringLiteral("VALID")},
            {QStringLiteral("remainingTrips"), 9},
            {QStringLiteral("consumedTrips"), 1},
        }},
    };
    const auto entered = rmm::validator::parseValidationResponse(
        QJsonDocument(entryDecision).toJson(QJsonDocument::Compact),
        entryDevice, entryReference);
    QVERIFY(entered.has_value());
    QVERIFY(entered->isAccepted());
    QCOMPARE(entered->remainingTrips, std::optional<int>(9));
    QCOMPARE(entered->consumedTrips, std::optional<int>(1));

    const auto exitRequest = QJsonDocument::fromJson(rmm::validator::buildValidationRequest(
        exitDevice, exitReference, QStringLiteral("EXIT"), QStringLiteral("ST010"),
        QStringLiteral("RMM:TICKET:1:signed"),
        QStringLiteral("b5c80f21-b66e-46d1-9c36-1a16d74cf4d7"), entryAt.addSecs(720))).object();
    QCOMPARE(exitRequest.value(QStringLiteral("deviceCode")).toString(), exitDevice);
    QCOMPARE(exitRequest.value(QStringLiteral("payload")).toObject()
                 .value(QStringLiteral("direction")).toString(), QStringLiteral("EXIT"));

    const QJsonObject exitDecision{
        {QStringLiteral("type"), QStringLiteral("ticket.validation-decided")},
        {QStringLiteral("deviceCode"), exitDevice},
        {QStringLiteral("payload"), QJsonObject{
            {QStringLiteral("validationReference"), exitReference},
            {QStringLiteral("decision"), QStringLiteral("ACCEPTED")},
            {QStringLiteral("reasonCode"), QStringLiteral("VALID")},
            {QStringLiteral("remainingTrips"), 9},
            {QStringLiteral("fareAmount"), 1.0},
        }},
    };
    const auto exited = rmm::validator::parseValidationResponse(
        QJsonDocument(exitDecision).toJson(QJsonDocument::Compact),
        exitDevice, exitReference);
    QVERIFY(exited.has_value());
    QVERIFY(exited->isAccepted());
    QCOMPARE(exited->remainingTrips, std::optional<int>(9));
    QCOMPARE(exited->fareAmount, std::optional<double>(1.0));
}

QTEST_MAIN(ValidatorProtocolTest)
#include "validatorprotocoltest.moc"
