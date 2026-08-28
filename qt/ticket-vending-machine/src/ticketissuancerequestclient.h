#pragma once

#include "ticketmachineprotocol.h"

#include <QByteArray>
#include <QObject>
#include <QQueue>
#include <QString>
#include <QVariantMap>

class QMqttClient;
class QTimer;

class TicketIssuanceRequestClient final : public QObject
{
    Q_OBJECT

public:
    explicit TicketIssuanceRequestClient(QObject *parent = nullptr);
    void submit(const TicketIssuanceRequest &request);
    void submitRecharge(const TicketRechargeRequest &request);
    void publishOperationEvent(
        const QString &eventCode,
        const QString &purchaseReference,
        const QString &ticketCode,
        const QString &resultCode = QString(),
        const QVariantMap &details = {});
    void completeCompensatoryIssuance(
        const QString &commandId,
        const QString &issuanceCode);

signals:
    void connectionStateChanged(bool connected, int retryDelaySeconds);
    void submitted(const QString &requestReference);
    void rechargeSubmitted(const QString &rechargeReference);
    void failed(const QString &reason);
    void ticketIssued(
        const QString &ticketCode,
        const QByteArray &qrPng,
        const QString &qrValue,
        const QString &linkingCode,
        const QString &purchaseReference);
    void compensatoryTicketIssued(
        const QString &commandId,
        const QString &issuanceCode,
        const QString &ticketCode,
        const QByteArray &qrPng,
        const QString &qrValue,
        const QString &linkingCode);
    void ticketRecharged(const TicketRechargeResult &result);

private:
    struct QueuedMessage
    {
        QString topic;
        QByteArray payload;
    };

    void connectToBroker();
    void scheduleReconnect();
    void flushQueuedMessages();
    void publishOrQueue(const QString &topic, const QByteArray &payload);
    void publishPending();
    void restorePendingOperations();
    void persistPendingOperation() const;
    void clearPersistedPendingOperation() const;
    void persistQueuedMessages() const;
    void publishPresence();
    void publishCommandAcknowledgement(
        const QString &commandId,
        const QString &issuanceCode,
        const QString &status,
        const QString &resultCode);
    void fail(const QString &reason);

    QMqttClient *m_client;
    QTimer *m_timeout;
    QTimer *m_reconnectTimer;
    QTimer *m_publishRetryTimer;
    QTimer *m_presenceTimer;
    QQueue<QueuedMessage> m_queuedMessages;
    QByteArray m_pendingPayload;
    QString m_pendingReference;
    QString m_awaitedReference;
    QString m_awaitedRechargeReference;
    bool m_pendingIsRecharge = false;
    QString m_deviceCode;
    bool m_configurationValid = false;
    qint32 m_packetId = -1;
    int m_reconnectAttempt = 0;
    int m_publishAttempt = 0;
};
