#include "ticketmachineconfiguration.h"
#include "validatorconfiguration.h"

#include <QProcessEnvironment>
#include <QTest>

class MqttIdentityConfigurationTest final : public QObject
{
    Q_OBJECT

private slots:
    void ticketMachineUsesItsInventoriedIdentityByDefault();
    void ticketMachineNormalizesACanonicalIdentity();
    void ticketMachineRejectsForeignAndLegacyIdentities_data();
    void ticketMachineRejectsForeignAndLegacyIdentities();
    void validatorBuildsAnEntryIdentityByDefault();
    void validatorResolvesAccentedStationNamesFromItsIdentity_data();
    void validatorResolvesAccentedStationNamesFromItsIdentity();
    void validatorAcceptsAnExitIdentityInExitMode();
    void validatorRejectsAnIdentityForAnotherMode_data();
    void validatorRejectsAnIdentityForAnotherMode();
};

void MqttIdentityConfigurationTest::ticketMachineUsesItsInventoriedIdentityByDefault()
{
    const auto configuration = TicketMachineConfiguration::fromEnvironment({});

    QVERIFY(configuration.valid);
    QCOMPARE(configuration.deviceCode, QStringLiteral("RMM-TM-ST046-01"));
    QCOMPARE(configuration.stationCode, QStringLiteral("ST046"));
}

void MqttIdentityConfigurationTest::ticketMachineNormalizesACanonicalIdentity()
{
    QProcessEnvironment environment;
    environment.insert(QStringLiteral("RMM_TICKET_MACHINE_DEVICE_CODE"),
                       QStringLiteral(" rmm-tm-st001-03 "));

    const auto configuration = TicketMachineConfiguration::fromEnvironment(environment);

    QVERIFY(configuration.valid);
    QCOMPARE(configuration.deviceCode, QStringLiteral("RMM-TM-ST001-03"));
    QCOMPARE(configuration.stationCode, QStringLiteral("ST001"));
}

void MqttIdentityConfigurationTest::ticketMachineRejectsForeignAndLegacyIdentities_data()
{
    QTest::addColumn<QString>("deviceCode");
    QTest::newRow("entry-validator") << QStringLiteral("RMM-EN-ST001-01");
    QTest::newRow("exit-validator") << QStringLiteral("RMM-EX-ST001-01");
    QTest::newRow("legacy-sale") << QStringLiteral("RMM-SALE-ST001-01");
    QTest::newRow("malformed-inventory-code") << QStringLiteral("RMM-TM-ST1-1");
}

void MqttIdentityConfigurationTest::ticketMachineRejectsForeignAndLegacyIdentities()
{
    QFETCH(QString, deviceCode);
    QProcessEnvironment environment;
    environment.insert(QStringLiteral("RMM_TICKET_MACHINE_DEVICE_CODE"), deviceCode);

    const auto configuration = TicketMachineConfiguration::fromEnvironment(environment);

    QVERIFY(!configuration.valid);
    QVERIFY(configuration.stationCode.isEmpty());
    QVERIFY(!configuration.error.isEmpty());
}

void MqttIdentityConfigurationTest::validatorBuildsAnEntryIdentityByDefault()
{
    const auto configuration = ValidatorConfiguration::fromEnvironment({});

    QVERIFY(configuration.valid);
    QVERIFY(configuration.isEntry());
    QCOMPARE(configuration.modeCode(), QStringLiteral("ENTRY"));
    QCOMPARE(configuration.stationCode, QStringLiteral("ST046"));
    QCOMPARE(configuration.stationName, QStringLiteral("El Espigón"));
    QCOMPARE(configuration.deviceCode, QStringLiteral("RMM-EN-ST046-01"));
}

void MqttIdentityConfigurationTest::validatorAcceptsAnExitIdentityInExitMode()
{
    QProcessEnvironment environment;
    environment.insert(QStringLiteral("RMM_VALIDATOR_MODE"), QStringLiteral(" exit "));
    environment.insert(QStringLiteral("RMM_VALIDATOR_STATION_CODE"), QStringLiteral(" st010 "));
    environment.insert(QStringLiteral("RMM_VALIDATOR_DEVICE_CODE"),
                       QStringLiteral(" rmm-ex-st010-02 "));

    const auto configuration = ValidatorConfiguration::fromEnvironment(environment);

    QVERIFY(configuration.valid);
    QVERIFY(!configuration.isEntry());
    QCOMPARE(configuration.modeCode(), QStringLiteral("EXIT"));
    QCOMPARE(configuration.stationCode, QStringLiteral("ST010"));
    QCOMPARE(configuration.stationName, QStringLiteral("Gueto Norte"));
    QCOMPARE(configuration.deviceCode, QStringLiteral("RMM-EX-ST010-02"));
}

void MqttIdentityConfigurationTest::validatorResolvesAccentedStationNamesFromItsIdentity_data()
{
    QTest::addColumn<QString>("stationCode");
    QTest::addColumn<QString>("stationName");
    QTest::newRow("ramon-y-cajal")
        << QStringLiteral("ST004") << QStringLiteral("Ramón y Cajal");
    QTest::newRow("museo-maritimo")
        << QStringLiteral("ST014") << QStringLiteral("Museo Marítimo");
    QTest::newRow("estadio-olimpico")
        << QStringLiteral("ST017") << QStringLiteral("Estadio Olímpico");
    QTest::newRow("el-espigon")
        << QStringLiteral("ST046") << QStringLiteral("El Espigón");
}

void MqttIdentityConfigurationTest::validatorResolvesAccentedStationNamesFromItsIdentity()
{
    QFETCH(QString, stationCode);
    QFETCH(QString, stationName);
    QProcessEnvironment environment;
    environment.insert(QStringLiteral("RMM_VALIDATOR_DEVICE_CODE"),
                       QStringLiteral("RMM-EN-%1-01").arg(stationCode));

    const auto configuration = ValidatorConfiguration::fromEnvironment(environment);

    QVERIFY(configuration.valid);
    QCOMPARE(configuration.stationCode, stationCode);
    QCOMPARE(configuration.stationName, stationName);
}

void MqttIdentityConfigurationTest::validatorRejectsAnIdentityForAnotherMode_data()
{
    QTest::addColumn<QString>("mode");
    QTest::addColumn<QString>("deviceCode");
    QTest::newRow("entry-using-exit")
        << QStringLiteral("ENTRY") << QStringLiteral("RMM-EX-ST038-01");
    QTest::newRow("exit-using-entry")
        << QStringLiteral("EXIT") << QStringLiteral("RMM-EN-ST038-01");
    QTest::newRow("entry-using-ticket-machine")
        << QStringLiteral("ENTRY") << QStringLiteral("RMM-TM-ST038-01");
    QTest::newRow("entry-using-legacy-validator")
        << QStringLiteral("ENTRY") << QStringLiteral("RMM-VAL-ST038-01");
}

void MqttIdentityConfigurationTest::validatorRejectsAnIdentityForAnotherMode()
{
    QFETCH(QString, mode);
    QFETCH(QString, deviceCode);
    QProcessEnvironment environment;
    environment.insert(QStringLiteral("RMM_VALIDATOR_MODE"), mode);
    environment.insert(QStringLiteral("RMM_VALIDATOR_DEVICE_CODE"), deviceCode);

    const auto configuration = ValidatorConfiguration::fromEnvironment(environment);

    QVERIFY(!configuration.valid);
    QVERIFY(!configuration.error.isEmpty());
}

QTEST_APPLESS_MAIN(MqttIdentityConfigurationTest)

#include "mqttidentityconfigurationtest.moc"
