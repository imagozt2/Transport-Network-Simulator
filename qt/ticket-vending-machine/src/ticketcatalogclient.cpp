#include "ticketcatalogclient.h"

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

TicketProduct productFromJson(const QJsonObject &object)
{
    return TicketProduct{
        .code = object.value(QStringLiteral("code")).toString(),
        .name = object.value(QStringLiteral("name")).toString(),
        .description = object.value(QStringLiteral("description")).toString(),
        .type = object.value(QStringLiteral("type")).toString(),
        .currency = object.value(QStringLiteral("currency")).toString(QStringLiteral("EUR")),
        .basePrice = optionalDouble(object, QStringLiteral("basePrice")),
        .pricePerStation = optionalDouble(object, QStringLiteral("pricePerStation")),
        .pricePerTrip = optionalDouble(object, QStringLiteral("pricePerTrip")),
        .pricePerDay = optionalDouble(object, QStringLiteral("pricePerDay")),
        .minRechargeAmount = optionalDouble(object, QStringLiteral("minRechargeAmount")),
        .maxRechargeAmount = optionalDouble(object, QStringLiteral("maxRechargeAmount")),
        .minTrips = optionalInt(object, QStringLiteral("minTrips")),
        .maxTrips = optionalInt(object, QStringLiteral("maxTrips")),
        .minDays = optionalInt(object, QStringLiteral("minDays")),
        .maxDays = optionalInt(object, QStringLiteral("maxDays")),
        .rechargeable = object.value(QStringLiteral("rechargeable")).toBool(),
    };
}
}

TicketCatalogClient::TicketCatalogClient(QObject *parent)
    : QObject(parent), m_network(new QNetworkAccessManager(this))
{
}

void TicketCatalogClient::load()
{
    if (m_loading) {
        return;
    }
    m_loading = true;
    const QString baseUrl = QString::fromUtf8(
        rmm::config::apiBaseUrl.data(), static_cast<qsizetype>(rmm::config::apiBaseUrl.size()));
    QNetworkRequest request(QUrl(baseUrl + QStringLiteral("/api/public/v1/ticket-products")));
    request.setRawHeader("Accept", "application/json");
    request.setTransferTimeout(10000);
    auto *reply = m_network->get(request);

    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        m_loading = false;
        const auto document = QJsonDocument::fromJson(reply->readAll());
        const bool valid = reply->error() == QNetworkReply::NoError
            && document.isObject() && document.object().value(QStringLiteral("items")).isArray();
        if (!valid) {
            reply->deleteLater();
            emit failed();
            return;
        }

        QVector<TicketProduct> products;
        const auto items = document.object().value(QStringLiteral("items")).toArray();
        products.reserve(items.size());
        for (const auto &item : items) {
            if (item.isObject()) {
                const auto product = productFromJson(item.toObject());
                if (!product.code.isEmpty() && !product.type.isEmpty()) {
                    products.append(product);
                }
            }
        }
        reply->deleteLater();
        emit loaded(products);
    });
}
