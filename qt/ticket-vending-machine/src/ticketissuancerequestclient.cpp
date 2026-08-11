#include "ticketissuancerequestclient.h"

#include <rmm/localservices.h>

#include <QDateTime>
#include <QJsonDocument>
#include <QJsonObject>
#include <QMqttClient>
#include <QMqttTopicName>
#include <QProcessEnvironment>
#include <QSslConfiguration>
#include <QTimer>
#include <QUuid>

TicketIssuanceRequestClient::TicketIssuanceRequestClient(QObject *parent)
    : QObject(parent), m_client(new QMqttClient(this)), m_timeout(new QTimer(this))
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
    m_timeout->setSingleShot(true);
    m_timeout->setInterval(10000);

    connect(m_client, &QMqttClient::connected, this, [this] {
        m_client->subscribe(
            QMqttTopicFilter(QStringLiteral("rmm/v1/devices/%1/commands").arg(m_deviceCode)), 1);
        publishPending();
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
        if (payload.value(QStringLiteral("purchaseReference")).toString() != m_awaitedReference) {
            return;
        }
        const auto ticket = payload.value(QStringLiteral("ticket")).toObject();
        const QByteArray png = QByteArray::fromBase64(
            ticket.value(QStringLiteral("qrPngBase64")).toString().toLatin1());
        const QString ticketCode = ticket.value(QStringLiteral("ticketCode")).toString();
        const QString qrValue = ticket.value(QStringLiteral("qrValue")).toString();
        if (ticketCode.isEmpty() || qrValue.isEmpty() || png.isEmpty()) {
            fail(QStringLiteral("INVALID_ISSUANCE_RESPONSE"));
            return;
        }
        m_timeout->stop();
        m_awaitedReference.clear();
        emit ticketIssued(
            ticketCode, png, qrValue,
            ticket.value(QStringLiteral("linkingCode")).toString());
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
        m_timeout->start();
        emit submitted(reference);
    });
    connect(m_client, &QMqttClient::errorChanged, this, [this](QMqttClient::ClientError error) {
        if (error != QMqttClient::NoError && !m_pendingReference.isEmpty()) {
            fail(QStringLiteral("MQTT_CONNECTION_ERROR"));
        }
    });
    connect(m_timeout, &QTimer::timeout, this, [this] {
        fail(QStringLiteral("MQTT_TIMEOUT"));
    });
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
    } else if (rmm::config::mqttTlsEnabled) {
        m_client->connectToHostEncrypted(QSslConfiguration::defaultConfiguration());
    } else {
        m_client->connectToHost();
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
        fail(QStringLiteral("MQTT_PUBLICATION_ERROR"));
    }
}

void TicketIssuanceRequestClient::fail(const QString &reason)
{
    m_timeout->stop();
    m_pendingPayload.clear();
    m_pendingReference.clear();
    m_awaitedReference.clear();
    m_packetId = -1;
    emit failed(reason);
}
