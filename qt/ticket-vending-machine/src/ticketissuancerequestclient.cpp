#include "ticketissuancerequestclient.h"

#include <rmm/localservices.h>

#include <QDateTime>
#include <QMqttClient>
#include <QMqttTopicName>
#include <QProcessEnvironment>
#include <QSettings>
#include <QSslConfiguration>
#include <QTimer>
#include <QUuid>

TicketIssuanceRequestClient::TicketIssuanceRequestClient(QObject *parent)
    : QObject(parent),
      m_client(new QMqttClient(this)),
      m_timeout(new QTimer(this)),
      m_reconnectTimer(new QTimer(this)),
      m_publishRetryTimer(new QTimer(this))
{
    const auto environment = QProcessEnvironment::systemEnvironment();
    m_deviceCode = environment.value(
        QStringLiteral("RMM_TICKET_MACHINE_DEVICE_CODE"),
        QStringLiteral("RMM-SALE-ST046-01"));
    m_client->setHostname(QString::fromUtf8(
        rmm::config::mqttHost.data(), static_cast<qsizetype>(rmm::config::mqttHost.size())));
    m_client->setPort(rmm::config::mqttPort);
    m_client->setClientId(m_deviceCode);
    m_client->setUsername(m_deviceCode);
    m_client->setPassword(environment.value(QStringLiteral("RMM_TICKET_MACHINE_MQTT_PASSWORD")));
    m_client->setCleanSession(false);
    m_client->setKeepAlive(20);
    m_timeout->setSingleShot(true);
    m_timeout->setInterval(45000);
    m_reconnectTimer->setSingleShot(true);
    m_publishRetryTimer->setSingleShot(true);
    m_publishRetryTimer->setInterval(1500);

    connect(m_client, &QMqttClient::connected, this, [this] {
        m_reconnectAttempt = 0;
        m_reconnectTimer->stop();
        emit connectionStateChanged(true, 0);
        m_client->subscribe(
            QMqttTopicFilter(QStringLiteral("rmm/v1/devices/%1/commands").arg(m_deviceCode)), 1);
        flushQueuedMessages();
        publishPending();
    });
    connect(m_client, &QMqttClient::disconnected, this, [this] {
        m_packetId = -1;
        emit connectionStateChanged(false, 0);
        scheduleReconnect();
    });
    connect(m_client, &QMqttClient::messageReceived, this,
            [this](const QByteArray &message, const QMqttTopicName &) {
        const auto command = rmm::ticketmachine::parseIssueCommand(
            message, m_awaitedReference, QDateTime::currentDateTimeUtc());
        if (command.result == rmm::ticketmachine::IssueCommandResult::Ignored) {
            return;
        }
        const bool compensatory = command.compensatory;
        if (compensatory) {
            QSettings settings;
            const QString storedStatus = settings.value(
                QStringLiteral("commands/%1/status").arg(command.commandId)).toString();
            if (storedStatus == QStringLiteral("COMPLETED")) {
                publishCommandAcknowledgement(
                    command.commandId, command.issuanceCode, QStringLiteral("COMPLETED"),
                    QStringLiteral("TICKET_PRESENTED"));
                return;
            }
        }
        if (command.result == rmm::ticketmachine::IssueCommandResult::Invalid) {
            if (compensatory) {
                publishCommandAcknowledgement(
                    command.commandId, command.issuanceCode, QStringLiteral("REJECTED"),
                    QStringLiteral("INVALID_TICKET_PAYLOAD"));
            } else {
                fail(QStringLiteral("INVALID_ISSUANCE_RESPONSE"));
            }
            return;
        }
        if (compensatory) {
            QSettings settings;
            settings.setValue(QStringLiteral("commands/%1/status").arg(command.commandId),
                              QStringLiteral("RECEIVED"));
            publishCommandAcknowledgement(
                command.commandId, command.issuanceCode, QStringLiteral("RECEIVED"),
                QStringLiteral("COMMAND_STORED"));
            emit compensatoryTicketIssued(
                command.commandId, command.issuanceCode, command.ticketCode,
                command.qrPng, command.qrValue, command.linkingCode);
            return;
        }
        m_timeout->stop();
        const QString purchaseReference = m_awaitedReference;
        m_awaitedReference.clear();
        publishOperationEvent(
            QStringLiteral("QR_TICKET_GENERATED"), purchaseReference, command.ticketCode,
            QStringLiteral("QR_PRESENTED"));
        emit ticketIssued(
            command.ticketCode, command.qrPng, command.qrValue,
            command.linkingCode, purchaseReference);
    });
    connect(m_client, &QMqttClient::messageSent, this, [this](qint32 packetId) {
        if (m_packetId < 0 || packetId != m_packetId) {
            return;
        }
        m_timeout->stop();
        const QString reference = m_pendingReference;
        m_pendingPayload.clear();
        m_pendingReference.clear();
        m_packetId = -1;
        m_publishAttempt = 0;
        m_publishRetryTimer->stop();
        m_timeout->start();
        emit submitted(reference);
    });
    connect(m_client, &QMqttClient::errorChanged, this, [this](QMqttClient::ClientError error) {
        if (error != QMqttClient::NoError) {
            scheduleReconnect();
        }
    });
    connect(m_reconnectTimer, &QTimer::timeout, this,
            &TicketIssuanceRequestClient::connectToBroker);
    connect(m_publishRetryTimer, &QTimer::timeout, this, [this] {
        if (m_client->state() == QMqttClient::Connected) {
            publishPending();
        } else {
            scheduleReconnect();
        }
    });
    connect(m_timeout, &QTimer::timeout, this, [this] {
        fail(QStringLiteral("MQTT_TIMEOUT"));
    });

    if (!m_client->password().isEmpty()) {
        connectToBroker();
    }
}

