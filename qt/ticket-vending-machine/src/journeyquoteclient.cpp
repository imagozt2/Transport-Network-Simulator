#include "journeyquoteclient.h"

#include <rmm/localservices.h>

#include <QJsonDocument>
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QUrl>
#include <QUrlQuery>

JourneyQuoteClient::JourneyQuoteClient(QObject *parent)
    : QObject(parent), m_network(new QNetworkAccessManager(this))
{
}

void JourneyQuoteClient::load(const QString &originCode, const QString &destinationCode)
{
    if (m_loading) {
        return;
    }
    m_loading = true;
    const QString baseUrl = QString::fromUtf8(
        rmm::config::apiBaseUrl.data(), static_cast<qsizetype>(rmm::config::apiBaseUrl.size()));
    QUrl url(baseUrl + QStringLiteral("/api/public/v1/journeys"));
    QUrlQuery query;
    query.addQueryItem(QStringLiteral("origin"), originCode);
    query.addQueryItem(QStringLiteral("destination"), destinationCode);
    url.setQuery(query);
    QNetworkRequest request(url);
    request.setRawHeader("Accept", "application/json");
    request.setTransferTimeout(10000);
    auto *reply = m_network->get(request);

    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        m_loading = false;
        const auto document = QJsonDocument::fromJson(reply->readAll());
        const int stationCount = document.isObject()
            ? document.object().value(QStringLiteral("stationCount")).toInt()
            : 0;
        const bool valid = reply->error() == QNetworkReply::NoError && stationCount > 0;
        reply->deleteLater();
        if (valid) {
            emit loaded(stationCount);
        } else {
            emit failed();
        }
    });
}
