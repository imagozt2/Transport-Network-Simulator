#pragma once

#include <QObject>
#include <QString>

class QNetworkAccessManager;

struct TicketRechargeConfiguration
{
    QString qrValue;
    QString originStationCode;
    QString destinationStationCode;
    int trips = 0;
    int days = 0;
    double balanceAmount = 0.0;
};

struct TicketRechargeQuote
{
    TicketRechargeConfiguration configuration;
    QString ticketCode;
    QString productType;
    QString currency;
    int stationCount = 0;
    int resultingTrips = 0;
    double resultingBalanceAmount = 0.0;
    double totalAmount = 0.0;
};

class TicketRechargeQuoteClient final : public QObject
{
    Q_OBJECT

public:
    explicit TicketRechargeQuoteClient(QObject *parent = nullptr);
    void quote(const TicketRechargeConfiguration &configuration);

signals:
    void loaded(const TicketRechargeQuote &quote);
    void failed();

private:
    QNetworkAccessManager *m_network = nullptr;
    bool m_loading = false;
};

Q_DECLARE_METATYPE(TicketRechargeConfiguration)
Q_DECLARE_METATYPE(TicketRechargeQuote)
