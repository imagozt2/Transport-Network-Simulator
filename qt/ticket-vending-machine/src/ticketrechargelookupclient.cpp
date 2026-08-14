#include "ticketrechargelookupclient.h"

#include <rmm/localservices.h>

#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QUrl>

namespace {
std::optional<double> optionalDouble(const QJsonObject &object, const QString &name)
{
    const auto value = object.value(name);
    return value.isDouble() ? std::optional<double>(value.toDouble()) : std::nullopt;
}

std::optional<int> optionalInt(const QJsonObject &object, const QString &name)
{
    const auto value = object.value(name);
    return value.isDouble() ? std::optional<int>(value.toInt()) : std::nullopt;
}

QVector<int> integerOptions(const QJsonObject &object, const QString &name)
{
    QVector<int> result;
    const auto values = object.value(name).toArray();
    result.reserve(values.size());
    for (const auto &value : values) {
        if (value.isDouble()) {
            result.append(value.toInt());
        }
    }
    return result;
}
}

TicketRechargeLookupClient::TicketRechargeLookupClient(QObject *parent)
    : QObject(parent), m_network(new QNetworkAccessManager(this))
{
}

void TicketRechargeLookupClient::lookup(const QString &qrValue)
{
    if (m_loading || qrValue.isEmpty()) {
        return;
    }
    m_loading = true;
    const QString baseUrl = QString::fromUtf8(
        rmm::config::apiBaseUrl.data(), static_cast<qsizetype>(rmm::config::apiBaseUrl.size()));
    QNetworkRequest request(QUrl(baseUrl + QStringLiteral("/api/public/v1/ticket-recharges/lookup")));
    request.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));
    request.setRawHeader("Accept", "application/json");
    request.setTransferTimeout(10000);
    const QJsonObject payload{{QStringLiteral("qrValue"), qrValue}};
    auto *reply = m_network->post(request, QJsonDocument(payload).toJson(QJsonDocument::Compact));

    connect(reply, &QNetworkReply::finished, this, [this, reply, qrValue] {
        m_loading = false;
        const QByteArray body = reply->readAll();
        const auto document = QJsonDocument::fromJson(body);
        if (reply->error() != QNetworkReply::NoError || !document.isObject()) {
            QString reason = QString::fromUtf8(body).trimmed();
            if (reason.isEmpty()) {
                reason = reply->errorString();
            }
            reply->deleteLater();
            emit failed(reason);
            return;
        }

        const auto object = document.object();
        RechargeableTicket ticket{
            .qrValue = qrValue,
            .ticketCode = object.value(QStringLiteral("ticketCode")).toString(),
            .productCode = object.value(QStringLiteral("productCode")).toString(),
            .productName = object.value(QStringLiteral("productName")).toString(),
            .productType = object.value(QStringLiteral("productType")).toString(),
            .ticketStatus = object.value(QStringLiteral("ticketStatus")).toString(),
            .supportType = object.value(QStringLiteral("supportType")).toString(),
            .currency = object.value(QStringLiteral("currency")).toString(QStringLiteral("EUR")),
            .remainingTrips = optionalInt(object, QStringLiteral("remainingTrips")),
            .balanceAmount = optionalDouble(object, QStringLiteral("balanceAmount")),
            .requiresOriginDestination = object.value(QStringLiteral("requiresOriginDestination")).toBool(),
            .minRechargeAmount = optionalDouble(object, QStringLiteral("minRechargeAmount")),
            .maxRechargeAmount = optionalDouble(object, QStringLiteral("maxRechargeAmount")),
            .pricePerTrip = optionalDouble(object, QStringLiteral("pricePerTrip")),
            .pricePerDay = optionalDouble(object, QStringLiteral("pricePerDay")),
            .tripOptions = integerOptions(object, QStringLiteral("tripOptions")),
            .dayOptions = integerOptions(object, QStringLiteral("dayOptions")),
        };
        reply->deleteLater();
        if (ticket.ticketCode.isEmpty() || ticket.productType.isEmpty()) {
            emit failed(QStringLiteral("INVALID_RECHARGE_RESPONSE"));
            return;
        }
        emit loaded(ticket);
    });
}
