#include "ticketissuancerequestclient.h"

#include <rmm/localservices.h>

#include <QDateTime>
#include <QJsonDocument>
#include <QJsonObject>
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
        const auto document = QJsonDocument::fromJson(message);
        if (!document.isObject()) {
            return;
        }
        const auto envelope = document.object();
        if (envelope.value(QStringLiteral("type")).toString()
                != QStringLiteral("ticket.issue-command")) {
            return;
        }
        const auto payload = envelope.value(QStringLiteral("payload")).toObject();
        const bool compensatory = payload.value(QStringLiteral("issuanceKind")).toString()
            == QStringLiteral("COMPENSATORY");
        const QString commandId = payload.value(QStringLiteral("commandId")).toString();
        const QString issuanceCode = payload.value(QStringLiteral("issuanceCode")).toString();
        const QDateTime expiresAt = QDateTime::fromString(
            payload.value(QStringLiteral("expiresAt")).toString(), Qt::ISODateWithMs);
        if (commandId.isEmpty() || !expiresAt.isValid()
                || expiresAt < QDateTime::currentDateTimeUtc()) {
            return;
        }
        if (!compensatory
                && payload.value(QStringLiteral("purchaseReference")).toString() != m_awaitedReference) {
            return;
        }
        if (compensatory) {
            QSettings settings;
            const QString storedStatus = settings.value(
                QStringLiteral("commands/%1/status").arg(commandId)).toString();
            if (storedStatus == QStringLiteral("COMPLETED")) {
                publishCommandAcknowledgement(
                    commandId, issuanceCode, QStringLiteral("COMPLETED"),
                    QStringLiteral("TICKET_PRESENTED"));
                return;
            }
        }
        const auto ticket = payload.value(QStringLiteral("ticket")).toObject();
        const QByteArray png = QByteArray::fromBase64(
            ticket.value(QStringLiteral("qrPngBase64")).toString().toLatin1());
        const QString ticketCode = ticket.value(QStringLiteral("ticketCode")).toString();
        const QString qrValue = ticket.value(QStringLiteral("qrValue")).toString();
        if (ticketCode.isEmpty() || qrValue.isEmpty() || png.isEmpty()) {
            if (compensatory) {
                publishCommandAcknowledgement(
                    commandId, issuanceCode, QStringLiteral("REJECTED"),
                    QStringLiteral("INVALID_TICKET_PAYLOAD"));
            } else {
                fail(QStringLiteral("INVALID_ISSUANCE_RESPONSE"));
            }
            return;
        }
        if (compensatory) {
            QSettings settings;
            settings.setValue(QStringLiteral("commands/%1/status").arg(commandId),
                              QStringLiteral("RECEIVED"));
            publishCommandAcknowledgement(
                commandId, issuanceCode, QStringLiteral("RECEIVED"),
                QStringLiteral("COMMAND_STORED"));
            emit compensatoryTicketIssued(
                commandId, issuanceCode, ticketCode, png, qrValue,
                ticket.value(QStringLiteral("linkingCode")).toString());
            return;
        }
        m_timeout->stop();
        const QString purchaseReference = m_awaitedReference;
        m_awaitedReference.clear();
        publishOperationEvent(
            QStringLiteral("QR_TICKET_GENERATED"), purchaseReference, ticketCode,
            QStringLiteral("QR_PRESENTED"));
        emit ticketIssued(
            ticketCode, png, qrValue,
            ticket.value(QStringLiteral("linkingCode")).toString(), purchaseReference);
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
    const QString now = QDateTime::currentDateTimeUtc().toString(Qt::ISODateWithMs);
    QJsonObject payload{
        {QStringLiteral("commandId"), commandId},
        {QStringLiteral("issuanceCode"), issuanceCode},
        {QStringLiteral("status"), status},
        {QStringLiteral("resultCode"), resultCode},
        {QStringLiteral("completedAt"), now},
    };
    QJsonObject envelope{
        {QStringLiteral("schemaVersion"), 1},
        {QStringLiteral("messageId"), QUuid::createUuid().toString(QUuid::WithoutBraces)},
        {QStringLiteral("correlationId"), commandId},
        {QStringLiteral("type"), QStringLiteral("ticket.issue-acknowledged")},
        {QStringLiteral("deviceCode"), m_deviceCode},
        {QStringLiteral("occurredAt"), now},
        {QStringLiteral("sentAt"), now},
        {QStringLiteral("payload"), payload},
    };
    publishOrQueue(
        QStringLiteral("rmm/v1/devices/%1/acks").arg(m_deviceCode),
        QJsonDocument(envelope).toJson(QJsonDocument::Compact));
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
    QJsonObject details;
    if (!purchaseReference.isEmpty()) {
        details.insert(QStringLiteral("purchaseReference"), purchaseReference);
    }
    if (!ticketCode.isEmpty()) {
        details.insert(QStringLiteral("ticketCode"), ticketCode);
    }
    if (!resultCode.isEmpty()) {
        details.insert(QStringLiteral("resultCode"), resultCode);
    }
    QJsonObject payload{
        {QStringLiteral("eventCode"), eventCode},
        {QStringLiteral("severity"),
         eventCode == QStringLiteral("TICKET_PURCHASE_FAILED")
            ? QStringLiteral("ERROR") : QStringLiteral("INFO")},
        {QStringLiteral("details"), details},
    };
    const QString now = QDateTime::currentDateTimeUtc().toString(Qt::ISODateWithMs);
    QJsonObject envelope{
        {QStringLiteral("schemaVersion"), 1},
        {QStringLiteral("messageId"), QUuid::createUuid().toString(QUuid::WithoutBraces)},
        {QStringLiteral("correlationId"), purchaseReference.isEmpty()
            ? QJsonValue::Null : QJsonValue(purchaseReference)},
        {QStringLiteral("type"), QStringLiteral("device.operation-event")},
        {QStringLiteral("deviceCode"), m_deviceCode},
        {QStringLiteral("occurredAt"), now},
        {QStringLiteral("sentAt"), now},
        {QStringLiteral("payload"), payload},
    };
    const QString topic = QStringLiteral("rmm/v1/devices/%1/events/operation").arg(m_deviceCode);
    publishOrQueue(topic, QJsonDocument(envelope).toJson(QJsonDocument::Compact));
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
    QJsonObject configuration;
    if (!request.originStationCode.isEmpty()) {
        configuration.insert(QStringLiteral("originStationCode"), request.originStationCode);
        configuration.insert(QStringLiteral("destinationStationCode"), request.destinationStationCode);
    } else if (request.quantity > 0) {
        configuration.insert(QStringLiteral("quantity"), request.quantity);
    } else {
        configuration.insert(QStringLiteral("rechargeAmount"), request.rechargeAmount);
    }
    QJsonObject payload{
        {QStringLiteral("purchaseReference"), m_pendingReference},
        {QStringLiteral("productCode"), request.productCode},
        {QStringLiteral("paymentMethod"), QStringLiteral("SIMULATED")},
        {QStringLiteral("paidAmount"), request.paidAmount},
        {QStringLiteral("currency"), QStringLiteral("EUR")},
        {QStringLiteral("configuration"), configuration},
    };
    const QString messageId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    QJsonObject envelope{
        {QStringLiteral("schemaVersion"), 1},
        {QStringLiteral("messageId"), messageId},
        {QStringLiteral("correlationId"), QJsonValue::Null},
        {QStringLiteral("type"), QStringLiteral("ticket.purchase-requested")},
        {QStringLiteral("deviceCode"), m_deviceCode},
        {QStringLiteral("occurredAt"), QDateTime::currentDateTimeUtc().toString(Qt::ISODateWithMs)},
        {QStringLiteral("sentAt"), QDateTime::currentDateTimeUtc().toString(Qt::ISODateWithMs)},
        {QStringLiteral("payload"), payload},
    };
    m_pendingPayload = QJsonDocument(envelope).toJson(QJsonDocument::Compact);
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
