#include "stationcatalogclient.h"

#include <rmm/localservices.h>

#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QUrl>

StationCatalogClient::StationCatalogClient(QObject *parent)
    : QObject(parent), m_network(new QNetworkAccessManager(this))
{
}

void StationCatalogClient::load()
{
    if (m_loading) {
        return;
    }
    m_loading = true;
    const QString baseUrl = QString::fromUtf8(
        rmm::config::apiBaseUrl.data(), static_cast<qsizetype>(rmm::config::apiBaseUrl.size()));
    QNetworkRequest request(QUrl(baseUrl + QStringLiteral("/api/public/v1/stations")));
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

        QVector<NetworkStation> stations;
        const auto items = document.object().value(QStringLiteral("items")).toArray();
        stations.reserve(items.size());
        for (const auto &item : items) {
            const auto object = item.toObject();
            NetworkStation station{
                .code = object.value(QStringLiteral("code")).toString(),
                .name = object.value(QStringLiteral("name")).toString(),
            };
            if (!station.code.isEmpty() && !station.name.isEmpty()) {
                stations.append(station);
            }
        }
        reply->deleteLater();
        emit loaded(stations);
    });
}
