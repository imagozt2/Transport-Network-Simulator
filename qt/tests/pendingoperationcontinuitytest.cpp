#include "ticketissuancerequestclient.h"
#include "validatormqttclient.h"

#include <QSettings>
#include <QSignalSpy>
#include <QTemporaryDir>
#include <QtTest>

class PendingOperationContinuityTest final : public QObject
{
    Q_OBJECT

private slots:
    void initTestCase();
    void init();
    void ticketPurchaseSurvivesClientRestart();
    void validationSurvivesClientRestart();

private:
    QTemporaryDir m_settingsDirectory;
};

void PendingOperationContinuityTest::initTestCase()
{
    QVERIFY(m_settingsDirectory.isValid());
    QCoreApplication::setOrganizationName(QStringLiteral("RMMTest"));
    QCoreApplication::setApplicationName(QStringLiteral("PendingOperations"));
    QSettings::setDefaultFormat(QSettings::IniFormat);
    QSettings::setPath(
        QSettings::IniFormat, QSettings::UserScope, m_settingsDirectory.path());
    qputenv("RMM_TICKET_MACHINE_DEVICE_CODE", "RMM-TM-ST046-01");
    qputenv("RMM_TICKET_MACHINE_MQTT_PASSWORD", "test-password");
    qputenv("RMM_VALIDATOR_MQTT_PASSWORD", "test-password");
}

void PendingOperationContinuityTest::init()
{
    QSettings settings;
    settings.clear();
    settings.sync();
}

void PendingOperationContinuityTest::ticketPurchaseSurvivesClientRestart()
{
    const TicketIssuanceRequest request {
        QStringLiteral("MULTI_TRIP"), {}, {}, 10, 0.0, 10.0
    };
    {
        TicketIssuanceRequestClient client;
        client.submit(request);
        QSettings settings;
        const QString reference = settings.value(
            QStringLiteral("mqtt/RMM-TM-ST046-01/pending/reference")).toString();
        QVERIFY(!reference.isEmpty());
        QVERIFY(!settings.value(
            QStringLiteral("mqtt/RMM-TM-ST046-01/pending/payload")).toByteArray().isEmpty());
    }

    TicketIssuanceRequestClient restoredClient;
    QSignalSpy failureSpy(&restoredClient, &TicketIssuanceRequestClient::failed);
    restoredClient.submit(request);

    QCOMPARE(failureSpy.count(), 1);
    QCOMPARE(failureSpy.first().first().toString(), QStringLiteral("REQUEST_ALREADY_IN_PROGRESS"));
}

void PendingOperationContinuityTest::validationSurvivesClientRestart()
{
    ValidatorConfiguration configuration;
    configuration.deviceCode = QStringLiteral("RMM-EN-ST046-01");
    configuration.stationCode = QStringLiteral("ST046");
    configuration.stationName = QStringLiteral("El Espigon");
    configuration.mode = ValidatorMode::Entry;
    {
        ValidatorMqttClient client(configuration);
        client.submit(QStringLiteral("RMM:TICKET:1:test-payload"));
        QVERIFY(client.hasPendingValidation());
    }

    ValidatorMqttClient restoredClient(configuration);
    QVERIFY(restoredClient.hasPendingValidation());
    QSettings settings;
    QVERIFY(!settings.value(
        QStringLiteral("mqtt/RMM-EN-ST046-01/pendingValidation/reference")).toString().isEmpty());
    QVERIFY(!settings.value(
        QStringLiteral("mqtt/RMM-EN-ST046-01/pendingValidation/payload")).toByteArray().isEmpty());
}

QTEST_MAIN(PendingOperationContinuityTest)
#include "pendingoperationcontinuitytest.moc"
