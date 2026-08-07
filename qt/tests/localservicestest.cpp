#include <rmm/localservices.h>

#include <QTest>

#include <string_view>

class LocalServicesTest final : public QObject
{
    Q_OBJECT

private slots:
    void apiBaseUrlIsHttpEndpoint();
    void mqttEndpointIsUsable();
};

void LocalServicesTest::apiBaseUrlIsHttpEndpoint()
{
    const auto baseUrl = rmm::config::apiBaseUrl;

    QVERIFY(!baseUrl.empty());
    QVERIFY(baseUrl.starts_with(std::string_view{"http://"})
            || baseUrl.starts_with(std::string_view{"https://"}));
}

void LocalServicesTest::mqttEndpointIsUsable()
{
    QVERIFY(!rmm::config::mqttHost.empty());
    QVERIFY(rmm::config::mqttPort > 0);
}

QTEST_APPLESS_MAIN(LocalServicesTest)

#include "localservicestest.moc"

