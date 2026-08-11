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
      m_timeout(new QTimer(this)),
      m_reconnectTimer(new QTimer(this)),
      m_publishRetryTimer(new QTimer(this))
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
    m_timeout->setInterval(8000);
    m_reconnectTimer->setSingleShot(true);
    m_publishRetryTimer->setSingleShot(true);
    m_publishRetryTimer->setInterval(1500);

    connect(m_client, &QMqttClient::connected, this, [this] {
        m_reconnectAttempt = 0;
        m_reconnectTimer->stop();
        const auto subscription = m_client->subscribe(QMqttTopicFilter(
            QStringLiteral("rmm/v1/devices/%1/responses").arg(m_configuration.deviceCode)), 1);
        if (subscription == nullptr) {
            m_client->disconnectFromHost();
            scheduleReconnect();
            return;
        }
        emit connectionStateChanged(true);
        publishPending();
    });
    connect(m_client, &QMqttClient::disconnected, this, [this] {
        m_packetId = -1;
        m_timeout->stop();
        emit connectionStateChanged(false);
        scheduleReconnect();
    });
    connect(m_client, &QMqttClient::messageSent, this, [this](qint32 packetId) {
        if (packetId == m_packetId) {
            m_packetId = -1;
            m_publishRetryTimer->stop();
            m_timeout->start();
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
            m_timeout->stop();
            scheduleReconnect();
        }
    });
    connect(m_timeout, &QTimer::timeout, this, [this] {
        schedulePublishRetry();
    });
    connect(m_reconnectTimer, &QTimer::timeout,
            this, &ValidatorMqttClient::connectToBroker);
    connect(m_publishRetryTimer, &QTimer::timeout, this, [this] {
        if (m_client->state() == QMqttClient::Connected) {
            publishPending();
        } else {
            scheduleReconnect();
        }
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
    if (m_client->state() == QMqttClient::Connected) {
        publishPending();
    } else {
        connectToBroker();
        scheduleReconnect();
    }
}

bool ValidatorMqttClient::hasPendingValidation() const
{
    return !m_pendingReference.isEmpty();
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
    if (m_publishAttempt >= 3) {
        failPending(QStringLiteral("MQTT_RETRY_LIMIT_REACHED"));
        return;
    }
    ++m_publishAttempt;
    m_packetId = m_client->publish(
        QMqttTopicName(QStringLiteral("rmm/v1/devices/%1/requests/validations")
            .arg(m_configuration.deviceCode)),
        m_pendingPayload, 1, false);
    if (m_packetId < 0) {
        m_packetId = -1;
        schedulePublishRetry();
    }
}

void ValidatorMqttClient::scheduleReconnect()
{
    if (m_client->password().isEmpty() || m_reconnectTimer->isActive()
        || m_client->state() == QMqttClient::Connected
        || m_client->state() == QMqttClient::Connecting) {
        return;
    }
    const int delaySeconds = qMin(30, 1 << qMin(m_reconnectAttempt, 5));
    ++m_reconnectAttempt;
    m_reconnectTimer->start(delaySeconds * 1000);
}

void ValidatorMqttClient::schedulePublishRetry()
{
    m_timeout->stop();
    if (m_pendingPayload.isEmpty()) return;
    if (m_publishAttempt >= 3) {
        failPending(QStringLiteral("MQTT_TIMEOUT"));
        return;
    }
    if (!m_publishRetryTimer->isActive()) {
        m_publishRetryTimer->start();
    }
    if (m_client->state() != QMqttClient::Connected) {
        scheduleReconnect();
    }
}

void ValidatorMqttClient::failPending(const QString &reason)
{
    clearPending();
    emit validationFailed(reason);
}

void ValidatorMqttClient::clearPending()
{
    m_timeout->stop();
    m_publishRetryTimer->stop();
    m_pendingPayload.clear();
    m_pendingReference.clear();
    m_packetId = -1;
    m_publishAttempt = 0;
}
