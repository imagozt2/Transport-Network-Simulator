#include "validatormqttclient.h"
#include "validatorprotocol.h"

#include <rmm/localservices.h>

#include <QMqttClient>
#include <QMqttTopicName>
#include <QProcessEnvironment>
#include <QSslConfiguration>
#include <QTimer>
#include <QUuid>

ValidatorMqttClient::ValidatorMqttClient(
    const ValidatorConfiguration &configuration,
    QObject *parent)
    : QObject(parent),
      m_configuration(configuration),
      m_client(new QMqttClient(this)),
      m_timeout(new QTimer(this))
{
    const auto environment = QProcessEnvironment::systemEnvironment();
    m_client->setHostname(QString::fromUtf8(
        rmm::config::mqttHost.data(), static_cast<qsizetype>(rmm::config::mqttHost.size())));
    m_client->setPort(rmm::config::mqttPort);
    m_client->setClientId(m_configuration.deviceCode);
    m_client->setUsername(m_configuration.deviceCode);
    m_client->setPassword(environment.value(QStringLiteral("RMM_VALIDATOR_MQTT_PASSWORD")));
    m_client->setCleanSession(false);
    m_client->setKeepAlive(20);
    m_timeout->setSingleShot(true);
    m_timeout->setInterval(15000);

    connect(m_client, &QMqttClient::connected, this, [this] {
        const auto subscription = m_client->subscribe(QMqttTopicFilter(
            QStringLiteral("rmm/v1/devices/%1/responses").arg(m_configuration.deviceCode)), 1);
        if (subscription == nullptr) {
            emit validationFailed(QStringLiteral("MQTT_SUBSCRIPTION_FAILED"));
            return;
        }
        emit connectionStateChanged(true);
        publishPending();
    });
    connect(m_client, &QMqttClient::disconnected, this, [this] {
        m_packetId = -1;
        emit connectionStateChanged(false);
    });
    connect(m_client, &QMqttClient::messageSent, this, [this](qint32 packetId) {
        if (packetId == m_packetId) {
            m_packetId = -1;
            emit validationSubmitted(m_pendingReference);
        }
    });
    connect(m_client, &QMqttClient::messageReceived, this,
            [this](const QByteArray &message, const QMqttTopicName &) {
        QString error;
        auto result = rmm::validator::parseValidationResponse(
            message, m_configuration.deviceCode, m_pendingReference, &error);
        if (!result.has_value()) {
            return;
        }
        m_timeout->stop();
        clearPending();
        emit validationCompleted(*result);
    });
    connect(m_client, &QMqttClient::errorChanged, this,
            [this](QMqttClient::ClientError error) {
        if (error != QMqttClient::NoError) {
            emit connectionStateChanged(false);
            if (!m_pendingReference.isEmpty()) {
                emit validationFailed(QStringLiteral("MQTT_CONNECTION_ERROR"));
                clearPending();
            }
        }
    });
    connect(m_timeout, &QTimer::timeout, this, [this] {
        emit validationFailed(QStringLiteral("MQTT_TIMEOUT"));
        clearPending();
    });

    QTimer::singleShot(0, this, [this] {
        if (m_client->password().isEmpty()) {
            emit validationFailed(QStringLiteral("MQTT_CREDENTIALS_MISSING"));
        } else {
            connectToBroker();
        }
    });
}

void ValidatorMqttClient::submit(const QString &qrValue)
{
    if (!m_pendingReference.isEmpty()) {
        emit validationFailed(QStringLiteral("VALIDATION_ALREADY_IN_PROGRESS"));
        return;
    }
    if (m_client->password().isEmpty()) {
        emit validationFailed(QStringLiteral("MQTT_CREDENTIALS_MISSING"));
        return;
    }

    m_pendingReference = QUuid::createUuid().toString(QUuid::WithoutBraces);
    m_pendingPayload = rmm::validator::buildValidationRequest(
        m_configuration.deviceCode, m_pendingReference, m_configuration.modeCode(),
        m_configuration.stationCode, qrValue,
        QUuid::createUuid().toString(QUuid::WithoutBraces),
        QDateTime::currentDateTimeUtc());
    m_timeout->start();
    if (m_client->state() == QMqttClient::Connected) {
        publishPending();
    } else {
        connectToBroker();
    }
}

void ValidatorMqttClient::connectToBroker()
{
    if (m_client->state() != QMqttClient::Disconnected || m_client->password().isEmpty()) return;
    if (rmm::config::mqttTlsEnabled) {
        m_client->connectToHostEncrypted(QSslConfiguration::defaultConfiguration());
    } else {
        m_client->connectToHost();
    }
}

void ValidatorMqttClient::publishPending()
{
    if (m_pendingPayload.isEmpty() || m_client->state() != QMqttClient::Connected) return;
    m_packetId = m_client->publish(
        QMqttTopicName(QStringLiteral("rmm/v1/devices/%1/requests/validations")
            .arg(m_configuration.deviceCode)),
        m_pendingPayload, 1, false);
    if (m_packetId < 0) {
        emit validationFailed(QStringLiteral("MQTT_PUBLISH_FAILED"));
        clearPending();
    }
}

void ValidatorMqttClient::clearPending()
{
    m_timeout->stop();
    m_pendingPayload.clear();
    m_pendingReference.clear();
    m_packetId = -1;
}
