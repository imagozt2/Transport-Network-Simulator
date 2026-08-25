#pragma once

#include "validationresult.h"
#include "validatorconfiguration.h"

#include <QByteArray>
#include <QObject>
#include <QString>

class QMqttClient;
class QTimer;

class ValidatorMqttClient final : public QObject
{
    Q_OBJECT

public:
    explicit ValidatorMqttClient(
        const ValidatorConfiguration &configuration,
        QObject *parent = nullptr);
    void submit(const QString &qrValue);
    [[nodiscard]] bool hasPendingValidation() const;

signals:
    void connectionStateChanged(bool connected);
    void validationSubmitted(const QString &validationReference);
    void validationCompleted(const ValidationResult &result);
    void validationFailed(const QString &reason);

private:
    void connectToBroker();
    void publishPending();
    void scheduleReconnect();
    void schedulePublishRetry();
    void failPending(const QString &reason);
    void clearPending();
    void restorePendingValidation();
    void persistPendingValidation() const;
    void clearPersistedValidation() const;
    void publishPresence();

    ValidatorConfiguration m_configuration;
    QMqttClient *m_client;
    QTimer *m_timeout;
    QTimer *m_reconnectTimer;
    QTimer *m_publishRetryTimer;
    QTimer *m_presenceTimer;
    QByteArray m_pendingPayload;
    QString m_pendingReference;
    qint32 m_packetId = -1;
    int m_reconnectAttempt = 0;
    int m_publishAttempt = 0;
};
