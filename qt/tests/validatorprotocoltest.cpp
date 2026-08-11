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
    void ignoresAResponseForAnotherValidation();
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

QTEST_MAIN(ValidatorProtocolTest)
#include "validatorprotocoltest.moc"
