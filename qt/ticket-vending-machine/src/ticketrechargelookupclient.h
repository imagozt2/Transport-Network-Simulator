#pragma once

#include <QObject>
#include <QString>
#include <QVector>

#include <optional>

class QNetworkAccessManager;

struct RechargeableTicket
{
    QString qrValue;
    QString ticketCode;
    QString productCode;
    QString productName;
    QString productType;
    QString ticketStatus;
    QString supportType;
    QString currency;
    std::optional<int> remainingTrips;
    std::optional<double> balanceAmount;
    bool requiresOriginDestination = false;
    std::optional<double> minRechargeAmount;
    std::optional<double> maxRechargeAmount;
    std::optional<double> pricePerTrip;
    std::optional<double> pricePerDay;
    QVector<int> tripOptions;
    QVector<int> dayOptions;
};

class TicketRechargeLookupClient final : public QObject
{
    Q_OBJECT

public:
    explicit TicketRechargeLookupClient(QObject *parent = nullptr);
    void lookup(const QString &qrValue);

signals:
    void loaded(const RechargeableTicket &ticket);
    void failed(const QString &reason);

private:
    QNetworkAccessManager *m_network = nullptr;
    bool m_loading = false;
};

Q_DECLARE_METATYPE(RechargeableTicket)