void TicketIssuanceRequestClient::connectToBroker()
{
    if (m_client->password().isEmpty() || m_client->state() != QMqttClient::Disconnected) {
        return;
    }
    if (rmm::config::mqttTlsEnabled) {
        m_client->connectToHostEncrypted(QSslConfiguration::defaultConfiguration());
    } else {
        m_client->connectToHost();
    }
}

void TicketIssuanceRequestClient::scheduleReconnect()
{
    if (m_client->password().isEmpty() || m_reconnectTimer->isActive()
            || m_client->state() == QMqttClient::Connected
            || m_client->state() == QMqttClient::Connecting) {
        return;
    }
    const int delaySeconds = qMin(30, 1 << qMin(m_reconnectAttempt, 5));
    ++m_reconnectAttempt;
    m_reconnectTimer->start(delaySeconds * 1000);
    emit connectionStateChanged(false, delaySeconds);
}

void TicketIssuanceRequestClient::publishOrQueue(
    const QString &topic,
    const QByteArray &payload)
{
    if (m_client->state() == QMqttClient::Connected
            && m_client->publish(QMqttTopicName(topic), payload, 1, false) >= 0) {
        return;
    }
    m_queuedMessages.enqueue({topic, payload});
    scheduleReconnect();
}

void TicketIssuanceRequestClient::flushQueuedMessages()
{
    while (!m_queuedMessages.isEmpty() && m_client->state() == QMqttClient::Connected) {
        const auto &message = m_queuedMessages.head();
        if (m_client->publish(QMqttTopicName(message.topic), message.payload, 1, false) < 0) {
            scheduleReconnect();
            return;
        }
        m_queuedMessages.dequeue();
    }
}

void TicketIssuanceRequestClient::completeCompensatoryIssuance(
    const QString &commandId,
    const QString &issuanceCode)
{
    QSettings settings;
    settings.setValue(QStringLiteral("commands/%1/status").arg(commandId),
                      QStringLiteral("COMPLETED"));
    publishCommandAcknowledgement(
        commandId, issuanceCode, QStringLiteral("COMPLETED"),
        QStringLiteral("TICKET_PRESENTED"));
}

void TicketIssuanceRequestClient::publishCommandAcknowledgement(
    const QString &commandId,
    const QString &issuanceCode,
    const QString &status,
    const QString &resultCode)
{
    publishOrQueue(
        QStringLiteral("rmm/v1/devices/%1/acks").arg(m_deviceCode),
        rmm::ticketmachine::buildCommandAcknowledgement(
            m_deviceCode, commandId, issuanceCode, status, resultCode,
            QUuid::createUuid().toString(QUuid::WithoutBraces),
            QDateTime::currentDateTimeUtc()));
}

void TicketIssuanceRequestClient::publishOperationEvent(
    const QString &eventCode,
    const QString &purchaseReference,
    const QString &ticketCode,
    const QString &resultCode)
{
    if (eventCode.isEmpty()) {
        return;
    }
    const QString topic = QStringLiteral("rmm/v1/devices/%1/events/operation").arg(m_deviceCode);
    publishOrQueue(topic, rmm::ticketmachine::buildOperationEvent(
        m_deviceCode, eventCode, purchaseReference, ticketCode, resultCode,
        QUuid::createUuid().toString(QUuid::WithoutBraces),
        QDateTime::currentDateTimeUtc()));
}

void TicketIssuanceRequestClient::submit(const TicketIssuanceRequest &request)
{
    if (!m_pendingReference.isEmpty() || !m_awaitedReference.isEmpty()) {
        emit failed(QStringLiteral("REQUEST_ALREADY_IN_PROGRESS"));
        return;
    }
    if (m_client->password().isEmpty()) {
        emit failed(QStringLiteral("MQTT_CREDENTIALS_MISSING"));
        return;
    }

    m_pendingReference = QUuid::createUuid().toString(QUuid::WithoutBraces);
    m_awaitedReference = m_pendingReference;
    const QString messageId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    m_pendingPayload = rmm::ticketmachine::buildPurchaseRequest(
        request, m_deviceCode, m_pendingReference, messageId,
        QDateTime::currentDateTimeUtc());
    m_timeout->start();
    if (m_client->state() == QMqttClient::Connected) {
        publishPending();
    } else {
        connectToBroker();
        scheduleReconnect();
    }
}

void TicketIssuanceRequestClient::publishPending()
{
    if (m_pendingPayload.isEmpty()) {
        return;
    }
    const QString topic = QStringLiteral("rmm/v1/devices/%1/requests/purchases").arg(m_deviceCode);
    m_packetId = m_client->publish(topic, m_pendingPayload, 1, false);
    if (m_packetId < 0) {
        m_packetId = -1;
        if (++m_publishAttempt <= 3) {
            m_publishRetryTimer->start();
        } else {
            m_publishAttempt = 0;
            m_client->disconnectFromHost();
        }
    }
}

void TicketIssuanceRequestClient::fail(const QString &reason)
{
    if (!m_awaitedReference.isEmpty()) {
        publishOperationEvent(
            QStringLiteral("TICKET_PURCHASE_FAILED"), m_awaitedReference, QString(), reason);
    }
    m_timeout->stop();
    m_publishRetryTimer->stop();
    m_pendingPayload.clear();
    m_pendingReference.clear();
    m_awaitedReference.clear();
    m_packetId = -1;
    m_publishAttempt = 0;
    emit failed(reason);
}
