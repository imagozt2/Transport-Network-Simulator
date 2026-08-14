#include "ticketrechargequoteclient.h"

#include <rmm/localservices.h>

#include <QJsonDocument>
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QUrl>

TicketRechargeQuoteClient::TicketRechargeQuoteClient(QObject *parent)
    : QObject(parent), m_network(new QNetworkAccessManager(this))
{
}

void TicketRechargeQuoteClient::quote(const TicketRechargeConfiguration &configuration)
{
    if (m_loading || configuration.qrValue.isEmpty()) return;
    m_loading = true;
    const QString baseUrl = QString::fromUtf8(
        rmm::config::apiBaseUrl.data(), static_cast<qsizetype>(rmm::config::apiBaseUrl.size()));
    QNetworkRequest request(QUrl(baseUrl + QStringLiteral("/api/public/v1/ticket-recharges/quotes")));
    request.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));
    request.setRawHeader("Accept", "application/json");
    request.setTransferTimeout(10000);
    QJsonObject payload{{QStringLiteral("qrValue"), configuration.qrValue}};
    if (!configuration.originStationCode.isEmpty()) {
        payload.insert(QStringLiteral("originStationCode"), configuration.originStationCode);
        payload.insert(QStringLiteral("destinationStationCode"), configuration.destinationStationCode);
    }
    if (configuration.trips > 0) payload.insert(QStringLiteral("trips"), configuration.trips);
    if (configuration.days > 0) payload.insert(QStringLiteral("days"), configuration.days);
    if (configuration.balanceAmount > 0) {
        payload.insert(QStringLiteral("balanceAmount"), configuration.balanceAmount);
    }
    auto *reply = m_network->post(request, QJsonDocument(payload).toJson(QJsonDocument::Compact));
    connect(reply, &QNetworkReply::finished, this, [this, reply, configuration] {
        m_loading = false;
        const auto document = QJsonDocument::fromJson(reply->readAll());
        if (reply->error() != QNetworkReply::NoError || !document.isObject()) {
            reply->deleteLater();
            emit failed();
            return;
        }
        const auto object = document.object();
        TicketRechargeQuote result{
            .configuration = configuration,
            .ticketCode = object.value(QStringLiteral("ticketCode")).toString(),
            .productType = object.value(QStringLiteral("productType")).toString(),
            .currency = object.value(QStringLiteral("currency")).toString(QStringLiteral("EUR")),
            .stationCount = object.value(QStringLiteral("stationCount")).toInt(),
            .resultingTrips = object.value(QStringLiteral("resultingTrips")).toInt(),
            .resultingBalanceAmount = object.value(QStringLiteral("resultingBalanceAmount")).toDouble(),
            .totalAmount = object.value(QStringLiteral("totalAmount")).toDouble(),
        };
        reply->deleteLater();
        if (result.ticketCode.isEmpty() || result.productType.isEmpty()) {
            emit failed();
            return;
        }
        emit loaded(result);
    });
}
