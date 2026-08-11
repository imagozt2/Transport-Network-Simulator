#pragma once

#include <QObject>
#include <QString>
#include <QVector>

#include <optional>

class QNetworkAccessManager;

struct TicketProduct
{
    QString code;
    QString name;
    QString description;
    QString type;
    QString currency;
    std::optional<double> basePrice;
    std::optional<double> pricePerStation;
    std::optional<double> pricePerTrip;
    std::optional<double> pricePerDay;
    std::optional<double> minRechargeAmount;
    std::optional<double> maxRechargeAmount;
    std::optional<int> minTrips;
    std::optional<int> maxTrips;
    std::optional<int> minDays;
    std::optional<int> maxDays;
    bool rechargeable = false;
};

class TicketCatalogClient final : public QObject
{
    Q_OBJECT

public:
    explicit TicketCatalogClient(QObject *parent = nullptr);
    void load();

signals:
    void loaded(const QVector<TicketProduct> &products);
    void failed();

private:
    QNetworkAccessManager *m_network = nullptr;
    bool m_loading = false;
};
