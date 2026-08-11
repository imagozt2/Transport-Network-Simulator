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

signals:
    void connectionStateChanged(bool connected);
    void validationSubmitted(const QString &validationReference);
    void validationCompleted(const ValidationResult &result);
    void validationFailed(const QString &reason);

private:
    void connectToBroker();
    void publishPending();
    void clearPending();

    ValidatorConfiguration m_configuration;
    QMqttClient *m_client;
    QTimer *m_timeout;
    QByteArray m_pendingPayload;
    QString m_pendingReference;
    qint32 m_packetId = -1;
};
