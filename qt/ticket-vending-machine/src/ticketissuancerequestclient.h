#pragma once

#include <QObject>
#include <QString>

class QMqttClient;
class QTimer;

struct TicketIssuanceRequest
{
    QString productCode;
    QString originStationCode;
    QString destinationStationCode;
    int quantity = 0;
    double rechargeAmount = 0.0;
    double paidAmount = 0.0;
};

class TicketIssuanceRequestClient final : public QObject
{
    Q_OBJECT

public:
    explicit TicketIssuanceRequestClient(QObject *parent = nullptr);
    void submit(const TicketIssuanceRequest &request);
    void publishOperationEvent(
        const QString &eventCode,
        const QString &purchaseReference,
        const QString &ticketCode,
        const QString &resultCode = QString());

signals:
    void submitted(const QString &requestReference);
    void failed(const QString &reason);
    void ticketIssued(
        const QString &ticketCode,
        const QByteArray &qrPng,
        const QString &qrValue,
        const QString &linkingCode,
        const QString &purchaseReference);

private:
    void publishPending();
    void fail(const QString &reason);

    QMqttClient *m_client;
    QTimer *m_timeout;
    QByteArray m_pendingPayload;
    QString m_pendingReference;
    QString m_awaitedReference;
    QString m_deviceCode;
    qint32 m_packetId = -1;
};
